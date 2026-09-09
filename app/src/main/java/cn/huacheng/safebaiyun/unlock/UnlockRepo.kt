package cn.huacheng.safebaiyun.unlock

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
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
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
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

    // 单门禁开锁步骤状态流
    private val _unlockStep = MutableStateFlow("")
    val unlockStep: StateFlow<String> = _unlockStep

    // ---------- 轮询控制 ----------
    private var pollJob: Job? = null

    // ---------- 初始化 ----------
    fun init(scope: CoroutineScope) {
        // scope 保留用于未来扩展
    }

    // ============================================================
    //  挂起函数 tryUnlock（统一入口，包含进度反馈）
    // ============================================================

    suspend fun tryUnlock(mac: String, key: String): Boolean {
        _unlockStep.value = "准备开锁..."
        val bluetoothManager = ContextHolder.get()
            .getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _unlockStep.value = "蓝牙未开启"
            showToast("蓝牙未开启")
            return false
        }
        if (!BluetoothAdapter.checkBluetoothAddress(mac)) {
            _unlockStep.value = "MAC地址错误"
            showToast("Mac地址格式错误")
            return false
        }
        val result = withTimeoutOrNull(ConfigManager.getUnlockTimeout()) {
            doUnlockSuspend(bluetoothAdapter, mac, key)
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

    /**
     * 内部挂起实现，使用 suspendCancellableCoroutine 将回调转为协程
     */
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
                // 查找特征
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
                // 读取挑战码
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
                    // 加密并写入
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

        // 发起连接
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
    //  自动扫描附近已配置门禁
    // ============================================================

    /**
     * 扫描附近设备，命中已配置的门禁 MAC 后立即停止扫描。
     * 不使用 RSSI；只根据 MAC 精确匹配。
     */
    suspend fun findNearbyConfiguredDoor(doors: List<DoorDevice>, durationMs: Long): DoorDevice? {
        if (doors.isEmpty() || durationMs <= 0) return null

        val bluetoothManager = ContextHolder.get()
            .getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter ?: return null
        if (!adapter.isEnabled) return null

        val scanner = adapter.bluetoothLeScanner ?: return null
        val knownDoors = doors
            .filter { BluetoothAdapter.checkBluetoothAddress(it.mac) }
            .associateBy { it.mac.uppercase(java.util.Locale.US) }
        if (knownDoors.isEmpty()) return null

        return withTimeoutOrNull(durationMs) {
            suspendCancellableCoroutine { continuation ->
                val finished = AtomicBoolean(false)
                lateinit var callback: ScanCallback

                fun finish(result: DoorDevice?) {
                    if (finished.compareAndSet(false, true)) {
                        runCatching { scanner.stopScan(callback) }
                        continuation.resume(result)
                    }
                }

                callback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult) {
                        val mac = result.device.address?.uppercase(java.util.Locale.US) ?: return
                        val door = knownDoors[mac]
                        if (door != null) {
                            log("自动扫描命中门禁: ${door.name} ($mac)")
                            finish(door)
                        }
                    }

                    override fun onScanFailed(errorCode: Int) {
                        log("自动扫描失败，错误码: $errorCode")
                        finish(null)
                    }
                }

                continuation.invokeOnCancellation {
                    if (finished.compareAndSet(false, true)) {
                        runCatching { scanner.stopScan(callback) }
                    }
                }

                try {
                    scanner.startScan(
                        null,
                        ScanSettings.Builder()
                            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                            .build(),
                        callback
                    )
                    log("开始自动扫描，最长 ${durationMs}ms")
                } catch (e: Exception) {
                    log("启动自动扫描失败: ${e.javaClass.simpleName}")
                    finish(null)
                }
            }
        }
    }

    // ============================================================
    //  一键轮询功能（P1 修复：支持真正的协程取消）
    // ============================================================

    /**
     * 一键轮询所有门禁，依次尝试开锁
     * @param doors 门禁列表
     * @param onProgress 进度回调（当前索引，总数，当前门禁名称）
     * @return 成功开启的门禁，如果全部失败则返回 null
     */
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
            // 检查协程是否被取消
            if (!kotlin.coroutines.coroutineContext.isActive) {
                log("轮询已被取消")
                return null
            }

            // 报告进度
            onProgress(index + 1, doors.size, door.name)

            log("正在尝试第 ${index + 1}/${doors.size} 个门禁: ${door.name} (${door.mac})")

            val success = try {
                tryUnlock(door.mac, door.key)
            } catch (e: CancellationException) {
                log("轮询被取消")
                throw e  // 重新抛出取消异常
            }

            if (success) {
                log("✅ 成功开启门禁: ${door.name}")
                showToast("✅ 已成功开门！")
                return door
            } else {
                log("❌ 第 ${index + 1} 个门禁开门失败，继续尝试下一个...")
                // 轮询间隔使用用户自定义值，但可以被取消
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

    /**
     * 停止当前轮询（P1 新增）
     */
    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        log("轮询已停止")
    }

    /**
     * 启动轮询（P1 新增，使用 Job 管理生命周期）
     */
    fun startPolling(
        scope: CoroutineScope,
        doors: List<DoorDevice>,
        onProgress: suspend (index: Int, total: Int, doorName: String) -> Unit = { _, _, _ -> },
        onComplete: (DoorDevice?) -> Unit = {}
    ): Job {
        // 先取消之前的轮询
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

    /**
     * 等待蓝牙开启，最多等待 timeoutMs 毫秒
     * 在 timeoutMs 时间内，一旦检测到蓝牙开启立即返回 true
     * 超时则返回 false
     */
    suspend fun waitForBluetooth(timeoutMs: Long): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            // 检查是否被取消
            if (!kotlin.coroutines.coroutineContext.isActive) {
                return false
            }
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter != null && adapter.isEnabled) {
                return true
            }
            delay(200) // 每 200ms 检查一次
        }
        // 最后再检查一次
        val adapter = BluetoothAdapter.getDefaultAdapter()
        return adapter != null && adapter.isEnabled
    }

    // ============================================================
    //  日志工具（限制最大条数，防止内存溢出）
    // ============================================================

    private const val MAX_LOG_LINES = 200

    @OptIn(DelicateCoroutinesApi::class)
    private fun log(msg: String) {
        Log.d(TAG, msg)  // P1 修复：println -> Android Log
        val current = _logFlow.value
        _logFlow.value = if (current.size >= MAX_LOG_LINES) {
            current.drop(current.size - MAX_LOG_LINES + 1) + msg
        } else {
            current + msg
        }
    }
}
