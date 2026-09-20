package cn.huacheng.safebaiyun

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import cn.huacheng.safebaiyun.theme.ColorOSError
import cn.huacheng.safebaiyun.theme.ColorOSSuccess
import cn.huacheng.safebaiyun.theme.SafeBaiyunTheme
import cn.huacheng.safebaiyun.unlock.DataRepo
import cn.huacheng.safebaiyun.unlock.UnlockRepo
import cn.huacheng.safebaiyun.util.ConfigManager
import cn.huacheng.safebaiyun.util.showToast
import cn.huacheng.safebaiyun.widget.WidgetUnlockBus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // 0 = 等待中（显示纯背景），1 = widget overlay，2 = 正常主界面
    private val uiMode = mutableStateOf(0)
    private val overlayDoorId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ConfigManager.init(this)
        UnlockRepo.init(lifecycleScope)

        // 立即检查一次（热启动时可能已有值）
        val immediateId = WidgetUnlockBus.consume()
        if (immediateId != null) {
            overlayDoorId.value = immediateId
            uiMode.value = 1
        } else {
            // 冷启动：等 150ms 再看（等 WidgetUnlockAction 执行完）
            lifecycleScope.launch {
                delay(150)
                val doorId = WidgetUnlockBus.consume()
                if (doorId != null) {
                    overlayDoorId.value = doorId
                    uiMode.value = 1
                } else {
                    uiMode.value = 2
                }
            }
        }

        setContent {
            SafeBaiyunTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val mode = uiMode.value
                    if (mode == 0) {
                        // 等待中：显示纯背景，避免闪烁
                        Box(Modifier.fillMaxSize())
                    } else if (mode == 1) {
                        // Widget 触发：显示开锁 overlay
                        WidgetUnlockOverlay(
                            doorId = overlayDoorId.value,
                            onFinish = { finish() }
                        )
                    } else {
                        // 正常打开 App：显示主界面
                        MainNavContent()
                    }
                }
            }
        }
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
        }
    }

    /**
     * 开锁 overlay：显示转圈 + 实时状态文字
     */
    @Composable
    private fun WidgetUnlockOverlay(
        doorId: String?,
        onFinish: () -> Unit
    ) {
        val context = LocalContext.current
        val unlockStep by UnlockRepo.unlockStep.collectAsState()
        val displayText = unlockStep.ifEmpty { "准备开锁..." }

        val textColor = when {
            unlockStep.contains("成功") -> ColorOSSuccess
            unlockStep.contains("失败") || unlockStep.contains("超时") -> ColorOSError
            else -> MaterialTheme.colorScheme.onSurface
        }

        LaunchedEffect(doorId) {
            delay(100)

            // 检查权限
            val hasConnect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED
            } else true

            if (hasConnect == false) {
                showToast("请先授予蓝牙权限")
                delay(2000)
                onFinish()
                return@LaunchedEffect
            }

            // 读取门禁（冷启动时序保护）
            var doors = DataRepo.getDoors()
            if (doors.isEmpty()) {
                delay(300)
                doors = DataRepo.getDoors()
            }

            val door = doorId
                ?.let { id -> doors.find { it.id == id } }
                ?.takeIf { it.mac.isNotEmpty() && it.key.isNotEmpty() }
                ?: doors.firstOrNull { it.mac.isNotEmpty() && it.key.isNotEmpty() }
                ?: doors.firstOrNull()

            if (door == null || door.mac.isEmpty() || door.key.isEmpty()) {
                showToast("未找到可开锁的门禁")
                delay(2000)
                onFinish()
                return@LaunchedEffect
            }

            showToast("正在解锁 ${door.name}")
            val success = UnlockRepo.tryUnlock(door.mac, door.key)
            if (success) {
                delay(800)
            }
            onFinish()
        }

        // 对话框样式
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x66000000)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 32.dp, vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = displayText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = textColor
                    )
                }
            }
        }
    }
}
