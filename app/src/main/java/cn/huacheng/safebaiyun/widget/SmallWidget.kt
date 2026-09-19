package cn.huacheng.safebaiyun.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import cn.huacheng.safebaiyun.R
import cn.huacheng.safebaiyun.unlock.DataRepo

/**
 * 小号（1×1）桌面部件
 *
 * 只显示一个圆形开锁按钮，点击后启动 ShortcutActivity 开锁。
 * 添加时可选择绑定的门禁（复用 MediumWidgetConfigActivity）。
 */
class SmallReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget get() = SmallWidget
}

object SmallWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            // ✅ 在 Composable 作用域内读取 state
            val prefs: Preferences = currentState()
            val boundDoorId = prefs[doorIdKey]

            val door = boundDoorId
                ?.let { doorId -> DataRepo.getDoors().find { it.id == doorId } }
                ?: DataRepo.getDoors().firstOrNull()

            GlanceTheme {
                WidgetContent(doorId = door?.id)
            }
        }
    }

    @Composable
    private fun WidgetContent(doorId: String?) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            CircleIconButton(
                imageProvider = ImageProvider(R.drawable.unlock),
                contentDescription = "开门",
                backgroundColor = WidgetPrimary,
                contentColor = WidgetOnPrimary,
                onClick = actionRunCallback<UnlockDoorAction>(
                    actionParametersOf(KEY_DOOR_ID to (doorId ?: ""))
                )
            )
        }
    }
}
