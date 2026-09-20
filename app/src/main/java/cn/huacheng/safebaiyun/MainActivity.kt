package cn.huacheng.safebaiyun

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cn.huacheng.safebaiyun.compose.HelpView
import cn.huacheng.safebaiyun.compose.MainView
import cn.huacheng.safebaiyun.compose.ManageDoorsView
import cn.huacheng.safebaiyun.compose.QRExportView
import cn.huacheng.safebaiyun.compose.QRImportView
import cn.huacheng.safebaiyun.compose.SettingsView
import cn.huacheng.safebaiyun.theme.SafeBaiyunTheme
import cn.huacheng.safebaiyun.unlock.DataRepo
import cn.huacheng.safebaiyun.unlock.UnlockRepo
import cn.huacheng.safebaiyun.util.ConfigManager
import cn.huacheng.safebaiyun.widget.WidgetUnlockBus

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ 关键修复：如果本次启动是 widget 触发的
        //    ColorOS 可能会拦截 ShortcutActivity 并强行拉起 MainActivity。
        //    从全局标志读取 doorId，立即转交给 ShortcutActivity。
        val widgetDoorId = WidgetUnlockBus.consume()
        if (widgetDoorId != null) {
            val newIntent = Intent(this, ShortcutActivity::class.java).apply {
                putExtra(ShortcutActivity.EXTRA_DOOR_ID, widgetDoorId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(newIntent)
            finish()
            return
        }

        ConfigManager.init(this)
        UnlockRepo.init(lifecycleScope)

        setContent {
            SafeBaiyunTheme {
                val navController = rememberNavController()
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = "main",
                    ) {
                        composable("main", enterTransition = {
                            slideIn { IntOffset(-it.width, 0) }
                        }, exitTransition = {
                            slideOut { IntOffset(-it.width, 0) }
                        }) {
                            MainView(navController)
                        }

                        composable("manage_doors", enterTransition = {
                            slideIn { IntOffset(it.width, 0) }
                        }, exitTransition = {
                            slideOut { IntOffset(it.width, 0) }
                        }) {
                            ManageDoorsView(
                                navController = navController,
                                onSaved = { /* 可在此刷新数据 */ }
                            )
                        }

                        composable("helper", enterTransition = {
                            slideIn { IntOffset(it.width, 0) }
                        }, exitTransition = {
                            slideOut { IntOffset(it.width, 0) }
                        }) {
                            HelpView(navController)
                        }

                        composable("qr_export", enterTransition = {
                            slideIn { IntOffset(it.width, 0) }
                        }, exitTransition = {
                            slideOut { IntOffset(it.width, 0) }
                        }) {
                            QRExportView(navController)
                        }

                        composable("qr_import", enterTransition = {
                            slideIn { IntOffset(it.width, 0) }
                        }, exitTransition = {
                            slideOut { IntOffset(it.width, 0) }
                        }) {
                            QRImportView(navController)
                        }

                        composable("settings", enterTransition = {
                            slideIn { IntOffset(it.width, 0) }
                        }, exitTransition = {
                            slideOut { IntOffset(it.width, 0) }
                        }) {
                            SettingsView(navController)
                        }
                    }
                }
            }
        }
    }
}
