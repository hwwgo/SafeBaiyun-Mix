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
 * 不直接启动 MainActivity，始终把点击事件交给 ShortcutActivity，
 * 这样无论 App 是冷启动还是热启动，都能先显示开锁状态。
 */
val KEY_DOOR_ID = ActionParameters.Key<String>("door_id")

class UnlockDoorAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val doorId = parameters[KEY_DOOR_ID] ?: return

        val intent = Intent(context, ShortcutActivity::class.java).apply {
            putExtra(ShortcutActivity.EXTRA_DOOR_ID, doorId)

            // 已存在 ShortcutActivity 时复用它并走 onNewIntent；
            // 冷启动时则直接创建 ShortcutActivity。
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        context.startActivity(intent)
    }
}
