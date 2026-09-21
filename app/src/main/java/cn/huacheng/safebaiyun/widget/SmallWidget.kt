package cn.huacheng.safebaiyun.widget

import android.content.Context
import android.content.Intent

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

import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.provideContent

import androidx.glance.currentState

import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding

import cn.huacheng.safebaiyun.ShortcutActivity
import cn.huacheng.safebaiyun.R
import cn.huacheng.safebaiyun.unlock.DataRepo


/**
 * 小号（1×1）桌面部件。
 *
 * 只显示一个开锁按钮。
 *
 * 点击后统一进入：
 *
 * UnlockDoorAction
 *      ↓
 * ShortcutActivity
 */
class SmallReceiver :
    GlanceAppWidgetReceiver() {

    override val glanceAppWidget:
            GlanceAppWidget
        get() = SmallWidget
}


object SmallWidget :
    GlanceAppWidget() {

    override val sizeMode:
            SizeMode =
        SizeMode.Single


    override suspend fun provideGlance(
        context: Context,
        id: GlanceId
    ) {

        provideContent {

            /*
             * 获取当前 Widget 绑定的门禁。
             */
            val prefs:
                    Preferences =
                currentState()

            val boundDoorId =
                prefs[doorIdKey]


            /*
             * 优先使用 Widget 绑定的门禁。
             *
             * 如果没有绑定，
             * 兼容原来的行为：
             * 使用第一个门禁。
             */
            val door =
                boundDoorId
                    ?.let { doorId ->

                        DataRepo
                            .getDoors()
                            .find {
                                it.id == doorId
                            }
                    }
                    ?: DataRepo
                        .getDoors()
                        .firstOrNull()


            GlanceTheme {

                WidgetContent(
                    doorId =
                        door?.id
                )
            }
        }
    }


    @Composable
    private fun WidgetContent(
        doorId: String?
    ) {

        Box(

            modifier =
                GlanceModifier
                    .fillMaxSize()
                    .padding(6.dp),

            contentAlignment =
                Alignment.Center
        ) {

            CircleIconButton(

                imageProvider =
                    ImageProvider(
                        R.drawable.unlock
                    ),

                contentDescription =
                    "开门",

                backgroundColor =
                    WidgetPrimary,

                contentColor =
                    WidgetOnPrimary,

                onClick =
                    actionStartActivity(
                                    Intent().apply {
                                        setClassName(
                                            "cn.huacheng.safebaiyun",
                                            "cn.huacheng.safebaiyun.ShortcutActivity"
                                        )
                                        putExtra(ShortcutActivity.EXTRA_DOOR_ID, (doorId ?: ""))
                                    }
                                )
            )
        }
    }
    }
