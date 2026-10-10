package cn.huacheng.safebaiyun.util

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import cn.huacheng.safebaiyun.R
import cn.huacheng.safebaiyun.ShortcutActivity

/**
 * 在桌面「钉（pin）」快捷方式。
 *
 * - Android 8.0+：使用 [ShortcutManager.requestPinShortcut]，由系统弹出确认框。
 * - Android 7.1 及以下：发送 INSTALL_SHORTCUT 广播兜底（Manifest 已声明 INSTALL_SHORTCUT 权限）。
 *
 * 快捷方式点击后启动 [ShortcutActivity]，intent 携带 EXTRA_DOOR_ID：
 *   - 传入 [ShortcutActivity.ALL_DOORS_ID] → 一键开锁全部门
 *   - 传入具体门 id → 仅开该门
 *
 * 注意 intent 使用 [ShortcutActivity.ACTION_PINNED_UNLOCK]，避免误入 ShortcutActivity 的旧创建流程。
 * 每个 doorId 对应唯一 shortcutId，重复钉会更新已有快捷方式而非新建。
 */
object ShortcutPin {

    fun pinShortcut(context: Context, doorId: String, name: String) {
        val launchIntent = Intent(context, ShortcutActivity::class.java).apply {
            action = ShortcutActivity.ACTION_PINNED_UNLOCK
            putExtra(ShortcutActivity.EXTRA_DOOR_ID, doorId)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val sm = context.getSystemService(Context.SHORTCUT_SERVICE) as? ShortcutManager
            if (sm == null || !sm.isRequestPinShortcutSupported) {
                showToast("当前桌面启动器不支持创建快捷方式")
                return
            }
            val info = ShortcutInfo.Builder(context, shortcutId(doorId))
                .setShortLabel(name)
                .setLongLabel(name)
                .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(launchIntent)
                .build()
            val success = sm.requestPinShortcut(info, null)
            if (!success) showToast("创建快捷方式失败，请重试")
        } else {
            @Suppress("DEPRECATION")
            val addIntent = Intent("com.android.launcher.action.INSTALL_SHORTCUT").apply {
                putExtra(Intent.EXTRA_SHORTCUT_NAME, name)
                putExtra(Intent.EXTRA_SHORTCUT_INTENT, launchIntent)
                putExtra(
                    Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                    Intent.ShortcutIconResource.fromContext(context, R.mipmap.ic_launcher)
                )
                putExtra("duplicate", false)
            }
            context.sendBroadcast(addIntent)
            showToast("已发送创建快捷方式请求")
        }
    }

    private fun shortcutId(doorId: String): String = "door_shortcut_$doorId"
}
