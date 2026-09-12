package cn.huacheng.safebaiyun.compose

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import cn.huacheng.safebaiyun.theme.*
import cn.huacheng.safebaiyun.unlock.DataRepo
import cn.huacheng.safebaiyun.unlock.DoorDevice
import cn.huacheng.safebaiyun.unlock.UnlockRepo
import cn.huacheng.safebaiyun.util.ConfigManager
import cn.huacheng.safebaiyun.util.showToast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

private enum class PollingState {
    IDLE,
    WAITING_BLUETOOTH,
    SCANNING,
    POLLING
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainView(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val hasPermission = remember { mutableStateOf(false) }
    val doors = remember { mutableStateOf<List<DoorDevice>>(DataRepo.getDoors()) }

    var pollingState by remember { mutableStateOf(PollingState.IDLE) }
    var pollingProgress by remember { mutableStateOf("") }
    var pollingCurrentIndex by remember { mutableStateOf(0) }
    var pollingTotal by remember { mutableStateOf(0) }
    var pollingFlowJob by remember { mutableStateOf<Job?>(null) }

    var autoPollExecuted by remember { mutableStateOf(false) }
    var scanPermissionResult by remember { mutableStateOf<Boolean?>(null) }
    var pendingManualPoll by remember { mutableStateOf(false) }

    // 统一检查 CONNECT + SCAN 权限
    LaunchedEffect(Unit) {
        hasPermission.value = hasAllBlePermissions(context)
    }

    val scanPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        scanPermissionResult = result.values.all { it }
    }

    suspend fun runScanThenPolling(selectedDoors: List<DoorDevice>) {
        try {
            var pollDoors = selectedDoors

            // ✅ 只有开关打开且权限齐全时才探测；否则跳过，直接进入轮询
            if (ConfigManager.getAutoScanEnabled() && hasAllBlePermissions(context)) {
                pollingState = PollingState.SCANNING
                pollingCurrentIndex = 0
                pollingTotal = 0
                pollingProgress = "正在探测附近门禁..."

                // ✅ 参数名改为 perDeviceTimeoutMs，语义为"单个门禁探测时长"
                val matchedDoor = UnlockRepo.findNearbyConfiguredDoor(
                    doors = selectedDoors,
                    perDeviceTimeoutMs = ConfigManager.getScanDuration()
                )

                if (matchedDoor != null) {
                    // 命中后把该门禁放到第一位
                    pollDoors = listOf(matchedDoor) + selectedDoors.filter { it.id != matchedDoor.id }
                }
            }

            pollingState = PollingState.POLLING
            pollingCurrentIndex = 0
            pollingTotal = pollDoors.size
            pollingProgress = "正在轮询门禁 1/${pollDoors.size}"

            coroutineScope {
                val job = UnlockRepo.startPolling(
                    scope = this,
                    doors = pollDoors,
                    onProgress = { index, total, name ->
                        withContext(Dispatchers.Main) {
                            pollingCurrentIndex = index
                            pollingTotal = total
                            pollingProgress = "正在轮询门禁 $index/$total: $name"
                        }
                    },
                    onComplete = { result ->
                        pollingState = PollingState.IDLE
                        pollingProgress =
                            if (result != null) "✅ 已开启: ${result.name}"
                            else "❌ 未找到可开门禁"
                        doors.value = DataRepo.getDoors()
                    }
                )
                job.join()
            }
        } catch (e: CancellationException) {
            pollingState = PollingState.IDLE
            pollingProgress = "⏹ 已停止轮询"
            throw e
        }
    }

    fun startPollingFlow(selectedDoors: List<DoorDevice>) {
        pollingFlowJob?.cancel()
        pollingFlowJob = scope.launch {
            try {
                runScanThenPolling(selectedDoors)
            } finally {
                pollingFlowJob = null
            }
        }
    }

    LaunchedEffect(scanPermissionResult, pendingManualPoll) {
        if (!pendingManualPoll) return@LaunchedEffect

        when (scanPermissionResult) {
            true -> {
                pendingManualPoll = false
                val selectedDoors = doors.value.filter { it.isSelected }
                if (selectedDoors.isNotEmpty()) {
                    startPollingFlow(selectedDoors)
                }
            }

            false -> {
                pendingManualPoll = false
                showToast("未授予蓝牙扫描权限，无法扫描门禁")
            }

            null -> Unit
        }
    }

    LaunchedEffect(hasPermission.value, doors.value, scanPermissionResult) {
        if (!hasPermission.value || doors.value.isEmpty() || autoPollExecuted) {
            return@LaunchedEffect
        }

        if (!ConfigManager.getAutoPollOnStart()) {
            return@LaunchedEffect
        }

        val selectedDoors = doors.value.filter { it.isSelected }
        if (selectedDoors.isEmpty()) {
            return@LaunchedEffect
        }

        pollingFlowJob?.cancel()
        pollingFlowJob = scope.launch {
            try {
                pollingState = PollingState.WAITING_BLUETOOTH
                pollingCurrentIndex = 0
                pollingTotal = 0
                pollingProgress = "等待蓝牙开启..."

                val waitTime = ConfigManager.getPollWaitTime()
                val bluetoothReady = waitForBluetoothWithin(waitTime)

                if (!bluetoothReady) {
                    pollingState = PollingState.IDLE
                    pollingProgress = "蓝牙未开启，自动轮询已跳过"
                    autoPollExecuted = true
                    showToast("蓝牙未开启，自动轮询已跳过")
                    return@launch
                }

                if (!hasAllBlePermissions(context)) {
                    if (scanPermissionResult == null) {
                        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            arrayOf(
                                Manifest.permission.BLUETOOTH_CONNECT,
                                Manifest.permission.BLUETOOTH_SCAN
                            )
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            arrayOf(
                                Manifest.permission.BLUETOOTH,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            )
                        } else {
                            emptyArray()
                        }

                        if (permissions.isNotEmpty()) {
                            pollingProgress = "需要蓝牙权限..."
                            scanPermissionLauncher.launch(permissions)
                            return@launch
                        }
                    }

                    pollingState = PollingState.IDLE
                    pollingProgress = "未授予蓝牙权限，自动轮询已跳过"
                    autoPollExecuted = true
                    showToast("未授予蓝牙权限，自动轮询已跳过")
                    return@launch
                }

                autoPollExecuted = true
                delay(100)
                runScanThenPolling(selectedDoors)
            } finally {
                pollingFlowJob = null
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
            CompactTopBar(
                onEditClick = { navController.navigate("manage_doors") },
                onHelperClick = { navController.navigate("helper") },
                onSettingsClick = { navController.navigate("settings") }
            )

            Box(modifier = Modifier.weight(1f).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                if (hasPermission.value) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        val selectedCount = doors.value.count { it.isSelected }

                        CompactPollButton(
                            doors = doors.value,
                            selectedCount = selectedCount,
                            pollingState = pollingState,
                            pollingProgress = pollingProgress,
                            onPollStart = {
                                val selectedDoors = doors.value.filter { it.isSelected }
                                if (selectedDoors.isEmpty()) {
                                    showToast("请至少选择一个门禁")
                                    return@CompactPollButton
                                }

                                if (!hasAllBlePermissions(context)) {
                                    pendingManualPoll = true
                                    scanPermissionResult = null
                                    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        arrayOf(
                                            Manifest.permission.BLUETOOTH_CONNECT,
                                            Manifest.permission.BLUETOOTH_SCAN
                                        )
                                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        arrayOf(
                                            Manifest.permission.BLUETOOTH,
                                            Manifest.permission.ACCESS_FINE_LOCATION
                                        )
                                    } else {
                                        emptyArray()
                                    }

                                    if (permissions.isNotEmpty()) {
                                        scanPermissionLauncher.launch(permissions)
                                    } else {
                                        pendingManualPoll = false
                                        startPollingFlow(selectedDoors)
                                    }
                                } else {
                                    startPollingFlow(selectedDoors)
                                }
                            }
                        )

                        if (pollingState != PollingState.IDLE) {
                            CompactProgressBar(
                                state = pollingState,
                                current = pollingCurrentIndex,
                                total = pollingTotal,
                                onStop = {
                                    pollingFlowJob?.cancel()
                                    pollingFlowJob = null
                                    UnlockRepo.stopPolling()
                                    pollingState = PollingState.IDLE
                                    pollingProgress = "⏹ 已停止轮询"
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        CompactDoorList(doors = doors, onRefresh = { doors.value = DataRepo.getDoors() })
                    }
                } else {
                    PermissionView(hasPermission)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CompactOutlineButton(
                    onClick = { navController.navigate("qr_export") },
                    icon = Icons.Default.Share,
                    text = "导出配置",
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                CompactOutlineButton(
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
private fun CompactTopBar(
    onEditClick: () -> Unit,
    onHelperClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
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
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "智能门禁",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        },
        actions = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                CompactIconButton(onClick = onEditClick, icon = Icons.Default.Add, contentDesc = "添加门禁")
                CompactIconButton(onClick = onSettingsClick, icon = Icons.Default.Settings, contentDesc = "设置")
                CompactIconButton(onClick = onHelperClick, icon = Icons.Default.Info, contentDesc = "帮助")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        )
    )
}

@Composable
private fun CompactIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDesc: String
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp)
    ) {
        Icon(
            icon,
            contentDescription = contentDesc,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun CompactPollButton(
    doors: List<DoorDevice>,
    selectedCount: Int,
    pollingState: PollingState,
    pollingProgress: String,
    onPollStart: () -> Unit
) {
    val hasDoors = doors.isNotEmpty()
    val isBusy = pollingState != PollingState.IDLE

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                brush = if (isBusy) {
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
            .clickable(enabled = !isBusy && hasDoors) { onPollStart() },
        contentAlignment = Alignment.Center
    ) {
        if (isBusy) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = pollingProgress,
                    fontSize = 13.sp,
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
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "一键轮询开锁 (${selectedCount}/${doors.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun CompactProgressBar(
    state: PollingState,
    current: Int,
    total: Int,
    onStop: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state == PollingState.WAITING_BLUETOOTH || state == PollingState.SCANNING) {
            LinearProgressIndicator(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = ColorOSPrimary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        } else {
            LinearProgressIndicator(
                progress = if (total > 0) {
                    (current.toFloat() / total).coerceIn(0f, 1f)
                } else {
                    0f
                },
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = ColorOSPrimary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = onStop,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.Stop,
                contentDescription = "停止轮询",
                tint = ColorOSError,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun CompactOutlineButton(
    onClick: () -> Unit,
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CompactDoorList(
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
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "暂无门禁，请点击右上角添加",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(doors.value, key = { it.id }) { door ->
            CompactDoorCard(
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
        item { Spacer(modifier = Modifier.size(60.dp)) }
    }
}

@Composable
private fun CompactDoorCard(
    door: DoorDevice,
    onToggleSelected: (String) -> Unit = {},
    onMoveUp: (String) -> Unit = {},
    onMoveDown: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var isUnlocking by remember { mutableStateOf(false) }
    var unlockStep by remember { mutableStateOf("") }
    val stepState = UnlockRepo.unlockStep

    val nameFontSize = if (door.name.length > 12) 14.sp else 15.sp

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (door.isSelected)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
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
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = door.isSelected,
                onCheckedChange = { onToggleSelected(door.id) },
                modifier = Modifier.size(20.dp),
                colors = CheckboxDefaults.colors(
                    checkedColor = ColorOSPrimary,
                    uncheckedColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = door.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontSize = nameFontSize,
                    fontWeight = FontWeight.Bold,
                    color = if (door.isSelected)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (door.mac.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = door.mac,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                if (isUnlocking && unlockStep.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    when {
                                        unlockStep.contains("成功") -> ColorOSSuccess
                                        unlockStep.contains("失败") || unlockStep.contains("超时") -> ColorOSError
                                        else -> ColorOSPrimary
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = unlockStep,
                            fontSize = 11.sp,
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

            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactSmallIconButton(
                    onClick = { onMoveUp(door.id) },
                    icon = Icons.Default.KeyboardArrowUp,
                    contentDesc = "上移"
                )
                CompactSmallIconButton(
                    onClick = { onMoveDown(door.id) },
                    icon = Icons.Default.KeyboardArrowDown,
                    contentDesc = "下移"
                )

                Spacer(modifier = Modifier.width(4.dp))

                CompactUnlockButton(
                    isUnlocking = isUnlocking,
                    unlockStep = unlockStep,
                    onClick = {
                        if (door.mac.isEmpty() || door.key.isEmpty()) {
                            showToast("请先配置该门禁的 MAC 和 Key")
                            return@CompactUnlockButton
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
private fun CompactSmallIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDesc: String
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(28.dp)
    ) {
        Icon(
            icon,
            contentDescription = contentDesc,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CompactUnlockButton(
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

    Box(
        modifier = Modifier
            .width(72.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
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
                modifier = Modifier.size(14.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
            unlockStep.contains("成功") -> Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            unlockStep.contains("失败") || unlockStep.contains("超时") -> Icon(
                Icons.Default.Close,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            else -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    "开锁",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

private suspend fun waitForBluetoothWithin(timeoutMs: Long): Boolean {
    val timeout = timeoutMs.coerceAtLeast(0L)
    val start = System.currentTimeMillis()

    while (true) {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter != null && adapter.isEnabled) {
            return true
        }

        if (System.currentTimeMillis() - start >= timeout) {
            return false
        }

        delay(200)
    }
}

/**
 * 统一权限检查：Android 12+ 需要 BLUETOOTH_CONNECT + BLUETOOTH_SCAN；
 * Android 6~11 需要 BLUETOOTH + ACCESS_FINE_LOCATION。
 */
private fun hasAllBlePermissions(context: android.content.Context): Boolean {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
            context.checkSelfPermission(Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        else -> true
    }
}

@Composable
private fun PermissionView(hasPermission: MutableState<Boolean>) {
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermission.value = result.values.all { it }
        if (!hasPermission.value) {
            showToast("权限未全部授予，请到系统设置中手动开启")
        }
    }

    val permissions = remember {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            else -> emptyArray()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Bluetooth,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "需要蓝牙权限才能开门",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                if (permissions.isNotEmpty()) {
                    requestPermissionLauncher.launch(permissions)
                } else {
                    hasPermission.value = true
                }
            },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ColorOSPrimary
            )
        ) {
            Text("授予权限", fontSize = 14.sp)
        }
    }
}
