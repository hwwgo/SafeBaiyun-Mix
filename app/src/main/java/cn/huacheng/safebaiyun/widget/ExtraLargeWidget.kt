package cn.huacheng.safebaiyun.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import cn.huacheng.safebaiyun.R
import cn.huacheng.safebaiyun.ShortcutActivity

// ============================================================
//  顶层变量 & ActionCallback
//  ✅ 必须放在顶层，Glance 才能通过反射正确定位类
// ============================================================

/** 传递门禁 ID 的参数 key */
val KEY_DOOR_ID = ActionParameters.Key<String>("door_id")

/**
 * 解锁门禁的回调 —— 点击特大号部件中某个门禁按钮时触发。
 */
class UnlockDoorAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val doorId = parameters[KEY_DOOR_ID]
        if (doorId != null) {
            val intent = Intent(context, ShortcutActivity::class.java).apply {
                putExtra(ShortcutActivity.EXTRA_DOOR_ID, doorId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        }
    }
}

// ============================================================
//  Receiver & Widget
// ============================================================

class ExtraLargeReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget get() = ExtraLargeWidget
}

object ExtraLargeWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            // ✅ 使用 ColorOS 主题
            ColorOSGlanceTheme {
                WidgetContent(context)
            }
        }
    }

    @Composable
    private fun WidgetContent(context: Context) {
        val doors = cn.huacheng.safebaiyun.unlock.DataRepo.getDoors().take(3)
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(170.dp)
                .cornerRadius(24.dp)
                .background(GlanceTheme.colors.surface)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                doors.forEach { door ->
                    DoorButton(
                        context = context,
                        doorId = door.id,
                        name = door.name,
                        hasConfig = door.mac.isNotEmpty() && door.key.isNotEmpty()
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                }
                repeat(3 - doors.size) {
                    Spacer(modifier = GlanceModifier.defaultWeight())
                }
            }
            Text(
                text = "白云通 · 三键快开",
                style = TextStyle(
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                ),
                modifier = GlanceModifier.padding(top = 4.dp)
            )
        }
    }

    @Composable
    private fun DoorButton(
        context: Context,
        doorId: String,
        name: String,
        hasConfig: Boolean,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = GlanceModifier.width(90.dp).fillMaxHeight(),
        ) {
            Box(
                modifier = GlanceModifier.defaultWeight(),
                contentAlignment = Alignment.Center
            ) {
                CircleIconButton(
                    imageProvider = ImageProvider(R.drawable.unlock),
                    contentDescription = "解锁$name",
                    backgroundColor = if (hasConfig) {
                        GlanceTheme.colors.primary
                    } else {
                        GlanceTheme.colors.surfaceVariant
                    },
                    contentColor = if (hasConfig) {
                        GlanceTheme.colors.onPrimary
                    } else {
                        GlanceTheme.colors.onSurfaceVariant
                    },
                    onClick = actionRunCallback<UnlockDoorAction>(
                        actionParametersOf(KEY_DOOR_ID to doorId)
                    )
                )
            }
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = name,
                style = TextStyle(
                    fontSize = 13.sp,
                    color = GlanceTheme.colors.onSurface
                ),
                maxLines = 1
            )
        }
    }
}
