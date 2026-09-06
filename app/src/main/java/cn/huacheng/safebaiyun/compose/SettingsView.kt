package cn.huacheng.safebaiyun.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import cn.huacheng.safebaiyun.util.ConfigManager
import cn.huacheng.safebaiyun.util.showToast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(navController: NavController) {
    val context = LocalContext.current

    var unlockTimeout by remember { mutableStateOf(ConfigManager.getUnlockTimeout().toString()) }
    var pollInterval by remember { mutableStateOf(ConfigManager.getPollInterval().toString()) }
    var resultDelay by remember { mutableStateOf(ConfigManager.getResultDelay().toString()) }
    var resetDelay by remember { mutableStateOf(ConfigManager.getResetDelay().toString()) }
    var hasChanges by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⚙️ 设置", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            ConfigManager.resetToDefaults()
                            unlockTimeout = ConfigManager.getDefaultUnlockTimeout().toString()
                            pollInterval = ConfigManager.getDefaultPollInterval().toString()
                            resultDelay = ConfigManager.getDefaultResultDelay().toString()
                            resetDelay = ConfigManager.getDefaultResetDelay().toString()
                            hasChanges = false
                            showToast("已恢复默认设置")
                        }
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = "恢复默认")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("⏱️ 时间参数设置", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        "调整以下参数可以优化开锁体验，数值单位为毫秒（1秒 = 1000毫秒）",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            ConfigItem(
                label = "单次开锁超时",
                description = "单次开锁允许的最大时间，超时则判定失败",
                value = unlockTimeout,
                onValueChange = { unlockTimeout = it; hasChanges = true },
                defaultValue = ConfigManager.getDefaultUnlockTimeout().toString(),
                unit = "毫秒"
            )

            ConfigItem(
                label = "轮询间隔",
                description = "轮询时，尝试两个门禁之间的等待时间",
                value = pollInterval,
                onValueChange = { pollInterval = it; hasChanges = true },
                defaultValue = ConfigManager.getDefaultPollInterval().toString(),
                unit = "毫秒"
            )

            ConfigItem(
                label = "结果展示延迟",
                description = "开锁完成后，显示成功/失败图标的时间",
                value = resultDelay,
                onValueChange = { resultDelay = it; hasChanges = true },
                defaultValue = ConfigManager.getDefaultResultDelay().toString(),
                unit = "毫秒"
            )

            ConfigItem(
                label = "状态复位延迟",
                description = "显示开锁结果后，自动复位到空闲状态的时间",
                value = resetDelay,
                onValueChange = { resetDelay = it; hasChanges = true },
                defaultValue = ConfigManager.getDefaultResetDelay().toString(),
                unit = "毫秒"
            )

            Button(
                onClick = {
                    try {
                        val timeout = unlockTimeout.toLong()
                        val interval = pollInterval.toLong()
                        val result = resultDelay.toLong()
                        val reset = resetDelay.toLong()
                        if (timeout < 1000 || interval < 100 || result < 100 || reset < 100) {
                            showToast("数值不能小于 100ms")
                            return@Button
                        }
                        ConfigManager.setUnlockTimeout(timeout)
                        ConfigManager.setPollInterval(interval)
                        ConfigManager.setResultDelay(result)
                        ConfigManager.setResetDelay(reset)
                        hasChanges = false
                        showToast("✅ 设置已保存")
                    } catch (e: NumberFormatException) {
                        showToast("请输入有效的数字")
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = hasChanges
            ) {
                Text(if (hasChanges) "💾 保存设置" else "设置已保存", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ConfigItem(
    label: String,
    description: String,
    value: String,
    onValueChange: (String) -> Unit,
    defaultValue: String,
    unit: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("数值") },
                    trailingIcon = { Text(unit, fontSize = 12.sp) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("默认: $defaultValue", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
