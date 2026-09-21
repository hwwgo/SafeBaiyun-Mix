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

import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent

import androidx.glance.background
import androidx.glance.currentState

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
import cn.huacheng.safebaiyun.ShortcutActivity
import cn.huacheng.safebaiyun.unlock.DataRepo


class MediumReceiver :
    GlanceAppWidgetReceiver() {

    override val glanceAppWidget:
            GlanceAppWidget
        get() = MediumWidget
}


object MediumWidget :
    GlanceAppWidget() {

    override val sizeMode:
            SizeMode =
        SizeMode.Single


    override suspend fun provideGlance(
        context: Context,
        id: GlanceId
    ) {

        provideContent {

            val prefs:
                    Preferences =
                currentState()

            val boundDoorId =
                prefs[doorIdKey]


            /*
             * 判断 Widget 绑定的是：
             *
             * 1. 一键开锁
             * 2. 指定门禁
             * 3. 未绑定
             */
            val displayName =
                when {

                    boundDoorId ==
                            ShortcutActivity
                                .ALL_DOORS_ID -> {

                        "一键开锁"
                    }


                    else -> {

                        boundDoorId
                            ?.let { doorId ->

                                DataRepo
                                    .getDoors()
                                    .find {
                                        it.id == doorId
                                    }
                                    ?.name
                            }

                            ?: DataRepo
                                .getDoors()
                                .firstOrNull()
                                ?.name

                            ?: "无门禁"
                    }
                }


            GlanceTheme {

                WidgetContent(

                    doorId =
                        boundDoorId,

                    doorName =
                        displayName
                )
            }
        }
    }


    @Composable
    private fun WidgetContent(

        doorId: String?,

        doorName: String
    ) {

        Row(

            modifier =
                GlanceModifier
                    .background(
                        WidgetSurface
                    )
                    .fillMaxWidth()
                    .cornerRadius(
                        50.dp
                    )
                    .padding(
                        start = 24.dp,
                        top = 12.dp,
                        bottom = 12.dp,
                        end = 12.dp
                    ),

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Column {

                Text(

                    text =
                        doorName,

                    style =
                        TextStyle(

                            fontWeight =
                                FontWeight.Medium,

                            fontSize =
                                16.sp,

                            color =
                                WidgetOnSurface
                        )
                )


                Text(

                    text =
                        "点击解锁",

                    style =
                        TextStyle(

                            fontSize =
                                14.sp,

                            color =
                                WidgetOnSurfaceVariant
                        )
                )
            }


            Spacer(
                modifier =
                    GlanceModifier
                        .defaultWeight()
            )


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
                    actionStartActivity<cn.huacheng.safebaiyun.ShortcutActivity>(

                        actionParametersOf(

                            KEY_DOOR_ID to
                                    (doorId ?: "")
                        )
                    )
            )
        }
    }
    }
