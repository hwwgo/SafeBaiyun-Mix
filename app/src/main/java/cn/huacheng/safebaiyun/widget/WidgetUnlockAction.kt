package cn.huacheng.safebaiyun.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import cn.huacheng.safebaiyun.ShortcutActivity

/**
 * 所有桌面 Widget 的统一开锁入口。
 *
 * ✅ 关键修复：
 *    先用 WidgetUnlockBus 记录待处理的门禁 ID，
 *    再启动 ShortcutActivity。
 *    即使 ColorOS 拦截并先拉起 MainActivity，
 *    MainActivity 也能通过全局标志感知到 widget 点击并转交。
 */
val KEY_DOOR_ID = ActionParameters.Key<String>("door_id")

class UnlockDoorAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val doorId = parameters[KEY_DOOR_ID] ?: return

        // ✅ 关键：先在进程内记录，再启动 Activity
        WidgetUnlockBus.requestUnlock(doorId)

        val intent = Intent(context, ShortcutActivity::class.java).apply {
            putExtra(ShortcutActivity.EXTRA_DOOR_ID, doorId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        context.startActivity(intent)
    }
}
