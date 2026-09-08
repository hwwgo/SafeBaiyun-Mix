package cn.huacheng.safebaiyun.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import cn.huacheng.safebaiyun.theme.*
import cn.huacheng.safebaiyun.util.ConfigManager
import cn.huacheng.safebaiyun.util.showToast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(navController: NavController) {
    var unlockTimeout by remember { mutableStateOf(ConfigManager.getUnlockTimeout().toString()) }
    var pollInterval by remember { mutableStateOf(ConfigManager.getPollInterval().toString()) }
    var resultDelay by remember { mutableStateOf(ConfigManager.getResultDelay().toString()) }
    var resetDelay by remember { mutableStateOf(ConfigManager.getResetDelay().toString()) }
    var autoPoll by remember { mutableStateOf(ConfigManager.getAutoPollOnStart()) }
    var pollWaitTime by remember { mutableStateOf(ConfigManager.getPollWaitTime().toString()) }
    var hasChanges by remember { mutableStateOf(false) }

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
            SettingsTopBar(
                onBack = { navController.popBackStack() },
                onRestore = {
                    ConfigManager.resetToDefaults()
                    unlockTimeout = ConfigManager.getDefaultUnlockTimeout().toString()
                    pollInterval = ConfigManager.getDefaultPollInterval().toString()
                    resultDelay = ConfigManager.getDefaultResultDelay().toString()
                    resetDelay = ConfigManager.getDefaultResetDelay().toString()
                    autoPoll = ConfigManager.getDefaultAutoPoll()
                    pollWaitTime = ConfigManager.getDefaultPollWaitTime().toString()
                    hasChanges = false
                    showToast("已恢复默认设置")
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                InfoCard()

                AutoPollCard(
                    autoPoll = autoPoll,
                    onToggle = {
                        autoPoll = it
                        hasChanges = true
                    }
                )

                ConfigItem(
                    label = "轮询等待时间",
                    description = "自动轮询时等待蓝牙开启的最长时间",
                    value = pollWaitTime,
                    onValueChange = {
                        pollWaitTime = it
                        hasChanges = true
                    },
                    defaultValue = ConfigManager.getDefaultPollWaitTime().toString(),
                    unit = "毫秒"
                )

                ConfigItem(
                    label = "单次开锁超时",
                    description = "单次开锁允许的最大时间，超时则判定失败",
                    value = unlockTimeout,
                    onValueChange = {
                        unlockTimeout = it
                        hasChanges = true
                    },
                    defaultValue = ConfigManager.getDefaultUnlockTimeout().toString(),
                    unit = "毫秒"
                )

                ConfigItem(
                    label = "轮询间隔",
                    description = "轮询时，尝试两个门禁之间的等待时间",
                    value = pollInterval,
                    onValueChange = {
                        pollInterval = it
                        hasChanges = true
                    },
                    defaultValue = ConfigManager.getDefaultPollInterval().toString(),
                    unit = "毫秒"
                )

                ConfigItem(
                    label = "结果展示延迟",
                    description = "开锁完成后，显示成功/失败图标的时间",
                    value = resultDelay,
                    onValueChange = {
                        resultDelay = it
                        hasChanges = true
                    },
                    defaultValue = ConfigManager.getDefaultResultDelay().toString(),
                    unit = "毫秒"
                )

                ConfigItem(
                    label = "状态复位延迟",
                    description = "显示开锁结果后，自动复位到空闲状态的时间",
                    value = resetDelay,
                    onValueChange = {
                        resetDelay = it
                        hasChanges = true
                    },
                    defaultValue = ConfigManager.getDefaultResetDelay().toString(),
                    unit = "毫秒"
                )

                SaveButton(
                    hasChanges = hasChanges,
                    onClick = {
                        try {
                            val timeout = unlockTimeout.toLong()
                            val interval = pollInterval.toLong()
                            val result = resultDelay.toLong()
                            val reset = resetDelay.toLong()
                            val wait = pollWaitTime.toLong()
                            if (timeout < 1000 || interval < 100 || result < 100 || reset < 100 || wait < 100) {
                                showToast("数值不能小于 100ms")
                                return@SaveButton
                            }
                            ConfigManager.setUnlockTimeout(timeout)
                            ConfigManager.setPollInterval(interval)
                            ConfigManager.setResultDelay(result)
                            ConfigManager.setResetDelay(reset)
                            ConfigManager.setAutoPollOnStart(autoPoll)
                            ConfigManager.setPollWaitTime(wait)
                            hasChanges = false
                            showToast("✅ 设置已保存")
                        } catch (e: NumberFormatException) {
                            showToast("请输入有效的数字")
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar(
    onBack: () -> Unit,
    onRestore: () -> Unit
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
                        Icons.Default.Settings,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "设置",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        },
        navigationIcon = {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        actions = {
            IconButton(
                onClick = onRestore,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Icon(
                    Icons.Default.Restore,
                    contentDescription = "恢复默认",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        )
    )
}

@Composable
private fun InfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(ColorOSGradientStart, ColorOSGradientEnd)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    "时间参数设置",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "调整以下参数可以优化开锁体验，数值单位为毫秒（1秒 = 1000毫秒）",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AutoPollCard(
    autoPoll: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ColorOSPrimary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        tint = ColorOSPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = "打开软件自动轮询",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "启动应用后自动执行一键轮询",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Switch(
                checked = autoPoll,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = ColorOSPrimary,
                    checkedThumbColor = Color.White
                )
            )
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ColorOSSecondary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = null,
                        tint = ColorOSSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        label,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        description,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("数值", fontSize = 12.sp) },
                    trailingIcon = { 
                        Text(
                            unit,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ColorOSPrimary,
                        focusedLabelColor = ColorOSPrimary,
                        cursorColor = ColorOSPrimary
                    )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "默认: $defaultValue",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun SaveButton(
    hasChanges: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                brush = if (hasChanges) {
                    Brush.horizontalGradient(
                        colors = listOf(ColorOSGradientStart, ColorOSGradientEnd)
                    )
                } else {
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            )
            .clickable(enabled = hasChanges) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (hasChanges) "保存设置" else "设置已保存",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (hasChanges) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
