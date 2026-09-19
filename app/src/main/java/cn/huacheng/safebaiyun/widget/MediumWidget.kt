package cn.huacheng.safebaiyun.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.appwidget.state.currentState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import cn.huacheng.safebaiyun.R
import cn.huacheng.safebaiyun.unlock.DataRepo

class MediumReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget get() = MediumWidget
}

object MediumWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // ✅ 读取该 widget 绑定的门禁 ID
        val prefs = currentState<Preferences>()
        val boundDoorId = prefs[doorIdKey]

        // 优先用绑定的；找不到（未配置或被删）时 fallback 到第一个
        val door = boundDoorId
            ?.let { id -> DataRepo.getDoors().find { it.id == id } }
            ?: DataRepo.getDoors().firstOrNull()

        provideContent {
            GlanceTheme {
                WidgetContent(
                    doorId = door?.id,
                    doorName = door?.name ?: "无门禁"
                )
            }
        }
    }

    @Composable
    private fun WidgetContent(doorId: String?, doorName: String) {
        Row(
            modifier = GlanceModifier
                .background(WidgetSurface)
                .fillMaxWidth()
                .cornerRadius(50.dp)
                .padding(start = 24.dp, top = 12.dp, bottom = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = doorName,
                    style = TextStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        color = WidgetOnSurface
                    )
                )
                Text(
                    text = "点击解锁",
                    style = TextStyle(
                        fontSize = 14.sp,
                        color = WidgetOnSurfaceVariant
                    )
                )
            }
            Spacer(modifier = GlanceModifier.defaultWeight())
            CircleIconButton(
                imageProvider = ImageProvider(R.drawable.unlock),
                contentDescription = "",
                backgroundColor = WidgetPrimary,
                contentColor = WidgetOnPrimary,
                onClick = actionRunCallback<UnlockDoorAction>(
                    actionParametersOf(KEY_DOOR_ID to (doorId ?: ""))
                )
            )
        }
    }
}
