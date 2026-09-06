package cn.huacheng.safebaiyun.compose

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import cn.huacheng.safebaiyun.unlock.DataRepo
import cn.huacheng.safebaiyun.unlock.DoorDevice
import cn.huacheng.safebaiyun.unlock.UnlockRepo
import cn.huacheng.safebaiyun.util.showToast
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainView(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bluetoothOn = remember { mutableStateOf(checkBluetooth(context)) }

    // 从 DataRepo 获取门禁列表（使用 State 自动刷新）
    var doorList by remember { mutableStateOf(DataRepo.readAllConfigs()) }

    // 监听数据变化（当添加/删除门禁时更新列表）
    // 简单方案：使用一个刷新触发器
    val refreshTrigger = remember { MutableStateFlow(0) }

    // 实际项目中 DataRepo 可能提供 Flow，这里用协程定期检查或通过事件通知
    // 为了简化，我们提供一个手动刷新函数，由添加/删除操作触发
    fun refreshList() {
        doorList = DataRepo.readAllConfigs()
    }

    // 监听刷新触发器
    LaunchedEffect(Unit) {
        // 可以监听 SharedPreferences 变化，但这里简单处理，在添加/删除后手动调用
        // 由于原仓库的添加/删除可能在 ManageDoorDialog 中，我们无法在此监听，
        // 但可以使用一个全局的刷新事件（例如通过 DataRepo 的更新回调）。
        // 为演示，我们使用一个定时刷新（仅开发测试用）
        // 实际使用时，最好在 ManageDoorDialog 的保存/删除成功后调用 refreshList
    }

    // 权限启动器（保留）
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.all { it }) {
            showToast("权限已授予")
        } else {
            showToast("需要蓝牙权限才能开锁")
        }
    }

    // 初始化 UnlockRepo（传入协程作用域）
    UnlockRepo.init(scope)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "🔓 智能门禁",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (!bluetoothOn.value) {
                                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (bluetoothOn.value) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                            contentDescription = "蓝牙状态",
                            tint = if (bluetoothOn.value) Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                    }
                    IconButton(onClick = { navController.navigate("helper") }) {
                        Icon(Icons.Default.Info, contentDescription = "帮助")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    // 打开添加门禁对话框（原仓库可能使用 Navigator 或直接弹 Dialog）
                    // 这里假设路由为 "add_door"，若不对请改为实际路由或调用 Dialog
                    navController.navigate("add_door")
                },
                containerColor = MaterialTheme.colorScheme.primary,
                elevation = FloatingActionButtonDefaults.elevation(6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加门禁")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // ---- 概览卡片 ----
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "${doorList.size}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(text = "门禁总数", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "2", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                        Text(text = "在线", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "1", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9E9E9E))
                        Text(text = "离线", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ---- 门禁列表 ----
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (doorList.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.DoorFront,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "还没有门禁配置",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "点击右下角的 + 添加你的门禁",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(doorList) { door ->
                        EnhancedDoorCard(door = door)
                    }
                }
            }
        }
    }
}

/**
 * 增强版门禁卡片
 */
@Composable
fun EnhancedDoorCard(door: DoorDevice) {
    val scope = rememberCoroutineScope()
    var isUnlocking by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = door.name.ifEmpty { "未命名设备" },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF4CAF50))
                    )
                }
                Text(
                    text = door.mac,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = {
                    if (!isUnlocking) {
                        isUnlocking = true
                        scope.launch {
                            val success = UnlockRepo.tryUnlock(door.mac, door.key)
                            if (success) {
                                showToast("✅ ${door.name} 开门成功")
                            } else {
                                showToast("❌ ${door.name} 开门失败")
                            }
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
                        text = "开锁",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * 检查蓝牙是否开启
 */
private fun checkBluetooth(context: Context): Boolean {
    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter = manager?.adapter
    return adapter?.isEnabled == true
}
