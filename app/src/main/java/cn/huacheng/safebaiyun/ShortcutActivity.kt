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
import cn.huacheng.safebaiyun.compose.UnlockLightweight
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
 * 解锁流程与进度 UI 统一交给 UnlockLightweight 处理（轻量提示）。
 */
class ShortcutActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ShortcutActivity"
        const val EXTRA_DOOR_ID = "door_id"
        const val ALL_DOORS_ID = "__ALL_DOORS__"
        /** 由 App 内钉到桌面的快捷方式所携带的 action（区别于已删除的 CREATE_SHORTCUT 创建流程） */
        const val ACTION_PINNED_UNLOCK = "cn.huacheng.safebaiyun.action.PINNED_UNLOCK"
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

        // 进入本 Activity 的来源：桌面小部件（广播桥）或已钉到桌面的快捷方式。
        // 创建快捷方式的入口已改为 App 内主动钉（见 ShortcutPin），原 activity-alias 已删除，
        // 因此这里统一走解锁流程（intent.action 不会再是 CREATE_SHORTCUT）。
        checkPermissionThenUnlock()
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
        // 轻量提示：半透明遮罩 + 居中紧凑的“正在解锁”小卡片，
        // 桌面快捷方式与小部件共用同一组件、同一主题，背景完全一致。
        setContent {
            SafeBaiyunTheme {
                UnlockLightweight(
                    doorId = resolvedDoorId,
                    onFinish = { finish() }
                )
            }
        }
    }
}
