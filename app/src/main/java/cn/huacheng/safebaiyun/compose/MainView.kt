package cn.huacheng.safebaiyun.compose

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import cn.huacheng.safebaiyun.R
import cn.huacheng.safebaiyun.theme.*
import cn.huacheng.safebaiyun.unlock.DataRepo
import cn.huacheng.safebaiyun.unlock.DoorDevice
import cn.huacheng.safebaiyun.unlock.UnlockRepo
import cn.huacheng.safebaiyun.util.ConfigManager
import cn.huacheng.safebaiyun.util.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainView(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val hasPermission = remember { mutableStateOf(false) }
    val doors = remember { mutableStateOf<List<DoorDevice>>(DataRepo.getDoors()) }

    var isPolling by remember { mutableStateOf(false) }
    var pollingProgress by remember { mutableStateOf("") }
    var pollingCurrentIndex by remember { mutableStateOf(0) }
    var pollingTotal by remember { mutableStateOf(0) }

    var autoPollExecuted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission.value = context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            hasPermission.value = true
        }
    }

    LaunchedEffect(Unit) {
        if (hasPermission.value && doors.value.isNotEmpty() && !autoPollExecuted) {
            val autoPoll = ConfigManager.getAutoPollOnStart()
            if (autoPoll) {
                val selectedDoors = doors.value.filter { it.isSelected }
                if (selectedDoors.isNotEmpty()) {
                    autoPollExecuted = true
                    val waitTime = ConfigManager.getPollWaitTime()
                    val bluetoothReady = UnlockRepo.waitForBluetooth(waitTime)
                    if (!bluetoothReady) {
                        showToast("蓝牙未开启，自动轮询已跳过")
                        return@LaunchedEffect
                    }
                    delay(500)
                    isPolling = true
                    pollingCurrentIndex = 0
                    pollingTotal = selectedDoors.size
                    pollingProgress = "自动轮询中..."

                    UnlockRepo.startPolling(
                        scope = scope,
                        doors = selectedDoors,
                        onProgress = { index, total, name ->
                            withContext(Dispatchers.Main) {
                                pollingCurrentIndex = index
                                pollingTotal = total
                                pollingProgress = "正在尝试 $index/$total: $name"
                            }
                        },
                        onComplete = { result ->
                            isPolling = false
                            pollingProgress = if (result != null) "✅ 已开启: ${result.name}" else "❌ 未找到可开门禁"
                            doors.value = DataRepo.getDoors()
                        }
                    )
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )
            )
    ) {
        Column {
            CleanTopBar(
                onEditClick = { navController.navigate("manage_doors") },
                onHelperClick = { navController.navigate("helper") },
                onSettingsClick = { navController.navigate("settings") }
            )

            Box(modifier = Modifier.weight(1f).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                if (hasPermission.value) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        val selectedCount = doors.value.count { it.isSelected }

                        CleanPollButton(
                            doors = doors.value,
                            selectedCount = selectedCount,
                            isPolling = isPolling,
                            pollingProgress = pollingProgress,
                            onPollStart = {
                                val selectedDoors = doors.value.filter { it.isSelected }
                                if (selectedDoors.isEmpty()) {
                                    showToast("请至少选择一个门禁")
                                    return@CleanPollButton
                                }
                                isPolling = true
                                pollingCurrentIndex = 0
                                pollingTotal = selectedDoors.size
                                pollingProgress = "准备轮询..."

                                UnlockRepo.startPolling(
                                    scope = scope,
                                    doors = selectedDoors,
                                    onProgress = { index, total, name ->
                                        withContext(Dispatchers.Main) {
                                            pollingCurrentIndex = index
                                            pollingTotal = total
                                            pollingProgress = "正在尝试 $index/$total: $name"
                                        }
                                    },
                                    onComplete = { result ->
                                        isPolling = false
                                        pollingProgress = if (result != null) "✅ 已开启: ${result.name}" else "❌ 未找到可开门禁"
                                        doors.value = DataRepo.getDoors()
                                    }
                                )
                            }
                        )

                        if (isPolling && pollingTotal > 0) {
                            CleanProgressBar(
                                current = pollingCurrentIndex,
                                total = pollingTotal,
                                onStop = {
                                    UnlockRepo.stopPolling()
                                    isPolling = false
                                    pollingProgress = "⏹ 已停止轮询"
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        DoorListContent(doors = doors, onRefresh = { doors.value = DataRepo.getDoors() })
                    }
                } else {
                    PermissionView(hasPermission)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CleanOutlineButton(
                    onClick = { navController.navigate("qr_export") },
                    icon = Icons.Default.Share,
                    text = "导出配置",
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                CleanOutlineButton(
                    onClick = { navController.navigate("qr_import") },
                    icon = Icons.Default.Add,
                    text = "扫描导入",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CleanTopBar(
    onEditClick: () -> Unit,
    onHelperClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 修复：移除阴影，使用简单渐变
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(ColorOSGradientStart, ColorOSGradientEnd)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "智能门禁",
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        },
        actions = {
            // 修复：移除背景，避免阴影重叠
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                CleanIconButton(onClick = onEditClick, icon = Icons.Default.Add, contentDesc = "添加门禁")
                CleanIconButton(onClick = onSettingsClick, icon = Icons.Default.Settings, contentDesc = "设置")
                CleanIconButton(onClick = onHelperClick, icon = Icons.Default.Info, contentDesc = "帮助")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        )
    )
}

@Composable
private fun CleanIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDesc: String
) {
    // 修复：移除背景色块，只保留图标
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp)
    ) {
        Icon(
            icon,
            contentDescription = contentDesc,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun CleanPollButton(
    doors: List<DoorDevice>,
    selectedCount: Int,
    isPolling: Boolean,
    pollingProgress: String,
    onPollStart: () -> Unit
) {
    val hasDoors = doors.isNotEmpty()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                brush = if (isPolling) {
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                } else {
                    Brush.horizontalGradient(
                        colors = listOf(ColorOSGradientStart, ColorOSGradientEnd)
                    )
                }
            )
            .clickable(enabled = !isPolling && hasDoors) { onPollStart() },
        contentAlignment = Alignment.Center
    ) {
        if (isPolling) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = pollingProgress,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "一键轮询开锁 (${selectedCount}/${doors.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun CleanProgressBar(
    current: Int,
    total: Int,
    onStop: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LinearProgressIndicator(
            progress = current.toFloat() / total,
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = ColorOSPrimary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        // 修复：移除背景色块
        IconButton(
            onClick = onStop,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Default.Stop,
                contentDescription = "停止轮询",
                tint = ColorOSError,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun CleanOutlineButton(
    onClick: () -> Unit,
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DoorListContent(
    doors: MutableState<List<DoorDevice>>,
    onRefresh: () -> Unit,
) {
    if (doors.value.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "暂无门禁，请点击右上角添加",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 16.sp
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(doors.value, key = { it.id }) { door ->
            CleanDoorCard(
                door = door,
                onToggleSelected = { id ->
                    DataRepo.toggleSelected(id)
                    doors.value = DataRepo.getDoors()
                },
                onMoveUp = { id ->
                    DataRepo.moveDoor(id, -1)
                    doors.value = DataRepo.getDoors()
                },
                onMoveDown = { id ->
                    DataRepo.moveDoor(id, 1)
                    doors.value = DataRepo.getDoors()
                }
            )
        }
        item { Spacer(modifier = Modifier.size(80.dp)) }
    }
}

@Composable
private fun CleanDoorCard(
    door: DoorDevice,
    onToggleSelected: (String) -> Unit = {},
    onMoveUp: (String) -> Unit = {},
    onMoveDown: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var isUnlocking by remember { mutableStateOf(false) }
    var unlockStep by remember { mutableStateOf("") }
    val stepState = UnlockRepo.unlockStep
    val interactionSource = remember { MutableInteractionSource() }

    val nameFontSize = if (door.name.length > 15) 15.sp else 17.sp

    // 修复：移除阴影，使用简单的边框区分选中状态
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (door.isSelected)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        // 修复：使用边框而不是阴影
        border = if (door.isSelected) {
            androidx.compose.foundation.BorderStroke(
                1.dp,
                ColorOSPrimary.copy(alpha = 0.3f)
            )
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = door.isSelected,
                onCheckedChange = { onToggleSelected(door.id) },
                modifier = Modifier.size(24.dp),
                colors = CheckboxDefaults.colors(
                    checkedColor = ColorOSPrimary,
                    uncheckedColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = door.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = nameFontSize,
                    fontWeight = FontWeight.Bold,
                    color = if (door.isSelected)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (door.mac.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = door.mac,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                if (isUnlocking && unlockStep.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    when {
                                        unlockStep.contains("成功") -> ColorOSSuccess
                                        unlockStep.contains("失败") || unlockStep.contains("超时") -> ColorOSError
                                        else -> ColorOSPrimary
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = unlockStep,
                            fontSize = 12.sp,
                            color = when {
                                unlockStep.contains("成功") -> ColorOSSuccess
                                unlockStep.contains("失败") || unlockStep.contains("超时") -> ColorOSError
                                else -> MaterialTheme.colorScheme.primary
                            },
                            fontWeight = if (unlockStep.contains("成功") || unlockStep.contains("失败"))
                                FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Row {
                    // 修复：移除背景色块
                    CleanSmallIconButton(
                        onClick = { onMoveUp(door.id) },
                        icon = Icons.Default.KeyboardArrowUp,
                        contentDesc = "上移"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    CleanSmallIconButton(
                        onClick = { onMoveDown(door.id) },
                        icon = Icons.Default.KeyboardArrowDown,
                        contentDesc = "下移"
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                CleanUnlockButton(
                    isUnlocking = isUnlocking,
                    unlockStep = unlockStep,
                    onClick = {
                        if (door.mac.isEmpty() || door.key.isEmpty()) {
                            showToast("请先配置该门禁的 MAC 和 Key")
                            return@CleanUnlockButton
                        }
                        if (!isUnlocking) {
                            isUnlocking = true
                            unlockStep = "准备开锁..."
                            scope.launch {
                                val job = launch {
                                    stepState.collectLatest { step ->
                                        if (step.isNotEmpty()) unlockStep = step
                                    }
                                }
                                val success = UnlockRepo.tryUnlock(door.mac, door.key)
                                delay(ConfigManager.getResultDelay())
                                job.cancel()
                                if (!unlockStep.contains("成功") && !unlockStep.contains("失败") && !unlockStep.contains("超时")) {
                                    unlockStep = if (success) "✅ 开锁成功" else "❌ 开锁失败"
                                }
                                delay(ConfigManager.getResetDelay())
                                isUnlocking = false
                                unlockStep = ""
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun CleanSmallIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDesc: String
) {
    // 修复：移除背景色块，只保留图标
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(32.dp)
    ) {
        Icon(
            icon,
            contentDescription = contentDesc,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CleanUnlockButton(
    isUnlocking: Boolean,
    unlockStep: String,
    onClick: () -> Unit
) {
    val buttonColor = when {
        isUnlocking -> ColorOSPrimary
        unlockStep.contains("成功") -> ColorOSSuccess
        unlockStep.contains("失败") || unlockStep.contains("超时") -> ColorOSError
        else -> ColorOSPrimary
    }

    // 修复：移除阴影
    Box(
        modifier = Modifier
            .width(88.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(buttonColor, buttonColor.copy(alpha = 0.8f))
                )
            )
            .clickable(enabled = !isUnlocking) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        when {
            isUnlocking -> CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
            unlockStep.contains("成功") -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("成功", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            unlockStep.contains("失败") || unlockStep.contains("超时") -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("失败", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("开锁", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun PermissionView(hasPermission: MutableState<Boolean>) {
    val requestPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            hasPermission.value = isGranted
        }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Bluetooth,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "需要蓝牙权限才能开门",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                }
            },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ColorOSPrimary
            )
        ) {
            Text("授予权限", fontSize = 16.sp)
        }
    }
}
