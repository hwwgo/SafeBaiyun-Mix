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
import cn.huacheng.safebaiyun.theme.ColorOSError
import cn.huacheng.safebaiyun.theme.ColorOSSuccess
import cn.huacheng.safebaiyun.theme.SafeBaiyunTheme
import cn.huacheng.safebaiyun.unlock.DataRepo
import cn.huacheng.safebaiyun.unlock.DoorDevice
import cn.huacheng.safebaiyun.unlock.UnlockRepo
import cn.huacheng.safebaiyun.util.ConfigManager
import cn.huacheng.safebaiyun.util.showToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
 * ✅ 权限不足时，本 Activity 自己请求权限，授权后自动继续开锁，不跳转主界面。
 */
class ShortcutActivity : ComponentActivity() {

    companion object {
        const val EXTRA_DOOR_ID = "door_id"

        /** 特殊标识：代表"一键开锁" */
        const val ALL_DOORS_ID = "__ALL_DOORS__"
    }

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** 权限授权后要执行的开锁动作 */
    private var pendingUnlock: (() -> Unit)? = null

    /** 权限请求 launcher */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.values.all { it }
        if (allGranted) {
            // ✅ 全部授予 → 继续开锁
            pendingUnlock?.invoke()
        } else {
            // ❌ 用户拒绝 → 提示并跳转主界面
            showToast("未授予蓝牙权限")
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        pendingUnlock = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        UnlockRepo.resetUnlockStep()

        setContent {
            SafeBaiyunTheme {
                val unlockStep by UnlockRepo.unlockStep.collectAsState()
                val displayText = unlockStep.ifEmpty { "准备开锁..." }

                val textColor = when {
                    unlockStep.contains("成功") -> ColorOSSuccess
                    unlockStep.contains("失败") || unlockStep.contains("超时") -> ColorOSError
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
        } else {
            checkPermissionThenUnlock()
        }
    }

    private fun createShortcut() {
        val intent = Intent()
        val icon = Intent.ShortcutIconResource.fromContext(this, R.mipmap.ic_launcher)
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(R.string.unlock_door))
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, icon)
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, Intent(this, ShortcutActivity::class.java))
        setResult(RESULT_OK, intent)
        finish()
    }

    /**
     * 检查蓝牙权限，不足时请求，足够时直接开锁
     */
    private fun checkPermissionThenUnlock() {
        val neededPermissions = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            else -> emptyArray()
        }

        val missing = neededPermissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            // ✅ 权限齐全，直接开锁
            unlock()
        } else {
            // ⏳ 权限不足，先请求，授权后自动继续
            pendingUnlock = { unlock() }
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun unlock() {
        val targetDoorId = intent.getStringExtra(EXTRA_DOOR_ID)

        // ✅ 判断是否为一键开锁
        if (targetDoorId == ALL_DOORS_ID) {
            unlockAllDoors()
            return
        }

        // 单门禁开锁
        val doors = DataRepo.getDoors()
        val doorToUnlock = targetDoorId
            ?.let { id -> doors.find { it.id == id } }
            ?.takeIf { it.mac.isNotEmpty() && it.key.isNotEmpty() }
            ?: doors.firstOrNull { it.mac.isNotEmpty() && it.key.isNotEmpty() }
            ?: doors.firstOrNull()

        if (doorToUnlock == null || doorToUnlock.mac.isEmpty() || doorToUnlock.key.isEmpty()) {
            showToast("请先初始化门禁")
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        showToast("正在解锁 ${doorToUnlock.name}")

        activityScope.launch {
            val success = UnlockRepo.tryUnlock(doorToUnlock.mac, doorToUnlock.key)
            if (success) {
                delay(800)
            }
            finish()
        }
    }

    /**
     * 一键开锁流程：探测 + 轮询（与主界面逻辑一致）
     */
    private fun unlockAllDoors() {
        val selectedDoors = DataRepo.getDoors().filter { it.isSelected }

        if (selectedDoors.isEmpty()) {
            showToast("请先在 App 中勾选要开锁的门禁")
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        showToast("正在一键开锁 ${selectedDoors.size} 个门禁")

        activityScope.launch {
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
            } catch (_: Exception) {
                success = false
            }

            if (success) {
                delay(800)
            }
            finish()
        }
    }
}
