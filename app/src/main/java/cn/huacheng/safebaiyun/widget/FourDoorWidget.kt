package cn.huacheng.safebaiyun.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import cn.huacheng.safebaiyun.R
import cn.huacheng.safebaiyun.unlock.DataRepo

/**
 * 4×1 四个门禁部件
 *
 * 横向排列最多 4 个门禁，每个门禁下方显示名称。
 * 无需配置，固定显示 DataRepo 中的前 4 个门禁。
 */
class FourDoorReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget get() = FourDoorWidget
}

object FourDoorWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent()
            }
        }
    }

    @Composable
    private fun WidgetContent() {
        val doors = DataRepo.getDoors().take(4)

        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(62.dp)
                .cornerRadius(18.dp)
                .background(WidgetSurface)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (doors.isNotEmpty()) {
                    doors.forEachIndexed { index, door ->
                        DoorColumn(
                            doorId = door.id,
                            name = door.name,
                            hasConfig = door.mac.isNotEmpty() && door.key.isNotEmpty()
                        )
                        if (index != doors.lastIndex) {
                            Spacer(modifier = GlanceModifier.defaultWeight())
                        }
                    }
                }
                repeat(4 - doors.size) {
                    Spacer(modifier = GlanceModifier.defaultWeight())
                }
            }
        }
    }

    @Composable
    private fun DoorColumn(
        doorId: String,
        name: String,
        hasConfig: Boolean
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = GlanceModifier.width(60.dp)
        ) {
            Box(
                modifier = GlanceModifier
                    .size(34.dp)
                    .cornerRadius(17.dp)
                    .background(if (hasConfig) WidgetPrimary else WidgetSurfaceVariant)
                    .clickable(
                        actionRunCallback<UnlockDoorAction>(
                            actionParametersOf(KEY_DOOR_ID to doorId)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.unlock),
                    contentDescription = "解锁$name",
                    modifier = GlanceModifier.size(18.dp),
                    colorFilter = ColorFilter.tint(WidgetOnPrimary)   // ✅ 染成白色
                )
            }
            Spacer(modifier = GlanceModifier.height(3.dp))
            Text(
                text = name,
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = WidgetOnSurface
                ),
                maxLines = 1
            )
        }
    }
}
