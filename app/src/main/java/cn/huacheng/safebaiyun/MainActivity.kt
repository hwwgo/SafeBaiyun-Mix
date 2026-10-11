package cn.huacheng.safebaiyun

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cn.huacheng.safebaiyun.compose.HelpView
import cn.huacheng.safebaiyun.compose.MainView
import cn.huacheng.safebaiyun.compose.ManageDoorsView
import cn.huacheng.safebaiyun.compose.QRExportView
import cn.huacheng.safebaiyun.compose.QRImportView
import cn.huacheng.safebaiyun.compose.SettingsView
import cn.huacheng.safebaiyun.compose.UnlockHistoryView
import cn.huacheng.safebaiyun.compose.UnlockLightweight
import cn.huacheng.safebaiyun.theme.SafeBaiyunTheme
import cn.huacheng.safebaiyun.unlock.UnlockRecordManager
import cn.huacheng.safebaiyun.util.ConfigManager
import cn.huacheng.safebaiyun.widget.WidgetUnlockBus

class MainActivity : ComponentActivity() {

    // 0 = 等待中（显示纯背景），1 = widget overlay，2 = 正常主界面
    private val uiMode = mutableStateOf(0)
    private val overlayDoorId = mutableStateOf<String?>(null)

    // 覆盖层关闭时是否直接 finish：
    // - 冷启动（widget 拉起、此前不在前台）→ true，结束回到桌面
    // - App 已在运行，widget 通过 onNewIntent 触发 → false，仅切回正常界面
    private var finishOverlayOnClose = true

    override fun onCreate(savedInstanceState: Bundle?) {
        // 小部件拉起时切换为半透明主题，让解锁提示背景透出桌面（半透明）；
        // 正常点图标启动保持不透明主题，避免冷启动闪屏。
        // 必须在 super.onCreate() 之前调用，窗口才会在透明主题下创建。
        val isWidgetLaunch = intent.getStringExtra(EXTRA_WIDGET_DOOR_ID) != null ||
            WidgetUnlockBus.peek(this) != null
        if (isWidgetLaunch) {
            setTheme(R.style.Theme_SafeBaiyun_Translucent)
        }

        super.onCreate(savedInstanceState)

        // 小部件冷启动：与透明主题双保险，清空窗口背景，让解锁浮层透出桌面
        if (isWidgetLaunch) {
            window.setBackgroundDrawable(null)
        }

        ConfigManager.init(this)
        UnlockRecordManager.init(this)

        // 桌面小部件通过 Intent extra 传入门禁 ID；
        // 非空即表示本次是由小部件拉起的开锁请求（"" 表示开第一个门）。
        val doorId = resolveWidgetDoorId(intent)
        Log.d("MainActivity", "onCreate resolveWidgetDoorId=$doorId")
        if (doorId != null) {
            overlayDoorId.value = doorId
            finishOverlayOnClose = true
            uiMode.value = 1
        } else {
            uiMode.value = 2
        }

        setContent {
            // 小部件开锁浮层（mode == 1）依赖 Theme.SafeBaiyun.Translucent + windowShowWallpaper
            // 让“壁纸”图层透出作为半透明背景；正常主界面（mode == 2）保持不透明。
            SafeBaiyunTheme {
                val mode = uiMode.value
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    // 覆盖层模式（小部件开锁）背景透明：去掉原来的深色全屏遮罩，
                    // 与桌面快捷方式共用同一套轻量小卡片提示，保证两者界面一致
                    color = if (mode == 1) Color.Transparent else MaterialTheme.colorScheme.background
                ) {
                    if (mode == 0) {
                        Box(Modifier.fillMaxSize())
                    } else if (mode == 1) {
                        UnlockLightweight(
                            doorId = overlayDoorId.value,
                            onFinish = {
                                if (finishOverlayOnClose) {
                                    finish()
                                } else {
                                    uiMode.value = 2
                                }
                            },
                            requirePermission = true
                        )
                    } else {
                        MainNavContent()
                    }
                }
            }
        }
    }

    /**
     * App 已在运行（singleTask）时，widget 再次点击会把 Intent 送到这里，
     * 而不是 onCreate。必须处理，否则重复点击不会触发开锁。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val doorId = resolveWidgetDoorId(intent)
        if (doorId != null) {
            overlayDoorId.value = doorId
            finishOverlayOnClose = false
            uiMode.value = 1
            // App 已在前台时由小部件再次拉起：当前窗口是按不透明主题创建的，
            // 这里清空窗口背景，浮层才会透出 App 主界面（而非不透明黑窗）
            window.setBackgroundDrawable(null)
        }
    }

    /**
     * 解析小部件请求的门禁 ID；无请求返回 null。
     *
     * 优先级：
     *  1) WidgetUnlockBus（SharedPreferences 持久化）—— 这是 ColorOS 冷启动的可靠来源，
     *     因为桌面 PendingIntent 直接启动 Activity 时，冷启动 Intent 的 extra 会被系统丢弃。
     *  2) Intent extra —— 热启动（onNewIntent）/ 进程已活时的兜底。
     */
    private fun resolveWidgetDoorId(intent: Intent): String? {
        val busId = WidgetUnlockBus.consume(this)
        Log.d("MainActivity", "resolveWidgetDoorId busId=$busId extra=${intent.getStringExtra(EXTRA_WIDGET_DOOR_ID)}")
        if (busId != null) return busId
        return intent.getStringExtra(EXTRA_WIDGET_DOOR_ID)
    }

    @Composable
    private fun MainNavContent() {
        val navController = rememberNavController()
        NavHost(
            navController = navController,
            startDestination = "main",
        ) {
            composable(
                route = "main",
                enterTransition = { slideIn { IntOffset(-it.width, 0) } },
                exitTransition = { slideOut { IntOffset(-it.width, 0) } }
            ) {
                MainView(navController)
            }

            composable(
                route = "manage_doors",
                enterTransition = { slideIn { IntOffset(it.width, 0) } },
                exitTransition = { slideOut { IntOffset(it.width, 0) } }
            ) {
                ManageDoorsView(
                    navController = navController,
                    onSaved = { }
                )
            }

            composable(
                route = "helper",
                enterTransition = { slideIn { IntOffset(it.width, 0) } },
                exitTransition = { slideOut { IntOffset(it.width, 0) } }
            ) {
                HelpView(navController)
            }

            composable(
                route = "qr_export",
                enterTransition = { slideIn { IntOffset(it.width, 0) } },
                exitTransition = { slideOut { IntOffset(it.width, 0) } }
            ) {
                QRExportView(navController)
            }

            composable(
                route = "qr_import",
                enterTransition = { slideIn { IntOffset(it.width, 0) } },
                exitTransition = { slideOut { IntOffset(it.width, 0) } }
            ) {
                QRImportView(navController)
            }

            composable(
                route = "settings",
                enterTransition = { slideIn { IntOffset(it.width, 0) } },
                exitTransition = { slideOut { IntOffset(it.width, 0) } }
            ) {
                SettingsView(navController)
            }

            composable(
                route = "unlock_history",
                enterTransition = { slideIn { IntOffset(it.width, 0) } },
                exitTransition = { slideOut { IntOffset(it.width, 0) } }
            ) {
                UnlockHistoryView(navController)
            }
        }
    }

    companion object {
        /** 桌面小部件通过该 extra 传递要开的门禁 ID；
         *  取值为具体门禁 ID、"__ALL_DOORS__"（一键开锁）或 ""（开第一个门） */
        const val EXTRA_WIDGET_DOOR_ID = "widget_door_id"
    }
}
