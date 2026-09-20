package cn.huacheng.safebaiyun

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import cn.huacheng.safebaiyun.theme.ColorOSError
import cn.huacheng.safebaiyun.theme.ColorOSSuccess
import cn.huacheng.safebaiyun.theme.SafeBaiyunTheme
import cn.huacheng.safebaiyun.unlock.DataRepo
import cn.huacheng.safebaiyun.unlock.DoorDevice
import cn.huacheng.safebaiyun.unlock.UnlockRepo
import cn.huacheng.safebaiyun.util.ConfigManager
import cn.huacheng.safebaiyun.util.showToast
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 快捷开锁 Activity
 *
 * 专门用于：
 * 1. 桌面小组件
 * 2. 桌面快捷方式
 * 3. Activity Launcher / 桌面插件
 *
 * 重要：
 * - 不依赖 MainActivity
 * - 冷启动可以直接完成开锁
 * - 热启动 / Activity 复用也可以正确处理新的 Intent
 * - 权限、数据初始化、蓝牙检查全部在 Activity 就绪后执行
 */
class ShortcutActivity : ComponentActivity() {

    companion object {
        const val EXTRA_DOOR_ID = "door_id"
        const val ALL_DOORS_ID = "__ALL_DOORS__"

        private const val MAX_DATA_RETRY = 20
        private const val DATA_RETRY_DELAY = 100L
    }

    /**
     * 当前一次快捷开锁任务
     */
    private var unlockJob: Job? = null

    /**
     * 防止 onPostResume / onNewIntent 重复启动同一个任务
     */
    private var unlockStarted = false

    /**
     * 当前需要处理的 Intent
     *
     * 不直接一直使用 Activity.intent，
     * 因为 singleTop 复用 Activity 时会通过 onNewIntent() 更新。
     */
    private var currentIntent: Intent? = null

    /**
     * 权限请求完成后的继续动作
     */
    private var pendingUnlock: (() -> Unit)? = null

    /**
     * 蓝牙权限请求
     */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->

        val allGranted = result.values.all { it }

        val action = pendingUnlock
        pendingUnlock = null

        if (allGranted) {
            action?.invoke()
        } else {
            unlockJob?.cancel()

            showToast("未授予蓝牙权限，无法开锁")

            lifecycleScope.launch {
                delay(1000)

                if (!isFinishing && !isDestroyed) {
                    finish()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        currentIntent = intent

        /*
         * 每次进入快捷开锁 Activity，都把上一轮状态清掉。
         */
        UnlockRepo.resetUnlockStep()

        /*
         * 快捷开锁界面。
         *
         * 注意：
         * 这里不启动 MainActivity。
         * ShortcutActivity 本身就是完整的开锁入口。
         */
        setContent {
            SafeBaiyunTheme {

                val unlockStep by UnlockRepo.unlockStep.collectAsState()

                val displayText = unlockStep.ifEmpty {
                    "准备开锁..."
                }

                val textColor = when {
                    unlockStep.contains("成功") -> {
                        ColorOSSuccess
                    }

                    unlockStep.contains("失败") ||
                            unlockStep.contains("超时") ||
                            unlockStep.contains("错误") -> {
                        ColorOSError
                    }

                    else -> {
                        MaterialTheme.colorScheme.onBackground
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.background
                        ),
                    contentAlignment = Alignment.Center
                ) {

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp)
                        )

                        Spacer(
                            modifier = Modifier.height(16.dp)
                        )

                        Text(
                            text = displayText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = textColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(
                                horizontal = 32.dp
                            )
                        )
                    }
                }
            }
        }

        /*
         * ACTION_CREATE_SHORTCUT 是系统创建传统快捷方式时调用的。
         */
        if (intent.action == Intent.ACTION_CREATE_SHORTCUT) {
            createShortcut()
        }
    }

    /**
     * Activity 真正进入前台以后再开始开锁。
     *
     * 这里取代原来的：
     *
     * delay(300)
     *
     * 固定延迟无法保证冷启动一定完成。
     *
     * onPostResume 是 Android Activity 已经恢复、可以安全进行
     * 权限交互和后续工作的生命周期节点。
     */
    override fun onPostResume() {
        super.onPostResume()

        if (intent.action == Intent.ACTION_CREATE_SHORTCUT) {
            return
        }

        startUnlockIfNeeded()
    }

    /**
     * 当 ShortcutActivity 已经存在，
     * 再次从 Widget / 桌面插件启动时会走这里。
     *
     * 原代码没有处理这个情况。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        setIntent(intent)
        currentIntent = intent

        /*
         * 取消上一轮任务。
         *
         * 例如：
         * A 门禁正在开锁
         * 用户又点击了 B 门禁
         *
         * 应该处理最新一次点击。
         */
        unlockJob?.cancel()
        unlockJob = null

        pendingUnlock = null
        unlockStarted = false

        UnlockRepo.resetUnlockStep()

        startUnlockIfNeeded()
    }

    /**
     * 启动一次快捷开锁。
     */
    private fun startUnlockIfNeeded() {

        if (isFinishing || isDestroyed) {
            return
        }

        if (unlockStarted) {
            return
        }

        if (!lifecycle.currentState.isAtLeast(
                androidx.lifecycle.Lifecycle.State.RESUMED
            )
        ) {
            return
        }

        unlockStarted = true

        /*
         * 使用 lifecycleScope：
         *
         * - Activity 销毁时自动取消
         * - 不再使用手动 SupervisorJob
         * - 避免冷启动 / 热启动残留协程
         */
        unlockJob = lifecycleScope.launch {

            /*
             * 给系统一个极短的调度机会。
             *
             * 这不是靠时间等待初始化，
             * 而是确保 onPostResume 后的 UI / Activity 状态
             * 已经稳定进入消息队列。
             */
            delay(50)

            if (!isActive) {
                return@launch
            }

            checkPermissionThenUnlock()
        }
    }

    /**
     * 创建传统桌面快捷方式。
     */
    private fun createShortcut() {

        val resultIntent = Intent()

        val icon = Intent.ShortcutIconResource.fromContext(
            this,
            R.mipmap.ic_launcher
        )

        resultIntent.putExtra(
            Intent.EXTRA_SHORTCUT_NAME,
            getString(R.string.unlock_door)
        )

        resultIntent.putExtra(
            Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
            icon
        )

        /*
         * 关键：
         *
         * 快捷方式必须指向 ShortcutActivity，
         * 绝对不能指向 MainActivity。
         */
        val launchIntent = Intent(
            this,
            ShortcutActivity::class.java
        ).apply {
            action = Intent.ACTION_VIEW
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        }

        resultIntent.putExtra(
            Intent.EXTRA_SHORTCUT_INTENT,
            launchIntent
        )

        setResult(
            RESULT_OK,
            resultIntent
        )

        finish()
    }

    /**
     * 检查蓝牙权限。
     */
    private fun checkPermissionThenUnlock() {

        if (isFinishing || isDestroyed) {
            return
        }

        val neededPermissions = when {

            /*
             * Android 12+
             *
             * 当前开锁流程使用 BluetoothGatt.connectGatt，
             * 所以核心权限是 CONNECT。
             */
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            }

            /*
             * Android 11 及以下
             */
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                arrayOf(
                    Manifest.permission.BLUETOOTH
                )
            }

            else -> {
                emptyArray()
            }
        }

        val missingPermissions = neededPermissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {

            unlock()

        } else {

            /*
             * 权限弹窗只能在 Activity 已经处于前台时请求。
             *
             * 现在已经由 onPostResume 保证这一点。
             */
            pendingUnlock = {
                if (!isFinishing && !isDestroyed) {
                    unlock()
                }
            }

            permissionLauncher.launch(
                missingPermissions.toTypedArray()
            )
        }
    }

    /**
     * 单门禁开锁。
     */
    private fun unlock() {

        unlockJob?.let { job ->

            /*
             * 当前 job 正在执行 unlock() 本身时，
             * 不要取消自己。
             *
             * unlock() 只是启动新的协程逻辑，
             * 所以这里不做 cancel。
             */
        }

        lifecycleScope.launch {

            /*
             * 冷启动时 DataRepo 理论上已经在 Application 中初始化。
             *
             * 但某些 ROM / 进程恢复场景下仍可能遇到：
             *
             * Application → Activity → SharedPreferences
             *
             * 的调度差异。
             *
             * 因此这里做一个非常短的、最多 2 秒的重试。
             *
             * 不是固定 sleep 2 秒。
             * 一旦读取到数据立即继续。
             */
            val doors = loadDoorsWithRetry()

            if (!isActive) {
                return@launch
            }

            val targetDoorId =
                currentIntent?.getStringExtra(EXTRA_DOOR_ID)

            /*
             * 一键开锁
             */
            if (targetDoorId == ALL_DOORS_ID) {
                unlockAllDoors(doors)
                return@launch
            }

            /*
             * 指定门禁：
             * 优先找指定 ID。
             *
             * 如果 ID 无效，则兼容以前的行为：
             * 找第一个有效门禁。
             */
            val doorToUnlock = targetDoorId
                ?.let { id ->
                    doors.firstOrNull {
                        it.id == id &&
                                it.mac.isNotBlank() &&
                                it.key.isNotBlank()
                    }
                }
                ?: doors.firstOrNull {
                    it.mac.isNotBlank() &&
                            it.key.isNotBlank()
                }

            if (doorToUnlock == null) {

                UnlockRepo.resetUnlockStep()

                showToast(
                    "未找到可开锁的门禁，请先打开 App 配置"
                )

                delay(1200)

                if (!isFinishing && !isDestroyed) {
                    finish()
                }

                return@launch
            }

            /*
             * 这里不再自己显示一个 Toast 后就结束。
             *
             * 真正的状态由 UnlockRepo.unlockStep 提供：
             *
             * 准备开锁...
             * ↓
             * 等待蓝牙开启...
             * ↓
             * 已连接...
             * ↓
             * 服务发现...
             * ↓
             * 开锁成功
             */
            UnlockRepo.tryUnlock(
                doorToUnlock.mac,
                doorToUnlock.key
            )

            /*
             * 给 Compose 留一点时间显示最终结果。
             */
            delay(
                if (UnlockRepo.unlockStep.value.contains("成功")) {
                    800
                } else {
                    500
                }
            )

            if (!isFinishing && !isDestroyed) {
                finish()
            }
        }
    }

    /**
     * 读取门禁数据。
     *
     * 冷启动最多重试 20 次 × 100ms = 2 秒。
     *
     * 正常情况第一次就能拿到，
     * 所以不会人为增加 2 秒延迟。
     */
    private suspend fun loadDoorsWithRetry(): List<DoorDevice> {

        var doors = DataRepo.getDoors()

        if (doors.isNotEmpty()) {
            return doors
        }

        repeat(MAX_DATA_RETRY) {

            delay(DATA_RETRY_DELAY)

            if (!isActive) {
                return emptyList()
            }

            doors = DataRepo.getDoors()

            if (doors.isNotEmpty()) {
                return doors
            }
        }

        return doors
    }

    /**
     * 一键开锁。
     */
    private suspend fun unlockAllDoors(
        doors: List<DoorDevice>
    ) {

        val selectedDoors = doors
            .filter {
                it.isSelected &&
                        it.mac.isNotBlank() &&
                        it.key.isNotBlank()
            }
            .ifEmpty {
                doors.filter {
                    it.mac.isNotBlank() &&
                            it.key.isNotBlank()
                }
            }

        if (selectedDoors.isEmpty()) {

            UnlockRepo.resetUnlockStep()

            showToast(
                "未找到可开锁的门禁，请先打开 App 配置"
            )

            delay(1200)

            if (!isFinishing && !isDestroyed) {
                finish()
            }

            return
        }

        try {

            val doProbe =
                ConfigManager.getAutoScanEnabled()

            var matched: DoorDevice? = null

            if (doProbe) {

                matched = UnlockRepo.probeAndUnlock(
                    doors = selectedDoors,
                    perDeviceTimeoutMs =
                        ConfigManager.getScanDuration()
                )
            }

            /*
             * 探测成功：
             * 直接结束。
             */
            if (matched != null) {

                delay(800)

                if (!isFinishing && !isDestroyed) {
                    finish()
                }

                return
            }

            /*
             * 探测失败：
             * 进入轮询兜底。
             */
            val pollResult =
                UnlockRepo.pollAllDoors(
                    selectedDoors
                )

            if (pollResult != null) {
                delay(800)
            } else {
                delay(500)
            }

        } catch (e: Exception) {

            /*
             * 不让异常导致 Activity 直接闪退。
             */
            UnlockRepo.resetUnlockStep()

            showToast(
                "开锁失败：${e.message ?: "未知错误"}"
            )

            delay(800)
        }

        if (!isFinishing && !isDestroyed) {
            finish()
        }
    }

    override fun onDestroy() {
        /*
         * 取消当前 Activity 生命周期内的任务。
         *
         * UnlockRepo 本身的 BLE 操作也会因为协程取消而清理。
         */
        unlockJob?.cancel()
        unlockJob = null

        pendingUnlock = null

        super.onDestroy()
    }
}
