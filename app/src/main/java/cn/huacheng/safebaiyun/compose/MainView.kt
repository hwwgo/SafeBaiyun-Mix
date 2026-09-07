package cn.huacheng.safebaiyun.compose

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AppSettingsAlt
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import cn.huacheng.safebaiyun.R
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
    val showManageDialog = remember { mutableStateOf(false) }
    val doors = remember { mutableStateOf<List<DoorDevice>>(DataRepo.getDoors()) }

    var isPolling by remember { mutableStateOf(false) }
    var pollingProgress by remember { mutableStateOf("") }
    var pollingCurrentIndex by remember { mutableStateOf(0) }
    var pollingTotal by remember { mutableStateOf(0) }

    var stopPolling by remember { mutableStateOf(false) }

    var autoPollExecuted by remember { mutableStateOf(false) }

    SideEffect {
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
                    stopPolling = false
                    scope.launch {
                        val result = UnlockRepo.pollAllDoors(
                            doors = selectedDoors,
                            onProgress = { index, total, name ->
                                if (stopPolling) return@pollAllDoors
                                withContext(Dispatchers.Main) {
                                    pollingCurrentIndex = index
                                    pollingTotal = total
                                    pollingProgress = "正在尝试 $index/$total: $name"
                                }
                            }
                        )
                        if (!stopPolling) {
                            isPolling = false
                            pollingProgress = if (result != null) "✅ 已开启: ${result.name}" else "❌ 未找到可开门禁"
                            doors.value = DataRepo.getDoors()
                        } else {
                            isPolling = false
                            pollingProgress = "⏹ 已停止轮询"
                            stopPolling = false
                        }
                    }
                }
            }
        }
    }

    Column {
        MainTopBar(
            onEditClick = { showManageDialog.value = true },
            onHelperClick = { navController.navigate("helper") },
            onSettingsClick = { navController.navigate("settings") }
        )

        Box(modifier = Modifier.weight(1f).padding(8.dp), contentAlignment = Alignment.Center) {
            if (hasPermission.value) {
                Column(modifier = Modifier.fillMaxSize()) {
                    val selectedCount = doors.value.count { it.isSelected }
                    PollButton(
                        doors = doors.value,
                        selectedCount = selectedCount,
                        isPolling = isPolling,
                        pollingProgress = pollingProgress,
                        onPollStart = {
                            val selectedDoors = doors.value.filter { it.isSelected }
                            if (selectedDoors.isEmpty()) {
                                showToast("请至少选择一个门禁")
                                return@PollButton
                            }
                            isPolling = true
                            pollingCurrentIndex = 0
                            pollingTotal = selectedDoors.size
                            pollingProgress = "准备轮询..."
                            stopPolling = false
                            scope.launch {
                                val result = UnlockRepo.pollAllDoors(
                                    doors = selectedDoors,
                                    onProgress = { index, total, name ->
                                        if (stopPolling) return@pollAllDoors
                                        withContext(Dispatchers.Main) {
                                            pollingCurrentIndex = index
                                            pollingTotal = total
                                            pollingProgress = "正在尝试 $index/$total: $name"
                                        }
                                    }
                                )
                                if (!stopPolling) {
                                    isPolling = false
                                    pollingProgress = if (result != null) "✅ 已开启: ${result.name}" else "❌ 未找到可开门禁"
                                    doors.value = DataRepo.getDoors()
                                } else {
                                    isPolling = false
                                    pollingProgress = "⏹ 已停止轮询"
                                    stopPolling = false
                                }
                            }
                        }
                    )

                    if (isPolling && pollingTotal > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LinearProgressIndicator(
                                progress = pollingCurrentIndex.toFloat() / pollingTotal,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    stopPolling = true
                                    isPolling = false
                                    pollingProgress = "⏹ 正在停止..."
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Stop,
                                    contentDescription = "停止轮询",
                                    tint = Color(0xFFF44336),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    DoorListContent(doors = doors, onRefresh = { doors.value = DataRepo.getDoors() })
                }
            } else {
                PermissionView(hasPermission)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(onClick = { navController.navigate("qr_export") }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.AppSettingsAlt, contentDescription = null)
                Spacer(modifier = Modifier.size(4.dp))
                Text("导出配置")
            }
            Spacer(modifier = Modifier.size(8.dp))
            OutlinedButton(onClick = { navController.navigate("qr_import") }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.HelpOutline, contentDescription = null)
                Spacer(modifier = Modifier.size(4.dp))
                Text("扫描导入")
            }
        }

        if (showManageDialog.value) {
            ManageDoorDialog(
                state = showManageDialog,
                initialDoors = doors.value,
                onSaved = { doors.value = DataRepo.getDoors() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(
    onEditClick: () -> Unit,
    onHelperClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    TopAppBar(
        title = { Text("🔓 智能门禁", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
        actions = {
            IconButton(onClick = onEditClick) { Icon(Icons.Default.Add, contentDescription = "添加门禁") }
            IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, contentDescription = "设置") }
            IconButton(onClick = onHelperClick) { Icon(Icons.Default.HelpOutline, contentDescription = "帮助") }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    )
}

@Composable
private fun PollButton(
    doors: List<DoorDevice>,
    selectedCount: Int,
    isPolling: Boolean,
    pollingProgress: String,
    onPollStart: () -> Unit
) {
    val hasDoors = doors.isNotEmpty()
    Button(
        onClick = { if (!isPolling && hasDoors) onPollStart() },
        enabled = !isPolling && hasDoors,
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPolling) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary
        )
    ) {
        if (isPolling) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onSecondaryContainer, strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = pollingProgress, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        } else {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "🔄 一键轮询开锁 ($selectedCount/${doors.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun DoorListContent(
    doors: MutableState<List<DoorDevice>>,
    onRefresh: () -> Unit,
) {
    if (doors.value.isEmpty()) {
        Text(text = "暂无门禁，请点击右上角添加", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(doors.value, key = { it.id }) { door ->
            DoorCard(
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
private fun DoorCard(
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

    val nameFontSize = if (door.name.length > 15) 15.sp else 18.sp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (door.isSelected) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = door.isSelected,
                onCheckedChange = { onToggleSelected(door.id) },
                modifier = Modifier.size(20.dp)
            )

            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    text = door.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = nameFontSize,
                    fontWeight = FontWeight.Medium,
                    color = if (door.isSelected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (door.mac.isNotEmpty()) {
                    Text(
                        text = door.mac,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (isUnlocking && unlockStep.isNotEmpty()) {
                    Text(
                        text = unlockStep,
                        fontSize = 12.sp,
                        color = when {
                            unlockStep.contains("成功") -> Color(0xFF4CAF50)
                            unlockStep.contains("失败") || unlockStep.contains("超时") -> Color(0xFFF44336)
                            else -> MaterialTheme.colorScheme.primary
                        },
                        fontWeight = if (unlockStep.contains("成功") || unlockStep.contains("失败")) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Row {
                    IconButton(onClick = { onMoveUp(door.id) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "上移", modifier = Modifier.size(14.dp))
                    }
                    IconButton(onClick = { onMoveDown(door.id) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "下移", modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Button(
                    onClick = {
                        if (door.mac.isEmpty() || door.key.isEmpty()) {
                            showToast("请先配置该门禁的 MAC 和 Key")
                            return@Button
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
                    },
                    enabled = !isUnlocking,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .height(34.dp)
                        .width(88.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            isUnlocking -> MaterialTheme.colorScheme.primary
                            unlockStep.contains("成功") -> Color(0xFF4CAF50)
                            unlockStep.contains("失败") || unlockStep.contains("超时") -> Color(0xFFF44336)
                            else -> MaterialTheme.colorScheme.primary
                        },
                        contentColor = Color.White
                    )
                ) {
                    when {
                        isUnlocking -> CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                        unlockStep.contains("成功") -> Text("✅", fontSize = 14.sp)
                        unlockStep.contains("失败") || unlockStep.contains("超时") -> Text("❌", fontSize = 14.sp)
                        else -> Text("开锁", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
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
    Button(
        modifier = Modifier.size(144.dp, 56.dp),
        onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
    ) {
        Text(text = stringResource(id = R.string.request_permission), fontSize = 18.sp)
    }
}
