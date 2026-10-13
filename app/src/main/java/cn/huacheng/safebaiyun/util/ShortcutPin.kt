package cn.huacheng.safebaiyun.util

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import cn.huacheng.safebaiyun.MainActivity
import cn.huacheng.safebaiyun.R
import cn.huacheng.safebaiyun.ShortcutActivity

/**
 * 在桌面「钉（pin）」快捷方式。
 *
 * - Android 8.0+：使用 [ShortcutManager.requestPinShortcut]，由系统弹出确认框。
 * - Android 7.1 及以下：发送 INSTALL_SHORTCUT 广播兜底（Manifest 已声明 INSTALL_SHORTCUT 权限）。
 *
 * 快捷方式点击后直接启动 [MainActivity]，intent 携带 EXTRA_DOOR_ID：
 *   - 传入 [ShortcutActivity.ALL_DOORS_ID] → 一键开锁全部门
 *   - 传入具体门 id → 仅开该门
 *
 * 为什么直接指向 MainActivity 而不经过 [ShortcutActivity] 中转：App 已在运行时，
 * 指向中转页的 Intent 会被系统按「前门（front door）」语义处理——任务重置到根
 * Activity（MainActivity），中转页根本不创建，部分 ROM 还会改写投递 Intent 的
 * component / 剥掉 action，导致开锁请求从未产生，表现为「热启动只弹主界面、不解锁」。
 * 直接指向根 Activity 则冷启动走 onCreate、热启动走 onNewIntent，都是系统标准行为。
 *
 * 兼容：旧版本钉出去的快捷方式仍指向 [ShortcutActivity]，冷启动走「中转页 + 总线」，
 * 热启动若 Intent 被根投递到主界面，由 MainActivity 解析 door_id extra，同样能开锁。
 *
 * 每个 doorId 对应唯一 shortcutId，重复钉会更新已有快捷方式而非新建。
 */
object ShortcutPin {

    fun pinShortcut(context: Context, doorId: String, name: String) {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
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
