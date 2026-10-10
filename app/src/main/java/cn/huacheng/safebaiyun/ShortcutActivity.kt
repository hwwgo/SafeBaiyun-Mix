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
import cn.huacheng.safebaiyun.compose.UnlockOverlay
import cn.huacheng.safebaiyun.theme.SafeBaiyunTheme
import cn.huacheng.safebaiyun.unlock.UnlockRepo
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
 * 解锁流程与进度 UI 统一交给 [UnlockOverlay] 处理。
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
        val busDoorId = WidgetUnlockBus.consume(this)
        resolvedDoorId = busDoorId ?: intent.getStringExtra(EXTRA_DOOR_ID)
        Log.d(TAG, "resolvedDoorId=$resolvedDoorId (busDoorId=$busDoorId)")

        UnlockRepo.resetUnlockStep()

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
            showUnlockOverlay()
        } else {
            pendingUnlock = { showUnlockOverlay() }
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun showUnlockOverlay() {
        setContent {
            SafeBaiyunTheme {
                // 权限已由 checkPermissionThenUnlock 保证，无需在覆盖层内再次检查。
                UnlockOverlay(
                    doorId = resolvedDoorId,
                    onFinish = { finish() },
                    requirePermission = false
                )
            }
        }
    }
}
