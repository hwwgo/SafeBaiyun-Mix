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
import cn.huacheng.safebaiyun.ShortcutActivity
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

/**
 * 主界面。
 *
 * 桌面小部件 / 桌面快捷方式的开锁请求也在这里执行 —— 通过 [autoUnlockDoorId]
 * 传入要开的门禁，主界面照常显示后自动触发，动作与用户手动点击完全一致：
 *   · [ShortcutActivity.ALL_DOORS_ID] → 走「一键开锁」（等同点顶部大按钮）
 *   · 具体门禁 id                    → 走单门开锁（等同点该门禁卡片右侧「开锁」）
 * 开锁结束（成功或失败）后主界面保持显示，不自动关闭。
 *
 * @param autoUnlockDoorId     待自动执行的开锁请求；null 表示无请求
 * @param onAutoUnlockConsumed 请求已被消费（已发起开锁）时回调，供上层清空状态避免重复触发
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainView(
    navController: NavHostController,
    autoUnlockDoorId: String? = null,
    onAutoUnlockConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val hasPermission = remember { mutableStateOf(false) }
    val doors = remember { mutableStateOf<List<DoorDevice>>(DataRepo.getDoors()) }

    /** 权限检查是否已完成（首帧异步赋值，自动开锁必须等它就绪） */
    var permissionChecked by remember { mutableStateOf(false) }

    /** 各门禁的实时开锁步骤：含 key 即表示「正在开锁」，value 为步骤文案 */
    val unlockSteps = remember { mutableStateMapOf<String, String>() }

    var pollingState by remember { mutableStateOf(PollingState.IDLE) }
    var pollingProgress by remember { mutableStateOf("") }
    var pollingCurrentIndex by remember { mutableStateOf(0) }
    var pollingTotal by remember { mutableStateOf(0) }
    var pollingFlowJob by remember { mutableStateOf<Job?>(null) }

    var autoPollExecuted by remember { mutableStateOf(false) }
    var scanPermissionResult by remember { mutableStateOf<Boolean?>(null) }
    var pendingManualPoll by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        hasPermission.value = hasAllBlePermissions(context)
        permissionChecked = true
    }

    val scanPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        scanPermissionResult = result.values.all { it }
    }

    suspend fun runScanThenPolling(selectedDoors: List<DoorDevice>) {
        try {
            val startTime = System.currentTimeMillis()

            val n = selectedDoors.size
            val doProbe = ConfigManager.getAutoScanEnabled() && hasAllBlePermissions(context)
            val probeSteps = if (doProbe) n else 0
            val totalSteps = probeSteps + n

            // ========== 阶段一：探测 + 开锁（合并） ==========
            if (doProbe) {
                pollingState = PollingState.SCANNING
                pollingCurrentIndex = 0
                pollingTotal = totalSteps
                pollingProgress = "正在探测门禁 1/$n"

                val matchedDoor = UnlockRepo.probeAndUnlock(
                    doors = selectedDoors,
                    perDeviceTimeoutMs = ConfigManager.getScanDuration(),
                    onProbe = { index, _, name ->
                        withContext(Dispatchers.Main) {
                            pollingCurrentIndex = index
                            pollingTotal = totalSteps
                            pollingProgress = "正在探测门禁 $index/$n: $name"
                        }
                    },
                    onUnlock = { _, _, name ->
                        withContext(Dispatchers.Main) {
                            pollingProgress = "正在开锁: $name"
                        }
                    }
                )

                if (matchedDoor != null) {
                    val elapsed = System.currentTimeMillis() - startTime
                    pollingCurrentIndex = totalSteps
                    pollingTotal = totalSteps
                    pollingState = PollingState.IDLE
                    pollingProgress = "✅ 已开启: ${matchedDoor.name}"
                    doors.value = DataRepo.getDoors()

                    withContext(Dispatchers.Main) {
                        showToast("✅ 已成功开门！")
                    }
                    scope.launch {
                        delay(1500)
                        showToast("用时 ${elapsed}ms")
                    }
                    return
                }

                pollingCurrentIndex = probeSteps
                pollingTotal = totalSteps
            }

            // ========== 阶段二：轮询（兜底） ==========
            pollingState = PollingState.POLLING
            pollingCurrentIndex = probeSteps
            pollingTotal = totalSteps
            pollingProgress = "正在轮询门禁 1/$n"

            coroutineScope {
                val job = UnlockRepo.startPolling(
                    scope = this,
                    doors = selectedDoors,
                    onProgress = { index, _, name ->
                        withContext(Dispatchers.Main) {
                            pollingCurrentIndex = probeSteps + index
                            pollingTotal = totalSteps
                            pollingProgress = "正在轮询门禁 $index/$n: $name"
                        }
                    },
                    onComplete = { result ->
                        pollingState = PollingState.IDLE
                        if (result != null) {
                            val elapsed = System.currentTimeMillis() - startTime
                            pollingProgress = "✅ 已开启: ${result.name}"
                            scope.launch {
                                showToast("✅ 已成功开门！")
                                delay(1500)
                                showToast("用时 ${elapsed}ms")
                            }
                        } else {
                            pollingProgress = "❌ 未找到可开门禁"
                        }
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

    /**
     * 开启单个门禁。
     *
     * 门禁卡片右侧「开锁」按钮与桌面快捷方式 / 小部件走的是同一个函数，
     * 因此两条路径的行为、进度文案、结果提示完全一致。
     *
     * 进度写在 [unlockSteps] 里（由卡片内联展示），开锁结束后延迟清空回归空闲态，
     * 主界面不会消失，用户可以接着操作。
     */
    fun unlockSingleDoor(door: DoorDevice) {
        // 重新读取当前配置，避免列表状态更新后使用旧的 MAC/Key
        val currentDoor = DataRepo.getDoors().firstOrNull { it.id == door.id } ?: door
        if (currentDoor.mac.isEmpty() || currentDoor.key.isEmpty()) {
            showToast("请先配置该门禁的 MAC 和 Key")
            return
        }
        // 正在开锁中则忽略重复触发
        if (unlockSteps.containsKey(currentDoor.id)) return

        scope.launch {
            val startTime = System.currentTimeMillis()
            unlockSteps[currentDoor.id] = "准备开锁..."

            val collectJob = launch {
                UnlockRepo.unlockStep.collectLatest { step ->
                    if (step.isNotEmpty()) unlockSteps[currentDoor.id] = step
                }
            }

            val success = UnlockRepo.tryUnlock(currentDoor.mac, currentDoor.key, currentDoor.name)
            val elapsed = System.currentTimeMillis() - startTime
            delay(ConfigManager.getResultDelay())
            collectJob.cancel()

            val step = unlockSteps[currentDoor.id].orEmpty()
            if (!step.contains("成功") && !step.contains("失败") && !step.contains("超时")) {
                unlockSteps[currentDoor.id] = if (success) "✅ 开锁成功" else "❌ 开锁失败"
            }

            if (success) {
                launch {
                    showToast("✅ 已成功开门！")
                    delay(1500)
                    showToast("用时 ${elapsed}ms")
                }
            }

            delay(ConfigManager.getResetDelay())
            unlockSteps.remove(currentDoor.id)
        }
    }

    /**
     * 处理来自桌面小部件 / 桌面快捷方式的开锁请求。
     *
     * 等权限检查完成、门禁列表就绪后自动执行，等价于用户手动点击：
     *   · ALL_DOORS_ID → 一键开锁
     *   · 具体 id      → 对应门禁开锁（找不到时退化为第一个可用门禁）
     * 发起后立即消费请求，避免重复触发；开锁结束后主界面保持显示。
     */
    LaunchedEffect(autoUnlockDoorId, permissionChecked) {
        val requested = autoUnlockDoorId ?: return@LaunchedEffect
        if (!permissionChecked) return@LaunchedEffect

        if (!hasPermission.value) {
            showToast("请先授予蓝牙权限，再使用快捷开锁")
            onAutoUnlockConsumed()
            return@LaunchedEffect
        }
        if (doors.value.isEmpty()) {
            showToast("暂无门禁，请先添加门禁")
            onAutoUnlockConsumed()
            return@LaunchedEffect
        }

        // 稍等一帧，让列表先渲染出来，视觉上更接近「用户自己点了一下」
        delay(150)

        if (requested == ShortcutActivity.ALL_DOORS_ID) {
            val selected = doors.value.filter { it.isSelected }
            if (selected.isEmpty()) {
                showToast("请至少选择一个门禁")
            } else {
                startPollingFlow(selected)
            }
        } else {
            val target = doors.value.firstOrNull { it.id == requested }
                ?: doors.value.firstOrNull { it.mac.isNotEmpty() && it.key.isNotEmpty() }
            if (target == null) {
                showToast("未找到可开锁的门禁")
            } else {
                unlockSingleDoor(target)
            }
        }

        // 请求已发起，通知上层清空（此时开锁在独立协程里跑，不受本协程结束影响）
        onAutoUnlockConsumed()
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
                onHistoryClick = { navController.navigate("unlock_history") },
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

                        CompactDoorList(
                            doors = doors,
                            unlockSteps = unlockSteps,
                            onUnlock = { unlockSingleDoor(it) },
                            onRefresh = { doors.value = DataRepo.getDoors() }
                        )
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
    onHistoryClick: () -> Unit,
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
                CompactIconButton(onClick = onHistoryClick, icon = Icons.Default.History, contentDesc = "开锁记录")
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
                    // ✅ 改动一：文案简化
                    text = "一键开锁 (${selectedCount}/${doors.size})",
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
        if (state == PollingState.WAITING_BLUETOOTH) {
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
    unlockSteps: Map<String, String>,
    onUnlock: (DoorDevice) -> Unit,
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
                unlockStep = unlockSteps[door.id].orEmpty(),
                onUnlock = onUnlock,
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
    unlockStep: String,
    onUnlock: (DoorDevice) -> Unit,
    onToggleSelected: (String) -> Unit = {},
    onMoveUp: (String) -> Unit = {},
    onMoveDown: (String) -> Unit = {}
) {
    // 开锁状态由主界面统一持有（unlockSteps），卡片只负责展示，
    // 这样手动点击与桌面快捷方式 / 小部件触发的是同一份状态。
    val isUnlocking = unlockStep.isNotEmpty()

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
                                .background(unlockStepColor(unlockStep, ColorOSPrimary))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = unlockStep,
                            fontSize = 11.sp,
                            color = unlockStepColor(unlockStep, MaterialTheme.colorScheme.primary),
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
                    // 单独点击门禁右侧“开锁”时，不受 isSelected 勾选状态影响。
                    // 实际开锁统一交给主界面的 unlockSingleDoor()，
                    // 与桌面快捷方式 / 小部件触发的是同一条路径。
                    onClick = { onUnlock(door) }
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
    val buttonColor = if (isUnlocking) ColorOSPrimary else unlockStepColor(unlockStep, ColorOSPrimary)

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

/**
 * 解锁步骤文案 → 状态色（成功 / 失败或超时 / 其它用默认色）。
 *
 * 原定义在已删除的 UnlockUi.kt 中，随「轻量提示」一并清理后合并到主界面，
 * 只有门禁卡片与开锁按钮使用，因此改为文件内私有函数。
 */
private fun unlockStepColor(step: String, default: Color): Color =
    when {
        step.contains("成功") -> ColorOSSuccess
        step.contains("失败") || step.contains("超时") -> ColorOSError
        else -> default
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
