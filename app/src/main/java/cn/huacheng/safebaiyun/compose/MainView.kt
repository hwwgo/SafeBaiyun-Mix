package cn.huacheng.safebaiyun.compose

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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

@Composable
fun MainView(navController: NavHostController) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val hasPermission = remember {
        mutableStateOf(false)
    }

    val showManageDialog = remember {
        mutableStateOf(false)
    }

    // 门禁列表状态（响应式刷新）
    val doors = remember {
        mutableStateOf<List<DoorDevice>>(DataRepo.getDoors())
    }

    // 轮询状态
    var isPolling by remember { mutableStateOf(false) }
    var pollingProgress by remember { mutableStateOf("") }
    var pollingCurrentIndex by remember { mutableStateOf(0) }
    var pollingTotal by remember { mutableStateOf(0) }

    SideEffect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission.value =
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            hasPermission.value = true
        }
    }

    Column {
        // 使用独立的 TopBar 组件
        MainTopBar(
            onEditClick = {
                showManageDialog.value = true
            },
            onHelperClick = {
                navController.navigate("helper")
            },
            onSettingsClick = {
                navController.navigate("settings")
            }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(8.dp), contentAlignment = Alignment.Center
        ) {
            if (hasPermission.value) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // ---- 轮询按钮和进度条 ----
                    PollButton(
                        doors = doors.value,
                        isPolling = isPolling,
                        pollingProgress = pollingProgress,
                        onPollStart = {
                            isPolling = true
                            pollingCurrentIndex = 0
                            pollingTotal = doors.value.size
                            pollingProgress = "准备轮询..."
                            scope.launch {
                                val result = UnlockRepo.pollAllDoors(
                                    doors = doors.value,
                                    onProgress = { index, total, name ->
                                        withContext(Dispatchers.Main) {
                                            pollingCurrentIndex = index
                                            pollingTotal = total
                                            pollingProgress = "正在尝试 $index/$total: $name"
                                        }
                                    }
                                )
                                // 轮询结束
                                isPolling = false
                                pollingProgress = if (result != null) "✅ 已开启: ${result.name}" else "❌ 未找到可开门禁"
                                // 刷新列表（如果有变化）
                                doors.value = DataRepo.getDoors()
                            }
                        }
                    )

                    // 轮询进度条（仅在轮询时显示）
                    if (isPolling && pollingTotal > 0) {
                        LinearProgressIndicator(
                            progress = pollingCurrentIndex.toFloat() / pollingTotal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // ---- 门禁列表 ----
                    DoorListContent(doors = doors, onRefresh = {
                        doors.value = DataRepo.getDoors()
                    })
                }
            } else {
                PermissionView(hasPermission)
            }
        }

        // 底部二维码操作按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(
                onClick = { navController.navigate("qr_export") },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.AppSettingsAlt, contentDescription = null)
                Spacer(modifier = Modifier.size(4.dp))
                Text("导出配置")
            }

            Spacer(modifier = Modifier.size(8.dp))

            OutlinedButton(
                onClick = { navController.navigate("qr_import") },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.HelpOutline, contentDescription = null)
                Spacer(modifier = Modifier.size(4.dp))
                Text("扫描导入")
            }
        }

        if (showManageDialog.value) {
            ManageDoorDialog(
                state = showManageDialog,
                initialDoors = doors.value,
                onSaved = {
                    doors.value = DataRepo.getDoors()
                }
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
        title = {
            Text(
                text = "🔓 智能门禁",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        actions = {
            IconButton(onClick = onEditClick) {
                Icon(Icons.Default.Add, contentDescription = "添加门禁")
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "设置")
            }
            IconButton(onClick = onHelperClick) {
                Icon(Icons.Default.HelpOutline, contentDescription = "帮助")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
        )
    )
}

@Composable
private fun PollButton(
    doors: List<DoorDevice>,
    isPolling: Boolean,
    pollingProgress: String,
    onPollStart: () -> Unit
) {
    val hasDoors = doors.isNotEmpty()

    Button(
        onClick = {
            if (!isPolling && hasDoors) {
                onPollStart()
            }
        },
        enabled = !isPolling && hasDoors,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPolling) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.primary
        )
    ) {
        if (isPolling) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = pollingProgress,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        } else {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "🔄 一键轮询开锁 (${doors.size})",
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(doors.value, key = { it.id }) { door ->
            DoorCard(door)
        }
        item {
            Spacer(modifier = Modifier.size(60.dp))
        }
    }
}

@Composable
private fun DoorCard(door: DoorDevice) {
    val scope = rememberCoroutineScope()
    var isUnlocking by remember { mutableStateOf(false) }
    var unlockStep by remember { mutableStateOf("") }

    // 监听 UnlockRepo 的步骤状态
    val stepState = UnlockRepo.unlockStep

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：名称 + MAC + 步骤
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = door.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (door.mac.isNotEmpty()) {
                    Text(
                        text = door.mac,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                // 显示当前开锁步骤（仅当正在解锁时）
                if (isUnlocking && unlockStep.isNotEmpty()) {
                    Text(
                        text = unlockStep,
                        fontSize = 13.sp,
                        color = when {
                            unlockStep.contains("成功") -> Color(0xFF4CAF50)
                            unlockStep.contains("失败") || unlockStep.contains("超时") -> Color(0xFFF44336)
                            else -> MaterialTheme.colorScheme.primary
                        },
                        fontWeight = if (unlockStep.contains("成功") || unlockStep.contains("失败")) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // 右侧：开锁按钮
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
                            // 监听步骤更新
                            val job = launch {
                                stepState.collectLatest { step ->
                                    if (step.isNotEmpty()) {
                                        unlockStep = step
                                    }
                                }
                            }
                            val success = UnlockRepo.tryUnlock(door.mac, door.key)
                            // 等待最终状态显示（使用用户自定义延迟）
                            delay(ConfigManager.getResultDelay())
                            job.cancel()
                            // 如果最终步骤没有包含成功/失败，补充显示
                            if (!unlockStep.contains("成功") && !unlockStep.contains("失败") && !unlockStep.contains("超时")) {
                                unlockStep = if (success) "✅ 开锁成功" else "❌ 开锁失败"
                            }
                            // 延迟后重置状态，让用户看到结果（使用用户自定义延迟）
                            delay(ConfigManager.getResetDelay())
                            isUnlocking = false
                            unlockStep = ""
                        }
                    }
                },
                enabled = !isUnlocking,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .height(40.dp)
                    .width(if (isUnlocking) 100.dp else 80.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when {
                        isUnlocking -> MaterialTheme.colorScheme.primary
                        unlockStep.contains("成功") -> Color(0xFF4CAF50)
                        unlockStep.contains("失败") || unlockStep.contains("超时") -> Color(0xFFF44336)
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            ) {
                when {
                    isUnlocking -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    }
                    unlockStep.contains("成功") -> {
                        Text(
                            text = "✅",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    unlockStep.contains("失败") || unlockStep.contains("超时") -> {
                        Text(
                            text = "❌",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    else -> {
                        Text(
                            text = stringResource(id = R.string.unlock_door),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionView(hasPermission: MutableState<Boolean>) {
    val requestPermissionLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { isGranted ->
            hasPermission.value = isGranted
        }

    Button(
        modifier = Modifier.size(144.dp, 56.dp),
        onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }) {
        Text(text = stringResource(id = R.string.request_permission), fontSize = 18.sp)
    }
}
