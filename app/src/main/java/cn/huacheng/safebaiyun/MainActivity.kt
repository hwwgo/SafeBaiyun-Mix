package cn.huacheng.safebaiyun

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import cn.huacheng.safebaiyun.compose.UnlockHistoryView
import cn.huacheng.safebaiyun.ShortcutActivity
import cn.huacheng.safebaiyun.theme.ColorOSError
import cn.huacheng.safebaiyun.theme.ColorOSSuccess
import cn.huacheng.safebaiyun.theme.SafeBaiyunTheme
import cn.huacheng.safebaiyun.unlock.DataRepo
import cn.huacheng.safebaiyun.unlock.DoorDevice
import cn.huacheng.safebaiyun.unlock.UnlockRepo
import cn.huacheng.safebaiyun.unlock.UnlockRecordManager
import cn.huacheng.safebaiyun.util.ConfigManager
import cn.huacheng.safebaiyun.util.showToast
import cn.huacheng.safebaiyun.widget.WidgetUnlockBus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // 0 = 等待中（显示纯背景），1 = widget overlay，2 = 正常主界面
    private val uiMode = mutableStateOf(0)
    private val overlayDoorId = mutableStateOf<String?>(null)

    // 覆盖层关闭时是否直接 finish：
    // - 冷启动（widget 拉起、此前不在前台）→ true，结束回到桌面
    // - App 已在运行，widget 通过 onNewIntent 触发 → false，仅切回正常界面
    private var finishOverlayOnClose = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            SafeBaiyunTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val mode = uiMode.value
                    if (mode == 0) {
                        Box(Modifier.fillMaxSize())
                    } else if (mode == 1) {
                        WidgetUnlockOverlay(
                            doorId = overlayDoorId.value,
                            onFinish = {
                                if (finishOverlayOnClose) {
                                    finish()
                                } else {
                                    uiMode.value = 2
                                }
                            }
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

    @Composable
    private fun WidgetUnlockOverlay(
        doorId: String?,
        onFinish: () -> Unit
    ) {
        val context = LocalContext.current
        val unlockStep by UnlockRepo.unlockStep.collectAsState()
        val displayText = unlockStep.ifEmpty { "准备开锁..." }

        // 状态文字颜色：最保守的 if / else if / else 写法
        val textColor = if (unlockStep.contains("成功")) {
            ColorOSSuccess
        } else if (unlockStep.contains("失败") || unlockStep.contains("超时")) {
            ColorOSError
        } else {
            MaterialTheme.colorScheme.onSurface
        }

        LaunchedEffect(doorId) {
            delay(100)

            // 权限检查：用 || 短路形式，避免 if / else 的括号问题
            val hasConnect = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

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

            // ====== 一键开锁（探测 + 轮询兜底） ======
            if (doorId == ShortcutActivity.ALL_DOORS_ID) {
                val selected = doors.filter { it.isSelected }.ifEmpty { doors }
                if (selected.isEmpty()) {
                    showToast("未找到可开锁的门禁")
                    delay(2000)
                    onFinish()
                    return@LaunchedEffect
                }

                showToast("正在一键开锁 ${selected.size} 个门禁")

                val doProbe = ConfigManager.getAutoScanEnabled()
                var matched: DoorDevice? = null
                if (doProbe) {
                    UnlockRepo.setUnlockStep("正在探测门禁 1/${selected.size}")
                    matched = UnlockRepo.probeAndUnlock(
                        doors = selected,
                        perDeviceTimeoutMs = ConfigManager.getScanDuration(),
                        onProbe = { index, total, name ->
                            UnlockRepo.setUnlockStep("正在探测门禁 $index/$total: $name")
                        },
                        onUnlock = { _, _, name ->
                            UnlockRepo.setUnlockStep("正在开锁: $name")
                        }
                    )
                }

                if (matched == null) {
                    UnlockRepo.setUnlockStep("正在轮询门禁 1/${selected.size}")
                    UnlockRepo.pollAllDoors(
                        doors = selected,
                        onProgress = { index, total, name ->
                            UnlockRepo.setUnlockStep("正在轮询门禁 $index/$total: $name")
                        }
                    )
                }

                delay(800)
                onFinish()
                return@LaunchedEffect
            }

            // ====== 单个门禁开锁 ======
            val door = doorId
                ?.let { id -> doors.find { it.id == id } }
                ?.takeIf { it.mac.isNotEmpty() && it.key.isNotEmpty() }
                ?: doors.firstOrNull { it.mac.isNotEmpty() && it.key.isNotEmpty() }
                ?: doors.firstOrNull()

            if (door == null) {
                showToast("未找到可开锁的门禁")
                delay(2000)
                onFinish()
                return@LaunchedEffect
            }

            if (door.mac.isEmpty() || door.key.isEmpty()) {
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

    companion object {
        /** 桌面小部件通过该 extra 传递要开的门禁 ID；
         *  取值为具体门禁 ID、"__ALL_DOORS__"（一键开锁）或 ""（开第一个门） */
        const val EXTRA_WIDGET_DOOR_ID = "widget_door_id"
    }
}
