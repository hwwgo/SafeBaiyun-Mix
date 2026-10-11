package cn.huacheng.safebaiyun.compose

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.huacheng.safebaiyun.ShortcutActivity
import cn.huacheng.safebaiyun.theme.ColorOSError
import cn.huacheng.safebaiyun.theme.ColorOSScrim
import cn.huacheng.safebaiyun.theme.ColorOSSuccess
import cn.huacheng.safebaiyun.unlock.DataRepo
import cn.huacheng.safebaiyun.unlock.DoorDevice
import cn.huacheng.safebaiyun.unlock.UnlockRepo
import cn.huacheng.safebaiyun.util.ConfigManager
import cn.huacheng.safebaiyun.util.showToast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * 解锁步骤文案 → 状态色。
 *
 * UnlockLightweight 与 MainView 都按“成功 / 失败或超时 / 其它”给文案上色，
 * 抽成共享函数避免三处重复的 when 判断。
 *
 * @param step    当前解锁步骤文案（来自 UnlockRepo.unlockStep）
 * @param default 非终态时的默认色
 */
fun unlockStepColor(step: String, default: Color): Color =
    when {
        step.contains("成功") -> ColorOSSuccess
        step.contains("失败") || step.contains("超时") -> ColorOSError
        else -> default
    }

/**
 * 解锁核心流程（与 UI 解耦的挂起函数）。
 *
 * 合并自 MainActivity.WidgetUnlockOverlay 与 ShortcutActivity.unlock / unlockAllDoors：
 * 冷启动时序保护 → 门禁解析 → 一键开锁（探测 + 轮询兜底）/ 单门开锁。
 */
private suspend fun performUnlock(doorId: String?, onFinish: () -> Unit) {
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
            return
        }

        showToast("正在一键开锁 ${selected.size} 个门禁")

        val doProbe = ConfigManager.getAutoScanEnabled()
        var matched: DoorDevice? = null
        try {
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
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("UnlockOverlay", "一键开锁异常", e)
        }

        delay(800)
        onFinish()
        return
    }

    // ====== 单个门禁开锁 ======
    val door = doorId
        ?.let { id -> doors.find { it.id == id } }
        ?.takeIf { it.mac.isNotEmpty() && it.key.isNotEmpty() }
        ?: doors.firstOrNull { it.mac.isNotEmpty() && it.key.isNotEmpty() }
        ?: doors.firstOrNull()

    if (door == null || door.mac.isEmpty() || door.key.isEmpty()) {
        showToast("未找到可开锁的门禁")
        delay(2000)
        onFinish()
        return
    }

    showToast("正在解锁 ${door.name}")
    val success = UnlockRepo.tryUnlock(door.mac, door.key, door.name)
    if (success) {
        delay(800)
    }
    onFinish()
}

/**
 * 轻量解锁提示（桌面小部件与桌面快捷方式共用，保证两者提示界面一致）。
 *
 * 外层 Box 为半透明遮罩（Color(0x66000000)），桌面小部件与快捷方式共用，
 * 保证两者背景一致：既不是纯白也不是纯黑，中央仅显示紧凑小卡片
 * （圆形进度 + 文案）。解锁完成（onFinish）后自动消失。
 *
 * @param doorId            目标门禁 ID；null / "" 表示开第一个门；ShortcutActivity.ALL_DOORS_ID 表示一键开锁
 * @param onFinish          解锁结束（成功 / 失败 / 无门禁）后的回调
 * @param requirePermission 是否需要在解锁前检查蓝牙权限（未授权则提示并结束）。
 *        桌面小部件走 MainActivity 冷启动路径、自身不弹权限框，传 true；
 *        快捷方式已在外部通过权限请求流程保证授权，传 false。
 */
@Composable
fun UnlockLightweight(
    doorId: String?,
    onFinish: () -> Unit,
    requirePermission: Boolean = false
) {
    val context = LocalContext.current
    val unlockStep by UnlockRepo.unlockStep.collectAsState()
    val displayText = unlockStep.ifEmpty { "准备开锁..." }
    val textColor = unlockStepColor(unlockStep, MaterialTheme.colorScheme.onSurface)

    LaunchedEffect(doorId) {
        delay(100)

        if (requirePermission) {
            val hasConnect = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            if (!hasConnect) {
                showToast("请先授予蓝牙权限")
                delay(2000)
                onFinish()
                return@LaunchedEffect
            }
        }

        performUnlock(doorId, onFinish)
    }

    // 外层 Box：半透明遮罩（ColorOSScrim，约 30% 黑），桌面快捷方式与小部件共用，
    // 保证两者背景一致：既不是纯白也不是纯黑，桌面/壁纸可透过约 70%，中央仅显示紧凑小卡片。
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorOSScrim),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 22.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.5.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
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
