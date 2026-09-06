package cn.huacheng.safebaiyun.unlock

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import cn.huacheng.safebaiyun.util.ContextHolder
import cn.huacheng.safebaiyun.util.LockBiz
import cn.huacheng.safebaiyun.util.showToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@SuppressLint("MissingPermission")
object UnlockRepo {

    private const val MAGIC_SERVICE = "14839ac4-7d7e-415c-9a42-167340cf2339"
    private const val TIMEOUT_MS = 10000L

    // ---------- 原有成员变量 ----------
    private lateinit var gatt: BluetoothGatt
    private lateinit var readableCharacteristic: BluetoothGattCharacteristic
    private lateinit var writeableCharacteristic: BluetoothGattCharacteristic

    private var autoDisconnectJob: Job? = null
    private var repoScope: CoroutineScope? = null

    private val _logFlow = MutableStateFlow<List<String>>(emptyList())
    val logFlow: StateFlow<List<String>> = _logFlow

    private var _pendingConfig: Pair<String, String> = "" to ""

    // ---------- 初始化 ----------
    fun init(scope: CoroutineScope) {
        repoScope = scope
    }

    // ============================================================
    //  原有 unlock 方法（保持兼容）
    // ============================================================

    fun unlock(mac: String, key: String) {
        val bluetoothManager = ContextHolder.get()
            .getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            showToast("蓝牙未开启")
            return
        }
        if (!BluetoothAdapter.checkBluetoothAddress(mac)) {
            showToast("Mac地址格式错误")
            return
        }
        connect(bluetoothAdapter, mac, key)

        val scope = repoScope ?: GlobalScope
        autoDisconnectJob = scope.launch {
            delay(TIMEOUT_MS)
            if (isActive) {
                log("10s超时，自动断开")
                gatt.disconnect()
                gatt.close()
            }
        }
    }

    fun unlock() {
        val (mac, key) = DataRepo.readData()
        unlock(mac, key)
    }

    // ============================================================
    //  原有 connect / callback / handleService（完整保留）
    // ============================================================

    private fun connect(bluetoothAdapter: BluetoothAdapter, mac: String, key: String) {
        val remoteDevice = bluetoothAdapter.getRemoteDevice(mac)
        _pendingConfig = mac to key
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            remoteDevice.connectGatt(
                ContextHolder.get(),
                false,
                callback,
                BluetoothDevice.TRANSPORT_LE
            )
        } else {
            remoteDevice.connectGatt(
                ContextHolder.get(),
                false,
                callback
            )
        }
        log("尝试连接蓝牙 ${this::gatt.isInitialized}")
    }

    private val callback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            log("连接状态改变 status$status,newState$newState")
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                autoDisconnectJob?.cancel()
                log("开始搜索服务")
                gatt?.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            log("搜索服务成功 $status")
            log("搜索到以下服务：${gatt?.services?.map { it.uuid }?.joinToString(",")}")
            handleService(gatt?.services?.find { it.uuid.toString() == MAGIC_SERVICE })
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            log("特征码读取回调 $status,${value.size}")
            handleCharacteristicWrite(value)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            val value = characteristic?.value ?: return
            log("特征码读取回调 $status,${value.size}")
            handleCharacteristicWrite(value)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            log("特征码写入回调")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                showToast("开门成功")
            } else {
                showToast("密钥写入失败")
            }
            gatt?.close()
        }
    }

    private fun handleService(service: BluetoothGattService?) {
        if (service == null) {
            return
        }
        log("开始处理服务，共${service.characteristics.size}个特征")
        val propCharacteristics = mutableListOf<BluetoothGattCharacteristic>()

        service.characteristics?.forEach {
            log("特征${it.uuid},prop:${it.properties}")
            val properties = it.properties
            if ((properties and 2) != 0) {
                readableCharacteristic = it
            }
            if ((properties and 8) != 0) {
                writeableCharacteristic = it
            }
            if ((properties and 16) != 0) {
                propCharacteristics.add(it)
            }
            if ((properties and 32) != 0) {
                propCharacteristics.add(it)
            }
        }
        handleCharacteristics(propCharacteristics)
    }

    private fun handleCharacteristics(propCharacteristics: MutableList<BluetoothGattCharacteristic>) {
        log("开始处理特征,写入对应数据")
        propCharacteristics.forEach { characteristic ->
            gatt.setCharacteristicNotification(characteristic, true)
            characteristic.descriptors.forEach {
                if ((characteristic.properties and 16) != 0) {
                    characteristic.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else if ((characteristic.properties and 32) != 0) {
                    characteristic.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                }
                gatt.writeDescriptor(it)
            }
        }
        val result = gatt.readCharacteristic(readableCharacteristic)
        log("特征写入结果 $result")
    }

    private fun handleCharacteristicWrite(value: ByteArray) {
        val (mac, key) = _pendingConfig
        log("开始写入密钥")
        val encrypted = LockBiz.encryptData(value, LockBiz.hexToByteArray(mac), key)
        log(encrypted.joinToString())
        writeableCharacteristic.setValue(encrypted)
        writeableCharacteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        val result = gatt.writeCharacteristic(writeableCharacteristic)
        log("密钥写入结果 $result")
    }

    // ============================================================
    //  新增挂起函数 tryUnlock（供 UI 调用）
    // ============================================================

    suspend fun tryUnlock(mac: String, key: String): Boolean {
        val bluetoothManager = ContextHolder.get()
            .getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            showToast("蓝牙未开启")
            return false
        }
        if (!BluetoothAdapter.checkBluetoothAddress(mac)) {
            showToast("Mac地址格式错误")
            return false
        }
        return withTimeoutOrNull(TIMEOUT_MS) {
            doUnlockSuspend(bluetoothAdapter, mac, key)
        } ?: false
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
                    log("已连接，开始发现服务")
                    gatt?.discoverServices()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    log("服务发现失败")
                    finish(false)
                    return
                }
                val service = gatt?.services?.find { it.uuid.toString() == MAGIC_SERVICE }
                if (service == null) {
                    log("未找到门禁服务")
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
                    log("未找到读/写特征")
                    finish(false)
                    return
                }
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
                    log("读取挑战码成功: ${value.size} 字节")
                    // 加密并写入
                    val macBytes = LockBiz.hexToByteArray(mac)
                    val command = LockBiz.encryptData(value, macBytes, key)
                    writeChar?.let {
                        it.value = command
                        it.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        gatt.writeCharacteristic(it)
                    } ?: finish(false)
                } else {
                    log("读取挑战码失败")
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
                    log("读取挑战码成功 (旧API): ${value.size} 字节")
                    val macBytes = LockBiz.hexToByteArray(mac)
                    val command = LockBiz.encryptData(value, macBytes, key)
                    writeChar?.let {
                        it.value = command
                        it.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        gatt?.writeCharacteristic(it)
                    } ?: finish(false)
                } else {
                    log("读取挑战码失败 (旧API)")
                    finish(false)
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    log("写入指令成功")
                    finish(true)
                } else {
                    log("写入指令失败")
                    finish(false)
                }
                gatt?.close()
            }

            private fun finish(success: Boolean) {
                if (!isCompleted) {
                    isCompleted = true
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
                gattInstance?.close()
                continuation.resume(false)
            }
        }
    }

    // ============================================================
    //  一键轮询功能（移入 object 内部）
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
            // 报告进度
            onProgress(index + 1, doors.size, door.name)

            log("正在尝试第 ${index + 1}/${doors.size} 个门禁: ${door.name} (${door.mac})")

            val success = tryUnlock(door.mac, door.key)

            if (success) {
                log("✅ 成功开启门禁: ${door.name}")
                showToast("✅ 已成功开门！")
                return door
            } else {
                log("❌ 第 ${index + 1} 个门禁开门失败，继续尝试下一个...")
                // 两个门禁之间稍微延迟，避免蓝牙切换太快
                delay(500)
            }
        }

        log("所有门禁均尝试失败")
        showToast("未找到可开启的门禁")
        return null
    }

    // ============================================================
    //  日志工具
    // ============================================================

    @OptIn(DelicateCoroutinesApi::class)
    private fun log(msg: String) {
        println(msg)
        _logFlow.value = _logFlow.value + msg
    }
}
