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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import cn.huacheng.safebaiyun.ShortcutActivity
import cn.huacheng.safebaiyun.theme.*
import cn.huacheng.safebaiyun.unlock.DataRepo
import cn.huacheng.safebaiyun.util.ConfigManager
import cn.huacheng.safebaiyun.util.ShortcutPin
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
    var unlockVibration by remember { mutableStateOf(ConfigManager.isUnlockVibrationEnabled()) }
    var unlockVibrationDuration by remember { mutableStateOf(ConfigManager.getUnlockVibrationDuration().toString()) }
    var unlockRecord by remember { mutableStateOf(ConfigManager.isUnlockRecordEnabled()) }
    var autoExit by remember { mutableStateOf(ConfigManager.isAutoExitEnabled()) }
    var hasChanges by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                        val vibrationDuration = unlockVibrationDuration.toLong()
                        if (timeout < 1000 || interval < 50 || result < 100 || reset < 100 || wait < 100 ||
                            scan < 500 || scan > 3000 || vibrationDuration < 1 || vibrationDuration > 5000) {
                            showToast("单次开锁超时不能小于1000ms，其它时间不能小于100ms，单门禁探测时间 500-3000ms，震动时长 1-5000ms")
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
                        ConfigManager.setUnlockVibrationEnabled(unlockVibration)
                        ConfigManager.setUnlockVibrationDuration(vibrationDuration)
                        ConfigManager.setUnlockRecordEnabled(unlockRecord)
                        ConfigManager.setAutoExitEnabled(autoExit)
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
                    unlockVibration = ConfigManager.getDefaultUnlockVibrationEnabled()
                    unlockVibrationDuration = ConfigManager.getDefaultUnlockVibrationDuration().toString()
                    unlockRecord = ConfigManager.getDefaultUnlockRecordEnabled()
                    autoExit = ConfigManager.getDefaultAutoExitEnabled()
                    ThemeModeState.resetToDefault()
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

                ShortcutCreatorCard()

                InfoCard()

                ThemeModeCard(
                    current = ThemeModeState.mode,
                    onSelect = { ThemeModeState.select(it) }
                )

                SettingsSwitchCard(
                    icon = Icons.Default.FormatSize,
                    title = "大字体模式",
                    description = "放大界面文字，方便阅读",
                    checked = largeFont,
                    onCheckedChange = {
                        largeFont = it
                        hasChanges = true
                    }
                )

                SettingsSwitchCard(
                    icon = Icons.Default.Vibration,
                    title = "开锁成功震动",
                    description = "开门成功后手机震动提示",
                    checked = unlockVibration,
                    onCheckedChange = {
                        unlockVibration = it
                        hasChanges = true
                    }
                )

                ConfigItem(
                    label = "震动时长",
                    description = "开锁成功后手机震动的持续时间，关闭震动后此项不会生效",
                    value = unlockVibrationDuration,
                    onValueChange = {
                        unlockVibrationDuration = it
                        hasChanges = true
                    },
                    defaultValue = ConfigManager.getDefaultUnlockVibrationDuration().toString(),
                    unit = "毫秒"
                )

                SettingsSwitchCard(
                    icon = Icons.Default.History,
                    title = "开锁记录",
                    description = "记录开锁成功、失败、时间和耗时",
                    checked = unlockRecord,
                    onCheckedChange = {
                        unlockRecord = it
                        hasChanges = true
                    }
                )

                SettingsSwitchCard(
                    icon = Icons.Default.ExitToApp,
                    title = "开锁成功后自动退出",
                    description = "解锁成功后自动关闭应用，从快捷方式或小部件开门时无需手动返回",
                    checked = autoExit,
                    onCheckedChange = {
                        autoExit = it
                        hasChanges = true
                    }
                )

                SettingsSwitchCard(
                    icon = Icons.Default.Refresh,
                    title = "打开软件自动开锁",
                    description = "启动应用后自动执行一键开锁",
                    checked = autoPoll,
                    onCheckedChange = {
                        autoPoll = it
                        hasChanges = true
                    }
                )

                SettingsSwitchCard(
                    icon = Icons.Default.BluetoothSearching,
                    title = "开锁前探测附近门禁",
                    description = "开锁前按顺序探测已勾选的门禁，命中后优先开锁",
                    checked = autoScan,
                    onCheckedChange = {
                        autoScan = it
                        hasChanges = true
                    }
                )

                ConfigItem(
                    label = "单个门禁探测时长",
                    description = "开锁前探测每个门禁的最长时间，发现后立即尝试开锁",
                    value = scanDuration,
                    onValueChange = {
                        scanDuration = it
                        hasChanges = true
                    },
                    defaultValue = ConfigManager.getDefaultScanDuration().toString(),
                    unit = "毫秒"
                )

                ConfigItem(
                    label = "等待蓝牙连接时间",
                    description = "开锁时等待手机与门禁建立蓝牙连接的最长时间",
                    value = pollWaitTime,
                    onValueChange = {
                        pollWaitTime = it
                        hasChanges = true
                    },
                    defaultValue = ConfigManager.getDefaultPollWaitTime().toString(),
                    unit = "毫秒"
                )

                ConfigItem(
                    label = "单次开锁超时时间",
                    description = "一次开锁操作允许的最长时间，超时则判定失败",
                    value = unlockTimeout,
                    onValueChange = {
                        unlockTimeout = it
                        hasChanges = true
                    },
                    defaultValue = ConfigManager.getDefaultUnlockTimeout().toString(),
                    unit = "毫秒"
                )

                ConfigItem(
                    label = "门禁切换等待时间",
                    description = "探测或开锁时，切换到下一个门禁前等待的时间",
                    value = pollInterval,
                    onValueChange = {
                        pollInterval = it
                        hasChanges = true
                    },
                    defaultValue = ConfigManager.getDefaultPollInterval().toString(),
                    unit = "毫秒"
                )

                ConfigItem(
                    label = "开锁结果显示时间",
                    description = "开锁完成后，成功或失败状态显示的时间",
                    value = resultDelay,
                    onValueChange = {
                        resultDelay = it
                        hasChanges = true
                    },
                    defaultValue = ConfigManager.getDefaultResultDelay().toString(),
                    unit = "毫秒"
                )

                ConfigItem(
                    label = "状态恢复时间",
                    description = "显示开锁结果后，自动恢复到空闲状态的时间",
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
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        actions = {
            TextButton(
                onClick = onSave,
                enabled = hasChanges,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    "保存",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (hasChanges) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }

            IconButton(
                onClick = onRestore,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Restore,
                    contentDescription = "恢复默认",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        )
    )
}

@Composable
private fun ShortcutCreatorCard() {
    val context = LocalContext.current
    val doors = remember { DataRepo.getDoors() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ColorOSPrimary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AddToHomeScreen,
                        contentDescription = null,
                        tint = ColorOSPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        "桌面快捷方式",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "在桌面创建快捷图标，一键直达开锁或指定门禁",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    ShortcutPin.pinShortcut(context, ShortcutActivity.ALL_DOORS_ID, "一键开锁")
                },
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ColorOSPrimary,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("创建「一键开锁」快捷方式", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }

            if (doors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "为以下门禁创建快捷方式：",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                doors.forEach { door ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.DoorFront,
                            contentDescription = null,
                            tint = ColorOSPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            door.name,
                            modifier = Modifier.weight(1f),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Button(
                            onClick = {
                                ShortcutPin.pinShortcut(context, door.id, door.name)
                            },
                            // 与门禁「开锁」按钮、主界面「一键开锁」按钮统一为 ColorOSPrimary（蓝紫）
                            modifier = Modifier.height(36.dp),
                            shape = RoundedCornerShape(percent = 50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ColorOSPrimary,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("创建", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "暂无门禁，请先在「门禁管理」中添加。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
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
private fun SettingsSwitchCard(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
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
                        imageVector = icon,
                        contentDescription = null,
                        tint = ColorOSPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = description,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    // 与「开锁成功震动」开关统一为 ColorOSPrimary，避免同屏两个开关颜色不一致
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
private fun ThemeModeCard(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ColorOSPrimary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = ColorOSPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = "文字背景颜色",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "选择背景的明暗风格，切换后立即生效",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeModeOption(
                    icon = Icons.Default.BrightnessAuto,
                    label = "跟随系统",
                    selected = current == ThemeMode.SYSTEM,
                    onClick = { onSelect(ThemeMode.SYSTEM) },
                    modifier = Modifier.weight(1f)
                )
                ThemeModeOption(
                    icon = Icons.Default.LightMode,
                    label = "浅色",
                    selected = current == ThemeMode.LIGHT,
                    onClick = { onSelect(ThemeMode.LIGHT) },
                    modifier = Modifier.weight(1f)
                )
                ThemeModeOption(
                    icon = Icons.Default.DarkMode,
                    label = "深色",
                    selected = current == ThemeMode.DARK,
                    onClick = { onSelect(ThemeMode.DARK) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ThemeModeOption(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (selected) ColorOSPrimary else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = contentColor
        )
    }
}
