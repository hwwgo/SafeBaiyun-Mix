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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private const val MAX_LOG_LINES = 200

    // ---------- 状态流 ----------
    private val _logFlow = MutableStateFlow<List<String>>(emptyList())
    val logFlow: StateFlow<List<String>> = _logFlow

    private val _unlockStep = MutableStateFlow("")
    val unlockStep: StateFlow<String> = _unlockStep

    fun resetUnlockStep() {
        _unlockStep.value = ""
    }

    // ---------- 轮询控制 ----------
    private var pollJob: Job? = null

    // ============================================================
    //  统一 GATT 开锁引擎：连接 -> 发现服务 -> 读挑战码 -> 写指令
    //  tryUnlock 与 probeAndUnlockSingle 共用，事件通过回调上报
    // ============================================================

    enum class ProbeOutcome {
        UNLOCKED,       // 连上了，并且开锁成功
        UNLOCK_FAILED,  // 连上了，但开锁失败
        NOT_CONNECTED   // 连接阶段失败或超时，门禁不在附近
    }

    private class GattUnlockEngine(
        private val adapter: BluetoothAdapter,
        private val mac: String,
        private val key: String,
        private val onConnected: () -> Unit = {},
        private val onEvent: (String) -> Unit = {},
    ) {
        private var gatt: BluetoothGatt? = null
        private var completed = false
        private var connected = false
        private var unlockPhaseStarted = false

        /**
         * @param connectTimeoutMs 连接阶段超时；超时且未连上 -> NOT_CONNECTED
         * @param unlockTimeoutMs  连接后的开锁阶段超时 -> UNLOCK_FAILED
         */
        suspend fun run(connectTimeoutMs: Long, unlockTimeoutMs: Long): ProbeOutcome =
            suspendCancellableCoroutine { cont ->

                fun finish(outcome: ProbeOutcome) {
                    if (completed) return
                    completed = true
                    timeoutJob?.cancel()
                    runCatching { gatt?.close() }
                    if (cont.isActive) cont.resume(outcome)
                }

                val callback = object : BluetoothGattCallback() {
                    private var readChar: BluetoothGattCharacteristic? = null
                    private var writeChar: BluetoothGattCharacteristic? = null

                    private fun sendCommand(g: BluetoothGatt, value: ByteArray) {
                        val command = LockBiz.encryptData(value, LockBiz.hexToByteArray(mac), key)
                        val wc = writeChar
                        if (wc == null) {
                            onEvent("未找到写特征")
                            finish(ProbeOutcome.UNLOCK_FAILED)
                            return
                        }
                        wc.value = command
                        wc.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        g.writeCharacteristic(wc)
                    }

                    override fun onConnectionStateChange(g: BluetoothGatt?, status: Int, newState: Int) {
                        if (newState == BluetoothGatt.STATE_CONNECTED) {
                            connected = true
                            if (!unlockPhaseStarted) {
                                unlockPhaseStarted = true
                                onConnected()
                                timeoutJob?.cancel()
                                timeoutJob = launchTimeout(unlockTimeoutMs) {
                                    onEvent("开锁阶段超时")
                                    finish(ProbeOutcome.UNLOCK_FAILED)
                                }
                            }
                            g?.discoverServices()
                        } else if (newState == BluetoothGatt.STATE_DISCONNECTED && !completed) {
                            if (connected) {
                                onEvent("连接中断")
                                finish(ProbeOutcome.UNLOCK_FAILED)
                            } else {
                                onEvent("连接失败")
                                finish(ProbeOutcome.NOT_CONNECTED)
                            }
                        }
                    }

                    override fun onServicesDiscovered(g: BluetoothGatt?, status: Int) {
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            onEvent("服务发现失败")
                            finish(ProbeOutcome.UNLOCK_FAILED)
                            return
                        }
                        val service = g?.services?.find { it.uuid.toString() == MAGIC_SERVICE }
                        if (service == null) {
                            onEvent("未找到门禁服务")
                            finish(ProbeOutcome.UNLOCK_FAILED)
                            return
                        }
                        service.characteristics.forEach { ch ->
                            val props = ch.properties
                            if (props and BluetoothGattCharacteristic.PROPERTY_READ != 0) readChar = ch
                            if (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) writeChar = ch
                        }
                        if (readChar == null || writeChar == null) {
                            onEvent("未找到读/写特征")
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
                    ) = handleRead(gatt, value, status)

                    @Deprecated("Deprecated")
                    override fun onCharacteristicRead(
                        gatt: BluetoothGatt?,
                        characteristic: BluetoothGattCharacteristic?,
                        status: Int
                    ) = handleRead(gatt ?: return, characteristic?.value ?: ByteArray(0), status)

                    private fun handleRead(g: BluetoothGatt, value: ByteArray, status: Int) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            sendCommand(g, value)
                        } else {
                            onEvent("读取挑战码失败")
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
                            onEvent("指令写入失败")
                            finish(ProbeOutcome.UNLOCK_FAILED)
                        }
                    }
                }

                timeoutJob = launchTimeout(connectTimeoutMs) {
                    if (!connected) {
                        onEvent("连接超时")
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
                    onEvent("连接异常: ${e.javaClass.simpleName}")
                    finish(ProbeOutcome.NOT_CONNECTED)
                }

                cont.invokeOnCancellation { finish(ProbeOutcome.NOT_CONNECTED) }
            }

        private var timeoutJob: Job? = null

        private fun launchTimeout(ms: Long, block: () -> Unit): Job =
            CoroutineScope(Dispatchers.IO).launch {
                delay(ms)
                block()
            }
    }

    private fun requireAdapter(): BluetoothAdapter? {
        val bluetoothManager = ContextHolder.get()
            .getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return bluetoothManager.adapter
    }

    // ============================================================
    //  单门禁开锁入口（单门禁按钮 + 一键开锁轮询兜底共用）
    // ============================================================

    suspend fun tryUnlock(mac: String, key: String): Boolean {
        fun step(msg: String) {
            _unlockStep.value = msg
        }
        step("准备开锁...")

        var adapter = requireAdapter()
        if (adapter == null || !adapter.isEnabled) {
            step("等待蓝牙开启...")
            if (!waitForBluetooth(ConfigManager.getPollWaitTime())) {
                step("蓝牙未开启")
                showToast("蓝牙未开启")
                return false
            }
            adapter = requireAdapter()
            if (adapter == null || !adapter.isEnabled) {
                step("蓝牙未开启")
                showToast("蓝牙未开启")
                return false
            }
            step("准备开锁...")
        }

        if (!BluetoothAdapter.checkBluetoothAddress(mac)) {
            step("MAC地址错误")
            showToast("Mac地址格式错误")
            return false
        }

        val outcome = withTimeoutOrNull(ConfigManager.getUnlockTimeout()) {
            GattUnlockEngine(adapter!!, mac, key, onEvent = ::step).run(
                connectTimeoutMs = ConfigManager.getUnlockTimeout(),
                unlockTimeoutMs = ConfigManager.getUnlockTimeout()
            )
        }

        step(
            when (outcome) {
                ProbeOutcome.UNLOCKED -> "✅ 开锁成功"
                null -> "❌ 开锁超时"
                else -> "❌ 开锁失败"
            }
        )
        return outcome == ProbeOutcome.UNLOCKED
    }

    // ============================================================
    //  探测并开锁：逐个探测，成功即返回；全失败返回 null
    // ============================================================

    suspend fun probeAndUnlock(
        doors: List<DoorDevice>,
        perDeviceTimeoutMs: Long,
        onProbe: suspend (index: Int, total: Int, doorName: String) -> Unit = { _, _, _ -> },
        onUnlock: suspend (index: Int, total: Int, doorName: String) -> Unit = { _, _, _ -> }
    ): DoorDevice? {
        if (doors.isEmpty() || perDeviceTimeoutMs <= 0) return null

        val adapter = requireAdapter() ?: return null
        if (!adapter.isEnabled) return null

        val validDoors = doors.filter { BluetoothAdapter.checkBluetoothAddress(it.mac) }
        if (validDoors.isEmpty()) return null

        val connectTimeout = perDeviceTimeoutMs.coerceIn(500L, 3000L)
        val unlockTimeout = ConfigManager.getUnlockTimeout()

        log("开始探测并开锁，共 ${validDoors.size} 个门禁，连接超时 ${connectTimeout}ms，开锁超时 ${unlockTimeout}ms")

        for ((index, door) in validDoors.withIndex()) {
            if (!kotlin.coroutines.coroutineContext.isActive) break

            onProbe(index + 1, validDoors.size, door.name)
            log("探测第 ${index + 1}/${validDoors.size} 个: ${door.name} (${door.mac})")

            val engineScope = CoroutineScope(kotlin.coroutines.coroutineContext)
            val outcome = GattUnlockEngine(
                adapter, door.mac, door.key,
                onEvent = { msg -> log("${door.name}: $msg") },
                onConnected = {
                    engineScope.launch { runCatching { onUnlock(index + 1, validDoors.size, door.name) } }
                }
            ).run(connectTimeout, unlockTimeout)

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
            if (!kotlin.coroutines.coroutineContext.isActive) return false
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter != null && adapter.isEnabled) return true
            delay(200)
        }
        val adapter = BluetoothAdapter.getDefaultAdapter()
        return adapter != null && adapter.isEnabled
    }

    // ============================================================
    //  日志工具（上限 200 条，环形截断）
    // ============================================================

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
