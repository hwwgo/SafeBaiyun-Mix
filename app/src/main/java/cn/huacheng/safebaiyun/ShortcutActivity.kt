package cn.huacheng.safebaiyun

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import cn.huacheng.safebaiyun.widget.WidgetUnlockBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 快捷开门 Activity（对话框形式）
 *
 * ✅ 优先从 WidgetUnlockBus 读取 doorId（跨 Activity 传递），
 *    其次从 Intent extra 读取（正常启动路径）。
 */
class ShortcutActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ShortcutActivity"
        const val EXTRA_DOOR_ID = "door_id"
        const val ALL_DOORS_ID = "__ALL_DOORS__"
    }

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pendingUnlock: (() -> Unit)? = null

    /** 解析出的最终目标 doorId */
    private var resolvedDoorId: String? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.values.all { it }
        if (allGranted) {
            pendingUnlock?.invoke()
        } else {
            showToast("未授予蓝牙权限，无法开锁")
            activityScope.launch {
                delay(2000)
                finish()
            }
        }
        pendingUnlock = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate, action=${intent.action}")

        // ✅ 优先从 WidgetUnlockBus 读取（跨 MainActivity/ShortcutActivity 传递）
        val busDoorId = WidgetUnlockBus.consume()
        resolvedDoorId = busDoorId ?: intent.getStringExtra(EXTRA_DOOR_ID)
        Log.d(TAG, "resolvedDoorId=$resolvedDoorId (busDoorId=$busDoorId)")

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
                        .background(Color(0x66000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 32.dp, vertical = 32.dp),
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
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        if (intent.action == Intent.ACTION_CREATE_SHORTCUT) {
            createShortcut()
        } else {
            activityScope.launch {
                delay(200)
                checkPermissionThenUnlock()
            }
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
            unlock()
        } else {
            pendingUnlock = { unlock() }
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun unlock() {
        val targetDoorId = resolvedDoorId

        if (targetDoorId == ALL_DOORS_ID) {
            unlockAllDoors()
            return
        }

        activityScope.launch {
            var doors = DataRepo.getDoors()
            if (doors.isEmpty()) {
                delay(300)
                doors = DataRepo.getDoors()
            }

            val doorToUnlock = targetDoorId
                ?.let { id -> doors.find { it.id == id } }
                ?.takeIf { it.mac.isNotEmpty() && it.key.isNotEmpty() }
                ?: doors.firstOrNull { it.mac.isNotEmpty() && it.key.isNotEmpty() }
                ?: doors.firstOrNull()

            if (doorToUnlock == null || doorToUnlock.mac.isEmpty() || doorToUnlock.key.isEmpty()) {
                showToast("未找到可开锁的门禁")
                delay(2000)
                finish()
                return@launch
            }

            showToast("正在解锁 ${doorToUnlock.name}")
            val success = UnlockRepo.tryUnlock(doorToUnlock.mac, doorToUnlock.key)
            if (success) {
                delay(800)
            }
            finish()
        }
    }

    private fun unlockAllDoors() {
        activityScope.launch {
            var doors = DataRepo.getDoors()
            if (doors.isEmpty()) {
                delay(300)
                doors = DataRepo.getDoors()
            }

            val selectedDoors = doors.filter { it.isSelected }.ifEmpty { doors }

            if (selectedDoors.isEmpty()) {
                showToast("未找到可开锁的门禁")
                delay(2000)
                finish()
                return@launch
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
            } catch (e: Exception) {
                Log.e(TAG, "一键开锁异常", e)
                success = false
            }

            if (success) {
                delay(800)
            }
            finish()
        }
    }
}
