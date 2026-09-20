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
 * 关键点：
 *  - 不直接启动 MainActivity，始终把点击事件交给 ShortcutActivity
 *  - 冷启动时也先显示开锁状态，不跳主界面
 *  - flags 只保留 NEW_TASK（Widget 必需）+ SINGLE_TOP（复用实例）
 *  - 去掉 CLEAR_TOP，避免误清任务栈导致回退主界面
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
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        context.startActivity(intent)
    }
}
