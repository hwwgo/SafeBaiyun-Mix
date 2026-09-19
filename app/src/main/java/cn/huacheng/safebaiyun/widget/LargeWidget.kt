package cn.huacheng.safebaiyun.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState         // ✅ 修改
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import cn.huacheng.safebaiyun.R
import cn.huacheng.safebaiyun.unlock.DataRepo

class LargeReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget get() = LargeWidget
}

object LargeWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // ✅ 读取该 widget 绑定的门禁 ID（显式类型）
        val prefs: Preferences = currentState()
        val boundDoorId = prefs[doorIdKey]

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
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(170.dp)
                .cornerRadius(24.dp)
                .background(WidgetSurface)
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Image(
                    modifier = GlanceModifier.size(68.dp, 96.dp),
                    provider = ImageProvider(R.drawable.guangzhou),
                    contentScale = ContentScale.FillBounds,
                    contentDescription = null
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Box(modifier = GlanceModifier.padding(top = 8.dp)) {
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
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = doorName,
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = WidgetOnSurface
                )
            )
            Text(
                text = "点击解锁门禁",
                style = TextStyle(
                    fontSize = 14.sp,
                    color = WidgetOnSurfaceVariant
                )
            )
        }
    }
}
