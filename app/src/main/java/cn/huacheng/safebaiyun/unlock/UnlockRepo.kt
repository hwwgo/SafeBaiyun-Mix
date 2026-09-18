package cn.huacheng.safebaiyun.unlock

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import android.util.Log
import cn.huacheng.safebaiyun.util.ConfigManager
import cn.huacheng.safebaiyun.util.ContextHolder
import cn.huacheng.safebaiyun.util.LockBiz
import cn.huacheng.safebaiyun.util.showToast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

@SuppressLint("MissingPermission")
object UnlockRepo {

    private const val TAG = "UnlockRepo"
    private const val MAGIC_SERVICE = "14839ac4-7d7e-415c-9a42-167340cf2339"

    // ---------- 状态流 ----------
    private val _logFlow = MutableStateFlow<List<String>>(emptyList())
    val logFlow: StateFlow<List<String>> = _logFlow

    private val _unlockStep = MutableStateFlow("")
    val unlockStep: StateFlow<String> = _unlockStep

    // ✅ 新增：重置开锁状态（供 ShortcutActivity 进入时清空使用）
    fun resetUnlockStep() {
        _unlockStep.value = ""
    }

    // ---------- 轮询控制 ----------
    private var pollJob: Job? = null

    fun init(scope: CoroutineScope) {
        // scope 保留用于未来扩展
    }

    // ============================================================
    //  单门禁探测结果的三种结局
    // ============================================================

    private enum class ProbeOutcome {
        UNLOCKED,       // 连上了，并且开锁成功
        UNLOCK_FAILED,  // 连上了，但开锁失败
        NOT_CONNECTED   // 连接阶段失败或超时，门禁不在附近
    }

    // ============================================================
    //  单门禁开锁入口（单门禁按钮 + 一键开锁的轮询兜底都走这里）
    // ============================================================

    suspend fun tryUnlock(mac: String, key: String): Boolean {
        _unlockStep.value = "准备开锁..."
        val bluetoothManager = ContextHolder.get()
            .getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

        // ✅ 蓝牙未开启时，等待用户在"开锁等待时间"内打开
        var bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _unlockStep.value = "等待蓝牙开启..."
            val waitTime = ConfigManager.getPollWaitTime()
            val ready = waitForBluetooth(waitTime)
            if (!ready) {
                _unlockStep.value = "蓝牙未开启"
                showToast("蓝牙未开启")
                return false
            }
            // 蓝牙打开后重新获取适配器
            bluetoothAdapter = bluetoothManager.adapter
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                _unlockStep.value = "蓝牙未开启"
                showToast("蓝牙未开启")
                return false
            }
            // ✅ 修复：蓝牙已就绪，恢复"准备开锁..."状态
            _unlockStep.value = "准备开锁..."
        }

        if (!BluetoothAdapter.checkBluetoothAddress(mac)) {
            _unlockStep.value = "MAC地址错误"
            showToast("Mac地址格式错误")
            return false
        }

        val result = withTimeoutOrNull(ConfigManager.getUnlockTimeout()) {
            doUnlockSuspend(bluetoothAdapter!!, mac, key)
        } ?: false.also { _unlockStep.value = "❌ 开锁超时" }
        if (result) {
            _unlockStep.value = "✅ 开锁成功"
        } else {
            if (_unlockStep.value != "❌ 开锁超时") {
                _unlockStep.value = "❌ 开锁失败"
            }
        }
        return result
    }

    private suspend fun doUnlockSuspend(
        adapter: BluetoothAdapter,
        mac: String,
        key: String
    ): Boolean = suspendCancellableCoroutine { continuation ->
        var gattInstance: BluetoothGatt? = null
        var isCompleted = false

        val callback = object : BluetoothGattCallback() {
            var readChar: BluetoothGattCharacteristic? = null
            var writeChar: BluetoothGattCharacteristic? = null

            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    _unlockStep.value = "已连接，正在发现服务..."
                    gatt?.discoverServices()
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    if (!isCompleted) {
                        _unlockStep.value = "连接断开"
                        finish(false)
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    _unlockStep.value = "服务发现失败"
                    finish(false)
                    return
                }
                _unlockStep.value = "服务发现成功，查找特征..."
                val service = gatt?.services?.find { it.uuid.toString() == MAGIC_SERVICE }
                if (service == null) {
                    _unlockStep.value = "未找到门禁服务"
                    finish(false)
                    return
                }
                service.characteristics.forEach { ch ->
                    val props = ch.properties
                    if (props and BluetoothGattCharacteristic.PROPERTY_READ != 0) readChar = ch
                    if (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) writeChar = ch
                }
                if (readChar == null || writeChar == null) {
                    _unlockStep.value = "未找到读/写特征"
                    finish(false)
                    return
                }
                _unlockStep.value = "正在读取挑战码..."
                gatt?.readCharacteristic(readChar)
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    _unlockStep.value = "读取挑战码成功，正在生成指令..."
                    val macBytes = LockBiz.hexToByteArray(mac)
                    val command = LockBiz.encryptData(value, macBytes, key)
                    writeChar?.let {
                        it.value = command
                        it.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        gatt.writeCharacteristic(it)
                    } ?: finish(false)
                } else {
                    _unlockStep.value = "读取挑战码失败"
                    finish(false)
                }
            }

            @Deprecated("Deprecated")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS && characteristic != null) {
                    val value = characteristic.value ?: ByteArray(0)
                    _unlockStep.value = "读取挑战码成功 (旧API)，正在生成指令..."
                    val macBytes = LockBiz.hexToByteArray(mac)
                    val command = LockBiz.encryptData(value, macBytes, key)
                    writeChar?.let {
                        it.value = command
                        it.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        gatt?.writeCharacteristic(it)
                    } ?: finish(false)
                } else {
                    _unlockStep.value = "读取挑战码失败 (旧API)"
                    finish(false)
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    _unlockStep.value = "指令写入成功，正在开门..."
                    finish(true)
                } else {
                    _unlockStep.value = "指令写入失败"
                    finish(false)
                }
                gatt?.close()
            }

            private fun finish(success: Boolean) {
                if (!isCompleted) {
                    isCompleted = true
                    if (success) {
                        _unlockStep.value = "✅ 开锁成功"
                    } else {
                        _unlockStep.value = "❌ 开锁失败"
                    }
                    gattInstance?.close()
                    continuation.resume(success)
                }
            }
        }

        val remoteDevice = adapter.getRemoteDevice(mac)
        gattInstance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            remoteDevice.connectGatt(ContextHolder.get(), false, callback, BluetoothDevice.TRANSPORT_LE)
        } else {
            remoteDevice.connectGatt(ContextHolder.get(), false, callback)
        }

        continuation.invokeOnCancellation {
            if (!isCompleted) {
                isCompleted = true
                _unlockStep.value = "已取消"
                gattInstance?.close()
                continuation.resume(false)
            }
        }
    }

    // ============================================================
    //  探测并开锁（合并方案）
    // ============================================================

    suspend fun probeAndUnlock(
        doors: List<DoorDevice>,
        perDeviceTimeoutMs: Long,
        onProbe: suspend (index: Int, total: Int, doorName: String) -> Unit = { _, _, _ -> },
        onUnlock: suspend (index: Int, total: Int, doorName: String) -> Unit = { _, _, _ -> }
    ): DoorDevice? {
        if (doors.isEmpty() || perDeviceTimeoutMs <= 0) return null

        val bluetoothManager = ContextHolder.get()
            .getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter ?: return null
        if (!adapter.isEnabled) return null

        val validDoors = doors.filter { BluetoothAdapter.checkBluetoothAddress(it.mac) }
        if (validDoors.isEmpty()) return null

        val connectTimeout = perDeviceTimeoutMs.coerceIn(500L, 3000L)

        log("开始探测并开锁，共 ${validDoors.size} 个门禁，连接超时 ${connectTimeout}ms，开锁超时 ${ConfigManager.getUnlockTimeout()}ms")

        for ((index, door) in validDoors.withIndex()) {
            if (!kotlin.coroutines.coroutineContext.isActive) break

            onProbe(index + 1, validDoors.size, door.name)
            log("探测第 ${index + 1}/${validDoors.size} 个: ${door.name} (${door.mac})")

            val outcome = probeAndUnlockSingle(
                adapter = adapter,
                door = door,
                connectTimeoutMs = connectTimeout,
                onUnlock = onUnlock,
                index = index,
                total = validDoors.size
            )

            when (outcome) {
                ProbeOutcome.UNLOCKED -> {
                    log("✅ 探测并开锁成功: ${door.name}")
                    return door
                }
                ProbeOutcome.UNLOCK_FAILED -> {
                    log("⚠️ ${door.name} 已连接但开锁失败，跳过剩余探测，进入轮询兜底")
                    return null
                }
                ProbeOutcome.NOT_CONNECTED -> {
                    log("❌ 第 ${index + 1} 个未连接: ${door.name}，继续探测下一个...")
                    delay(ConfigManager.getPollInterval())
                }
            }
        }

        log("探测结束：附近没有可连接的门禁")
        return null
    }

    private suspend fun probeAndUnlockSingle(
        adapter: BluetoothAdapter,
        door: DoorDevice,
        connectTimeoutMs: Long,
        onUnlock: suspend (index: Int, total: Int, doorName: String) -> Unit,
        index: Int,
        total: Int
    ): ProbeOutcome = suspendCancellableCoroutine { cont ->
        val mac = door.mac
        val key = door.key

        var gatt: BluetoothGatt? = null
        var isCompleted = false
        var isConnected = false
        var unlockPhaseStarted = false

        val timerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        var connectTimeoutJob: Job? = null
        var unlockTimeoutJob: Job? = null

        fun cleanup() {
            connectTimeoutJob?.cancel()
            unlockTimeoutJob?.cancel()
            timerScope.cancel()
            runCatching { gatt?.close() }
        }

        fun finish(outcome: ProbeOutcome) {
            if (isCompleted) return
            isCompleted = true
            cleanup()
            if (cont.isActive) cont.resume(outcome)
        }

        val callback = object : BluetoothGattCallback() {
            var readChar: BluetoothGattCharacteristic? = null
            var writeChar: BluetoothGattCharacteristic? = null

            override fun onConnectionStateChange(g: BluetoothGatt?, status: Int, newState: Int) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    isConnected = true
                    connectTimeoutJob?.cancel()

                    if (!unlockPhaseStarted) {
                        unlockPhaseStarted = true
                        timerScope.launch {
                            runCatching { onUnlock(index + 1, total, door.name) }
                        }
                        unlockTimeoutJob = timerScope.launch {
                            delay(ConfigManager.getUnlockTimeout())
                            if (!isCompleted) {
                                log("开锁阶段超时: $mac")
                                finish(ProbeOutcome.UNLOCK_FAILED)
                            }
                        }
                    }

                    g?.discoverServices()
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    if (!isCompleted) {
                        if (!isConnected) {
                            log("探测连接失败: $mac")
                            finish(ProbeOutcome.NOT_CONNECTED)
                        } else {
                            log("连接中断: $mac")
                            finish(ProbeOutcome.UNLOCK_FAILED)
                        }
                    }
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt?, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    log("服务发现失败: $mac")
                    finish(ProbeOutcome.UNLOCK_FAILED)
                    return
                }
                val service = g?.services?.find { it.uuid.toString() == MAGIC_SERVICE }
                if (service == null) {
                    log("未找到门禁服务: $mac")
                    finish(ProbeOutcome.UNLOCK_FAILED)
                    return
                }
                service.characteristics.forEach { ch ->
                    val props = ch.properties
                    if (props and BluetoothGattCharacteristic.PROPERTY_READ != 0) readChar = ch
                    if (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) writeChar = ch
                }
                if (readChar == null || writeChar == null) {
                    log("未找到读/写特征: $mac")
                    finish(ProbeOutcome.UNLOCK_FAILED)
                    return
                }
                g?.readCharacteristic(readChar)
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val macBytes = LockBiz.hexToByteArray(mac)
                    val command = LockBiz.encryptData(value, macBytes, key)
                    writeChar?.let {
                        it.value = command
                        it.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        gatt.writeCharacteristic(it)
                    } ?: finish(ProbeOutcome.UNLOCK_FAILED)
                } else {
                    log("读挑战码失败: $mac")
                    finish(ProbeOutcome.UNLOCK_FAILED)
                }
            }

            @Deprecated("Deprecated")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS && characteristic != null) {
                    val value = characteristic.value ?: ByteArray(0)
                    val macBytes = LockBiz.hexToByteArray(mac)
                    val command = LockBiz.encryptData(value, macBytes, key)
                    writeChar?.let {
                        it.value = command
                        it.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        gatt?.writeCharacteristic(it)
                    } ?: finish(ProbeOutcome.UNLOCK_FAILED)
                } else {
                    log("读挑战码失败(旧API): $mac")
                    finish(ProbeOutcome.UNLOCK_FAILED)
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    finish(ProbeOutcome.UNLOCKED)
                } else {
                    log("写指令失败: $mac")
                    finish(ProbeOutcome.UNLOCK_FAILED)
                }
            }
        }

        connectTimeoutJob = timerScope.launch {
            delay(connectTimeoutMs)
            if (!isCompleted && !isConnected) {
                log("探测连接超时: $mac")
                finish(ProbeOutcome.NOT_CONNECTED)
            }
        }

        try {
            val device = adapter.getRemoteDevice(mac)
            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(ContextHolder.get(), false, callback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(ContextHolder.get(), false, callback)
            }
        } catch (e: Exception) {
            log("探测异常: $mac, ${e.javaClass.simpleName}")
            finish(ProbeOutcome.NOT_CONNECTED)
        }

        cont.invokeOnCancellation { finish(ProbeOutcome.NOT_CONNECTED) }
    }

    // ============================================================
    //  一键轮询功能（兜底）
    // ============================================================

    suspend fun pollAllDoors(
        doors: List<DoorDevice>,
        onProgress: suspend (index: Int, total: Int, doorName: String) -> Unit = { _, _, _ -> }
    ): DoorDevice? {
        if (doors.isEmpty()) {
            showToast("门禁列表为空")
            return null
        }

        log("开始轮询 ${doors.size} 个门禁...")

        for ((index, door) in doors.withIndex()) {
            if (!kotlin.coroutines.coroutineContext.isActive) {
                log("轮询已被取消")
                return null
            }

            onProgress(index + 1, doors.size, door.name)

            log("正在尝试第 ${index + 1}/${doors.size} 个门禁: ${door.name} (${door.mac})")

            val success = try {
                tryUnlock(door.mac, door.key)
            } catch (e: CancellationException) {
                log("轮询被取消")
                throw e
            }

            if (success) {
                log("✅ 成功开启门禁: ${door.name}")
                return door
            } else {
                log("❌ 第 ${index + 1} 个门禁开门失败，继续尝试下一个...")
                try {
                    delay(ConfigManager.getPollInterval())
                } catch (e: CancellationException) {
                    log("轮询在等待间隔中被取消")
                    throw e
                }
            }
        }

        log("所有门禁均尝试失败")
        showToast("未找到可开启的门禁")
        return null
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        log("轮询已停止")
    }

    fun startPolling(
        scope: CoroutineScope,
        doors: List<DoorDevice>,
        onProgress: suspend (index: Int, total: Int, doorName: String) -> Unit = { _, _, _ -> },
        onComplete: (DoorDevice?) -> Unit = {}
    ): Job {
        stopPolling()

        pollJob = scope.launch {
            val result = pollAllDoors(doors, onProgress)
            onComplete(result)
        }
        return pollJob!!
    }

    // ============================================================
    //  等待蓝牙开启
    // ============================================================

    suspend fun waitForBluetooth(timeoutMs: Long): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (!kotlin.coroutines.coroutineContext.isActive) {
                return false
            }
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter != null && adapter.isEnabled) {
                return true
            }
            delay(200)
        }
        val adapter = BluetoothAdapter.getDefaultAdapter()
        return adapter != null && adapter.isEnabled
    }

    // ============================================================
    //  日志工具
    // ============================================================

    private const val MAX_LOG_LINES = 200

    @OptIn(DelicateCoroutinesApi::class)
    private fun log(msg: String) {
        Log.d(TAG, msg)
        val current = _logFlow.value
        _logFlow.value = if (current.size >= MAX_LOG_LINES) {
            current.drop(current.size - MAX_LOG_LINES + 1) + msg
        } else {
            current + msg
        }
    }
}
