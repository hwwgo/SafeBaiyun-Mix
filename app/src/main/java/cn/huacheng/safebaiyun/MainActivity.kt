package cn.huacheng.safebaiyun

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
import cn.huacheng.safebaiyun.compose.QRExportView
import cn.huacheng.safebaiyun.compose.QRImportView
import cn.huacheng.safebaiyun.compose.SettingsView
import cn.huacheng.safebaiyun.theme.SafeBaiyunTheme
import cn.huacheng.safebaiyun.unlock.UnlockRepo
import cn.huacheng.safebaiyun.util.ConfigManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化 ConfigManager
        ConfigManager.init(this)

        // 初始化 UnlockRepo（传入 lifecycleScope）
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
                            slideIn {
                                IntOffset(-it.width, 0)
                            }
                        }, exitTransition = {
                            slideOut {
                                IntOffset(-it.width, 0)
                            }
                        }) {
                            MainView(navController)
                        }

                        composable("helper", enterTransition = {
                            slideIn {
                                IntOffset(it.width, 0)
                            }
                        }, exitTransition = {
                            slideOut {
                                IntOffset(it.width, 0)
                            }
                        }) {
                            HelpView(navController)
                        }

                        composable("qr_export", enterTransition = {
                            slideIn {
                                IntOffset(-it.width, 0)
                            }
                        }, exitTransition = {
                            slideOut {
                                IntOffset(-it.width, 0)
                            }
                        }) {
                            QRExportView(navController)
                        }

                        composable("qr_import", enterTransition = {
                            slideIn {
                                IntOffset(-it.width, 0)
                            }
                        }, exitTransition = {
                            slideOut {
                                IntOffset(-it.width, 0)
                            }
                        }) {
                            QRImportView(navController)
                        }

                        // 新增设置页面路由
                        composable("settings", enterTransition = {
                            slideIn {
                                IntOffset(it.width, 0)
                            }
                        }, exitTransition = {
                            slideOut {
                                IntOffset(it.width, 0)
                            }
                        }) {
                            SettingsView(navController)
                        }
                    }
                }
            }
        }
    }
}
