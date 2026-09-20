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
 * ✅ 加 from_widget_click 标识：
 *    如果 ColorOS 拦截了 ShortcutActivity 的启动、强行先拉起 MainActivity，
 *    MainActivity 检测到这个标识后会立即转交给 ShortcutActivity 并 finish 自己。
 */
val KEY_DOOR_ID = ActionParameters.Key<String>("door_id")

/** 标识：本次启动来自 widget 点击 */
const val EXTRA_FROM_WIDGET = "from_widget_click"

class UnlockDoorAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val doorId = parameters[KEY_DOOR_ID] ?: return

        val intent = Intent(context, ShortcutActivity::class.java).apply {
            putExtra(ShortcutActivity.EXTRA_DOOR_ID, doorId)
            putExtra(EXTRA_FROM_WIDGET, true)          // ✅ 关键：加标识
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        context.startActivity(intent)
    }
}
