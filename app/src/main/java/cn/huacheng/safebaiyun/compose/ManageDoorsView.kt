package cn.huacheng.safebaiyun.compose

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.MutableState   // ✅ 修复：导入 MutableState 类型
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("管理门禁", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = {
                        DataRepo.saveDoors(doors.toList())
                        onSaved()
                        navController.popBackStack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        DataRepo.saveDoors(doors.toList())
                        onSaved()
                        showToast("已保存")
                        navController.popBackStack()
                    }) {
                        Text("保存")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            if (doors.isEmpty()) {
                Text(
                    text = "暂无门禁，点击下方添加",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .padding(vertical = 32.dp)
                        .align(Alignment.CenterHorizontally)
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                items(doors, key = { it.id }) { door ->
                    DoorEditItem(
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
                            if (name.isEmpty()) return@DoorEditItem

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

            OutlinedButton(
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
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("添加门禁")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DoorEditItem(
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
        .padding(4.dp)
        .fillMaxWidth()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEditing)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = door.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )

                if (!isEditing) {
                    IconButton(onClick = onToggleExpand, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "编辑",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    TextButton(onClick = onSaveEdit) { Text("完成") }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (isEditing) {
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = editName.value,
                    onValueChange = { editName.value = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = fieldModifier
                )
                OutlinedTextField(
                    value = editMac.value,
                    onValueChange = { editMac.value = it },
                    label = { Text("MAC 地址") },
                    singleLine = true,
                    placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                    modifier = fieldModifier
                )
                OutlinedTextField(
                    value = editKey.value,
                    onValueChange = { editKey.value = it },
                    label = { Text("加密 Key") },
                    singleLine = true,
                    modifier = fieldModifier
                )
            }
        }
    }
}
