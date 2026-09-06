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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cn.huacheng.safebaiyun.compose.HelpView
import cn.huacheng.safebaiyun.compose.MainView
import cn.huacheng.safebaiyun.compose.QRExportView
import cn.huacheng.safebaiyun.compose.QRImportView
import cn.huacheng.safebaiyun.theme.SafeBaiyunTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 删除 UnlockRepo.init(lifecycleScope) 这行，因为 UnlockRepo 内部已自动检查蓝牙

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
                    }
                }
            }
        }
    }
}
