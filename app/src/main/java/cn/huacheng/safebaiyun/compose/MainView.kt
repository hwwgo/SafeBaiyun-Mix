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
import cn.huacheng.safebaiyun.util.showToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 主页面 —— 门禁列表 + 开门操作
 */
@Composable
fun MainView(navController: NavHostController) {

    val context = LocalContext.current

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
                DoorListContent(doors = doors, onRefresh = {
                    doors.value = DataRepo.getDoors()
                })
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

/**
 * 门禁列表区域
 */
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
            Spacer(modifier = Modifier.size(60.dp)) // 底部留白，不遮挡 FAB
        }
    }
}

// ============================================================
//  👇 以下为优化后的 DoorCard（UI 美化，功能不变）
// ============================================================

/**
 * 单个门禁卡片（优化版）
 * 采用横向布局：左侧名称+MAC，右侧开锁按钮
 * 增加圆角、阴影、状态圆点
 */
@Composable
private fun DoorCard(door: DoorDevice) {
    // 按钮加载状态
    var isUnlocking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
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
            // 左侧：名称 + MAC + 状态圆点
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // 门禁名称 + 状态圆点
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        text = door.name.ifEmpty { "未命名门禁" },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // 在线状态圆点（固定绿色，实际可根据连接状态变化）
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .then(
                                androidx.compose.foundation.layout.Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .then(
                                            androidx.compose.foundation.shape.RoundedCornerShape(50)
                                        )
                                )
                            )
                            .background(Color(0xFF4CAF50))
                    )
                }
                // MAC 地址（完整显示）
                if (door.mac.isNotEmpty()) {
                    Text(
                        text = door.mac,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 右侧：开锁按钮（胶囊形状 + 加载状态）
            Button(
                onClick = {
                    if (door.mac.isEmpty() || door.key.isEmpty()) {
                        showToast("请先配置该门禁的 MAC 和 Key")
                        return@Button
                    }
                    if (!isUnlocking) {
                        isUnlocking = true
                        scope.launch {
                            // 调用原有的开锁逻辑
                            showToast("正在解锁 ${door.name}")
                            // 使用原有的 unlock 方法（无返回值，保持兼容）
                            UnlockRepo.unlock(door.mac, door.key)
                            // 延时恢复按钮状态（因为 unlock 没有回调，只能估时）
                            // 如果不想用延时，可以改用 tryUnlock 挂起函数
                            // 但为了保持原样，这里使用延时 3 秒后恢复
                            kotlinx.coroutines.delay(3000)
                            isUnlocking = false
                        }
                    }
                },
                enabled = !isUnlocking,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .height(40.dp)
                    .width(80.dp)
            ) {
                if (isUnlocking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
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

// 需要添加 rememberCoroutineScope 的导入
// 在文件顶部添加：
// import androidx.compose.runtime.rememberCoroutineScope

// ============================================================
//  辅助函数
// ============================================================

/**
 * 将 AA:BB:CC:DD:EE:FF 显示为 AA:BB:CC:...
 * （保留，以防其他地方使用）
 */
private fun formatMacShort(mac: String): String {
    val parts = mac.split(":")
    return if (parts.size >= 3) "${parts[0]}:${parts[1]}:${parts[2]}..." else mac
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
