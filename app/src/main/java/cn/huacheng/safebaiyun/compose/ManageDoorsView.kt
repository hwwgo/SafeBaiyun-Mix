package cn.huacheng.safebaiyun.compose

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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import cn.huacheng.safebaiyun.theme.*
import cn.huacheng.safebaiyun.unlock.DataRepo
import cn.huacheng.safebaiyun.unlock.DoorDevice
import cn.huacheng.safebaiyun.util.showToast
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageDoorsView(
    navController: NavController,
    onSaved: () -> Unit = {}
) {
    var doors by remember { mutableStateOf(DataRepo.getDoors().map { it.copy() }.toMutableList()) }
    var editingId by remember { mutableStateOf<String?>(null) }

    val editName = remember { mutableStateOf("") }
    val editMac = remember { mutableStateOf("") }
    val editKey = remember { mutableStateOf("") }

    // ColorOS 16 渐变背景
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
            // ColorOS 16 顶部栏
            ColorOSManageTopBar(
                onBack = {
                    DataRepo.saveDoors(doors.toList())
                    onSaved()
                    navController.popBackStack()
                },
                onSave = {
                    DataRepo.saveDoors(doors.toList())
                    onSaved()
                    showToast("已保存")
                    navController.popBackStack()
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                if (doors.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Home,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "暂无门禁，点击下方添加",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(doors, key = { it.id }) { door ->
                        ColorOSDoorEditItem(
                            door = door,
                            isEditing = editingId == door.id,
                            editName = editName,
                            editMac = editMac,
                            editKey = editKey,
                            onToggleExpand = {
                                if (editingId == door.id) {
                                    editingId = null
                                } else {
                                    editingId = door.id
                                    editName.value = door.name
                                    editMac.value = door.mac
                                    editKey.value = door.key
                                }
                            },
                            onSaveEdit = {
                                val name = editName.value.trim()
                                val mac = editMac.value.trim()
                                val key = editKey.value.trim()
                                if (name.isEmpty()) return@ColorOSDoorEditItem

                                val index = doors.indexOfFirst { it.id == door.id }
                                if (index >= 0) {
                                    doors[index] = door.copy(name = name, mac = mac, key = key)
                                }
                                editingId = null
                            },
                            onDelete = {
                                val newList = doors.toMutableList()
                                newList.removeAll { it.id == door.id }
                                doors = newList
                                if (editingId == door.id) editingId = null
                            }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // ColorOS 16 添加按钮
                ColorOSAddButton(
                    onClick = {
                        val newId = UUID.randomUUID().toString()
                        val newName = "门禁${doors.size + 1}"
                        val newDoor = DoorDevice(
                            id = newId,
                            name = newName,
                            mac = "",
                            key = ""
                        )
                        doors = (doors + newDoor).toMutableList()
                        editingId = newId
                        editName.value = newName
                        editMac.value = ""
                        editKey.value = ""
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorOSManageTopBar(
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                "管理门禁",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        },
        navigationIcon = {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        actions = {
            TextButton(onClick = onSave) {
                Text(
                    "保存",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ColorOSPrimary
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        )
    )
}

@Composable
private fun ColorOSAddButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = ColorOSGlow,
                spotColor = ColorOSGlow
            )
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(ColorOSGradientStart, ColorOSGradientEnd)
                )
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "添加门禁",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun ColorOSDoorEditItem(
    door: DoorDevice,
    isEditing: Boolean,
    editName: MutableState<String>,
    editMac: MutableState<String>,
    editKey: MutableState<String>,
    onToggleExpand: () -> Unit,
    onSaveEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val fieldModifier = Modifier
        .padding(vertical = 4.dp)
        .fillMaxWidth()

    // ColorOS 16 毛玻璃卡片
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isEditing) 6.dp else 3.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = if (isEditing) ColorOSGlow else ColorOSGlow.copy(alpha = 0.3f),
                spotColor = if (isEditing) ColorOSGlow else ColorOSGlow.copy(alpha = 0.3f)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEditing)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 门禁图标
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(ColorOSGradientStart, ColorOSGradientEnd)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Home,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = door.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (!isEditing) {
                    IconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "编辑",
                            tint = ColorOSPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    TextButton(onClick = onSaveEdit) {
                        Text(
                            "完成",
                            color = ColorOSPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ColorOSError.copy(alpha = 0.1f))
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = ColorOSError,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (isEditing) {
                Spacer(modifier = Modifier.height(12.dp))

                ColorOSTextField(
                    value = editName.value,
                    onValueChange = { editName.value = it },
                    label = "名称",
                    modifier = fieldModifier
                )

                ColorOSTextField(
                    value = editMac.value,
                    onValueChange = { editMac.value = it },
                    label = "MAC 地址",
                    placeholder = "AA:BB:CC:DD:EE:FF",
                    modifier = fieldModifier
                )

                ColorOSTextField(
                    value = editKey.value,
                    onValueChange = { editKey.value = it },
                    label = "加密 Key",
                    modifier = fieldModifier
                )
            }
        }
    }
}

@Composable
private fun ColorOSTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (placeholder.isNotEmpty()) { { Text(placeholder) } } else null,
        singleLine = true,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ColorOSPrimary,
            focusedLabelColor = ColorOSPrimary,
            cursorColor = ColorOSPrimary
        )
    )
}
