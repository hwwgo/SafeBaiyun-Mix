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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 快捷开门 Activity（桌面快捷方式 / 部件按钮 启动）
 *
 * 通过 Intent extra [EXTRA_DOOR_ID] 指定要开的门禁 ID。
 *   - 传具体门禁 id → 开该门禁
 *   - 传 [ALL_DOORS_ID] → 一键开锁（探测 + 轮询所有勾选门禁）
 *   - 未传 → 开第一个有效门禁
 *
 * 关键点：
 * 1. 冷启动时也始终先停留在本 Activity 的开锁状态页，不跳 MainActivity。
 * 2. 使用 lifecycleScope 管理真正的开锁协程，避免 Activity 生命周期与开锁协程脱节。
 * 3. singleTop + onNewIntent 支持 App 已运行时从桌面部件再次触发，并正确更新门禁 ID。
 */
class ShortcutActivity : ComponentActivity() {

    companion object {
        const val EXTRA_DOOR_ID = "door_id"
        const val ALL_DOORS_ID = "__ALL_DOORS__"
    }

    private var unlockJob: Job? = null
    private var pendingUnlock: (() -> Unit)? = null
    private var unlockStarted = false
    private var currentIntent: Intent? = null

    /**
     * 权限请求 launcher。
     *
     * 权限通过后，启动真正的开锁 Job；不再依赖一个独立的 ActivityScope。
     */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.values.all { it }

        if (allGranted) {
            pendingUnlock?.invoke()
        } else {
            pendingUnlock = null
            showToast("未授予蓝牙权限，无法开锁")
            lifecycleScope.launch {
                delay(2000)
                if (!isFinishing && !isDestroyed) {
                    startActivity(Intent(this@ShortcutActivity, MainActivity::class.java))
                    finish()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        currentIntent = intent
        UnlockRepo.resetUnlockStep()

        setContent {
            SafeBaiyunTheme {
                val unlockStep by UnlockRepo.unlockStep.collectAsState()

                val displayText = unlockStep.ifEmpty { "准备开锁..." }
                val textColor = when {
                    unlockStep.contains("成功") -> ColorOSSuccess
                    unlockStep.contains("失败") || unlockStep.contains("超时") ->
                        ColorOSError
                    else -> MaterialTheme.colorScheme.onBackground
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = displayText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = textColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            }
        }

        if (intent.action == Intent.ACTION_CREATE_SHORTCUT) {
            createShortcut()
        }
    }

    /**
     * Activity 真正进入前台后才启动开锁。
     *
     * 这一步是冷启动修复的核心：不在 onCreate 里立刻启动开锁协程，
     * 避免桌面/Launcher 启动 Activity 时 Activity 尚未完成前台生命周期。
     */
    override fun onPostResume() {
        super.onPostResume()

        if (intent.action != Intent.ACTION_CREATE_SHORTCUT) {
            startUnlockIfNeeded()
        }
    }

    /**
     * Widget/桌面快捷方式再次点击时，如果 ShortcutActivity 已经在栈顶，
     * Manifest 的 singleTop 会走到这里。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        setIntent(intent)
        currentIntent = intent

        unlockJob?.cancel()
        unlockJob = null

        pendingUnlock = null
        unlockStarted = false

        UnlockRepo.resetUnlockStep()

        if (intent.action != Intent.ACTION_CREATE_SHORTCUT) {
            startUnlockIfNeeded()
        }
    }

    private fun startUnlockIfNeeded() {
        if (unlockStarted || isFinishing || isDestroyed) {
            return
        }

        unlockStarted = true

        // 给 Activity 一帧时间完成 resumed 状态，再做权限检查。
        lifecycleScope.launch {
            delay(100)
            if (!isFinishing && !isDestroyed) {
                checkPermissionThenUnlock()
            }
        }
    }

    private fun createShortcut() {
        val shortcutIntent = Intent(this, ShortcutActivity::class.java)

        val resultIntent = Intent().apply {
            val icon = Intent.ShortcutIconResource.fromContext(
                this@ShortcutActivity,
                R.mipmap.ic_launcher
            )

            putExtra(
                Intent.EXTRA_SHORTCUT_NAME,
                getString(R.string.unlock_door)
            )
            putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, icon)
            putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
        }

        setResult(RESULT_OK, resultIntent)
        finish()
    }

    /**
     * 检查蓝牙权限，不足时请求，足够时启动真正的开锁 Job。
     *
     * 开锁只需要 BLUETOOTH_CONNECT，不需要 BLUETOOTH_SCAN，
     * 因为 UnlockRepo 使用的是已知 MAC 地址直接连接 GATT。
     */
    private fun checkPermissionThenUnlock() {
        val neededPermissions = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT
            )

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> arrayOf(
                Manifest.permission.BLUETOOTH
            )

            else -> emptyArray()
        }

        val missing = neededPermissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            launchUnlockJob()
        } else {
            pendingUnlock = {
                launchUnlockJob()
            }
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    /**
     * 所有实际开锁工作统一挂在 lifecycleScope 中。
     */
    private fun launchUnlockJob() {
        unlockJob?.cancel()

        unlockJob = lifecycleScope.launch {
            try {
                unlock()
            } catch (_: CancellationException) {
                // 新的 Widget 点击或 Activity 销毁导致的取消属于正常流程。
                throw CancellationException()
            }
        }
    }

    /**
     * 开锁入口。
     */
    private suspend fun unlock() {
        val targetDoorId = currentIntent?.getStringExtra(EXTRA_DOOR_ID)

        if (targetDoorId == ALL_DOORS_ID) {
            unlockAllDoors()
            return
        }

        val doors = loadDoorsWithRetry()

        val doorToUnlock = targetDoorId
            ?.let { id -> doors.find { it.id == id } }
            ?.takeIf { it.mac.isNotEmpty() && it.key.isNotEmpty() }
            ?: doors.firstOrNull {
                it.mac.isNotEmpty() && it.key.isNotEmpty()
            }
            ?: doors.firstOrNull()

        if (
            doorToUnlock == null ||
            doorToUnlock.mac.isEmpty() ||
            doorToUnlock.key.isEmpty()
        ) {
            showToast("未找到可开锁的门禁，请先打开 App 配置")
            delay(1500)
            finish()
            return
        }

        showToast("正在解锁 ${doorToUnlock.name}")

        val success = UnlockRepo.tryUnlock(
            doorToUnlock.mac,
            doorToUnlock.key
        )

        if (success) {
            delay(800)
        }

        finish()
    }

    /**
     * 冷启动时 DataRepo 可能还在 Application 初始化阶段。
     * 不再只固定等 300ms，而是短时间重试，直到读到数据或超时。
     */
    private suspend fun loadDoorsWithRetry(): List<DoorDevice> {
        repeat(20) { index ->
            val doors = DataRepo.getDoors()

            if (doors.isNotEmpty()) {
                return doors
            }

            if (index < 19) {
                delay(100)
            }
        }

        return DataRepo.getDoors()
    }

    private suspend fun unlockAllDoors() {
        val doors = loadDoorsWithRetry()

        val selectedDoors = doors
            .filter { it.isSelected }
            .ifEmpty { doors }

        if (selectedDoors.isEmpty()) {
            showToast("未找到可开锁的门禁，请先打开 App 配置")
            delay(1500)
            finish()
            return
        }

        showToast("正在一键开锁 ${selectedDoors.size} 个门禁")

        var success = false

        try {
            val doProbe = ConfigManager.getAutoScanEnabled()

            var matched: DoorDevice? = null

            if (doProbe) {
                matched = UnlockRepo.probeAndUnlock(
                    doors = selectedDoors,
                    perDeviceTimeoutMs = ConfigManager.getScanDuration()
                )
            }

            if (matched != null) {
                success = true
            } else {
                val pollResult = UnlockRepo.pollAllDoors(selectedDoors)
                success = pollResult != null
            }
        } catch (e: CancellationException) {
            // 不能吞掉取消异常，否则新的 Widget 点击无法可靠接管旧任务。
            throw e
        } catch (_: Exception) {
            success = false
        }

        if (success) {
            delay(800)
        }

        finish()
    }

    override fun onDestroy() {
        unlockJob?.cancel()
        unlockJob = null
        pendingUnlock = null
        super.onDestroy()
    }
}
