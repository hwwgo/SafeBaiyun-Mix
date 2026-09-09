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
    var autoScan by remember { mutableStateOf(ConfigManager.getAutoScanEnabled()) }
    var scanDuration by remember { mutableStateOf(ConfigManager.getScanDuration().toString()) }
    var pollWaitTime by remember { mutableStateOf(ConfigManager.getPollWaitTime().toString()) }
    var largeFont by remember { mutableStateOf(ConfigManager.isLargeFont()) }
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
                hasChanges = hasChanges,
                onBack = { navController.popBackStack() },
                onSave = {
                    try {
                        val timeout = unlockTimeout.toLong()
                        val interval = pollInterval.toLong()
                        val result = resultDelay.toLong()
                        val reset = resetDelay.toLong()
                        val wait = pollWaitTime.toLong()
                        val scan = scanDuration.toLong()
                        if (timeout < 1000 || interval < 100 || result < 100 || reset < 100 || wait < 100 || scan < 100 || scan > 10000) {
                            showToast("单次开锁超时不能小于1000ms，其它时间不能小于100ms，扫描时间为100-10000ms")
                            return@SettingsTopBar
                        }
                        ConfigManager.setUnlockTimeout(timeout)
                        ConfigManager.setPollInterval(interval)
                        ConfigManager.setResultDelay(result)
                        ConfigManager.setResetDelay(reset)
                        ConfigManager.setAutoPollOnStart(autoPoll)
                        ConfigManager.setAutoScanEnabled(autoScan)
                        ConfigManager.setScanDuration(scan)
                        ConfigManager.setPollWaitTime(wait)
                        ConfigManager.setLargeFont(largeFont)
                        hasChanges = false
                        showToast("✅ 设置已保存，重启应用后生效")
                    } catch (e: NumberFormatException) {
                        showToast("请输入有效的数字")
                    }
                },
                onRestore = {
                    ConfigManager.resetToDefaults()
                    unlockTimeout = ConfigManager.getDefaultUnlockTimeout().toString()
                    pollInterval = ConfigManager.getDefaultPollInterval().toString()
                    resultDelay = ConfigManager.getDefaultResultDelay().toString()
                    resetDelay = ConfigManager.getDefaultResetDelay().toString()
                    autoPoll = ConfigManager.getDefaultAutoPoll()
                    autoScan = ConfigManager.getDefaultAutoScanEnabled()
                    scanDuration = ConfigManager.getDefaultScanDuration().toString()
                    pollWaitTime = ConfigManager.getDefaultPollWaitTime().toString()
                    largeFont = ConfigManager.getDefaultLargeFont()
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

                // 大字体模式开关
                LargeFontCard(
                    largeFont = largeFont,
                    onToggle = {
                        largeFont = it
                        hasChanges = true
                    }
                )

                AutoPollCard(
                    autoPoll = autoPoll,
                    onToggle = {
                        autoPoll = it
                        hasChanges = true
                    }
                )

                AutoScanCard(
                    autoScan = autoScan,
                    onToggle = {
                        autoScan = it
                        hasChanges = true
                    }
                )

                ConfigItem(
                    label = "自动扫描时间",
                    description = "自动轮询前扫描附近已配置门禁的最长时间",
                    value = scanDuration,
                    onValueChange = {
                        scanDuration = it
                        hasChanges = true
                    },
                    defaultValue = ConfigManager.getDefaultScanDuration().toString(),
                    unit = "毫秒"
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
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar(
    hasChanges: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
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
            TextButton(
                onClick = onSave,
                enabled = hasChanges
            ) {
                Text(
                    "保存",
                    fontWeight = FontWeight.Bold
                )
            }

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
                    "设置",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "调整以下参数可以优化开锁体验",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LargeFontCard(
    largeFont: Boolean,
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
                        .background(ColorOSTertiary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.FormatSize,
                        contentDescription = null,
                        tint = ColorOSTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = "大字体模式",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "放大界面文字，方便阅读",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Switch(
                checked = largeFont,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = ColorOSTertiary,
                    checkedThumbColor = Color.White
                )
            )
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
private fun AutoScanCard(
    autoScan: Boolean,
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
                        .background(ColorOSSecondary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.BluetoothSearching,
                        contentDescription = null,
                        tint = ColorOSSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = "自动轮询前扫描门禁",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "先扫描已配置 MAC，命中后优先开锁，不使用信号强度",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Switch(
                checked = autoScan,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = ColorOSSecondary,
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

