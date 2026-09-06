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
import androidx.compose.material.icons.filled.AppSettingsAlt
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import cn.huacheng.safebaiyun.util.showToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 主页面 —— 门禁列表 + 开门操作
 */
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

    SideEffect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission.value =
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            hasPermission.value = true
        }
    }

    Column {
        MainTopBar(onEditClick = {
            showManageDialog.value = true
        }, onHelperClick = {
            navController.navigate("helper")
        })
        
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(8.dp), contentAlignment = Alignment.Center
        ) {
            if (hasPermission.value) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // ---- 一键轮询按钮 ----
                    PollButton(
                        doors = doors.value,
                        isPolling = isPolling,
                        pollingProgress = pollingProgress,
                        onPollStart = { onProgress ->
                            isPolling = true
                            pollingProgress = "准备轮询..."
                            scope.launch {
                                val result = UnlockRepo.pollAllDoors(
                                    doors = doors.value,
                                    onProgress = { index, total, name ->
                                        withContext(Dispatchers.Main) {
                                            pollingProgress = "正在尝试 $index/$total: $name"
                                        }
                                    }
                                )
                                // 轮询结束，重置状态
                                isPolling = false
                                pollingProgress = if (result != null) "✅ 已开启: ${result.name}" else "❌ 未找到可开门禁"
                                // 刷新列表（如果有变化）
                                doors.value = DataRepo.getDoors()
                            }
                        }
                    )

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

// ============================================================
//  轮询按钮组件
// ============================================================

@Composable
private fun PollButton(
    doors: List<DoorDevice>,
    isPolling: Boolean,
    pollingProgress: String,
    onPollStart: (suspend (Int, Int, String) -> Unit) -> Unit
) {
    val hasDoors = doors.isNotEmpty()

    Button(
        onClick = {
            if (!isPolling && hasDoors) {
                onPollStart { _, _, _ -> }
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

// ============================================================
//  门禁列表区域（保持不变）
// ============================================================

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

// ============================================================
//  门禁卡片（之前美化好的版本）
// ============================================================

@Composable
private fun DoorCard(door: DoorDevice) {
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
            }

            Button(
                onClick = {
                    if (door.mac.isEmpty() || door.key.isEmpty()) {
                        showToast("请先配置该门禁的 MAC 和 Key")
                        return@Button
                    }
                    showToast("正在解锁 ${door.name}")
                    UnlockRepo.unlock(door.mac, door.key)
                },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .height(40.dp)
                    .width(80.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.unlock_door),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── 权限请求视图（保持不变） ──

@Composable
private fun PermissionView(hasPermission: MutableState<Boolean>) {
    val requestPermissionLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { isGranted ->
            hasPermission.value = isGranted
        }

    Button(modifier = Modifier.size(144.dp, 56.dp),
        onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }) {
        Text(text = stringResource(id = R.string.request_permission), fontSize = 18.sp)
    }
}
