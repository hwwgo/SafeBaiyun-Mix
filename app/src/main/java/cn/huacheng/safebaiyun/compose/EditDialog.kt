package cn.huacheng.safebaiyun.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.huacheng.safebaiyun.unlock.DataRepo
import cn.huacheng.safebaiyun.unlock.DoorDevice
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageDoorDialog(
    state: MutableState<Boolean>,
    initialDoors: List<DoorDevice>,
    onSaved: () -> Unit,
) {
    var doors by remember { mutableStateOf(initialDoors.map { it.copy() }.toMutableList()) }
    var editingId by remember { mutableStateOf<String?>(null) }

    val editName = remember { mutableStateOf("") }
    val editMac = remember { mutableStateOf("") }
    val editKey = remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = { state.value = false }) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "管理门禁",
                    style = typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    DataRepo.saveDoors(doors.toList())
                    onSaved()
                    state.value = false
                }) {
                    Text(text = "保存")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (doors.isEmpty()) {
                Text(
                    text = "暂无门禁",
                    style = typography.bodyMedium,
                    color = colorScheme.outline,
                    modifier = Modifier.padding(vertical = 16.dp).align(Alignment.CenterHorizontally)
                )
            }

            doors.forEachIndexed { index, door ->
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

                        val newList = doors.toMutableList()
                        newList[index] = door.copy(name = name, mac = mac, key = key)
                        doors = newList
                        editingId = null
                    },
                    onDelete = {
                        // ✅ 立即删除，刷新 UI
                        val newList = doors.toMutableList()
                        newList.removeAt(index)
                        doors = newList
                        if (editingId == door.id) editingId = null
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    val newId = UUID.randomUUID().toString()
                    val newName = "门禁${doors.size + 1}"
                    val newList = doors.toMutableList()
                    newList.add(
                        DoorDevice(
                            id = newId,
                            name = newName,
                            mac = "",
                            key = ""
                        )
                    )
                    doors = newList
                    editingId = newId
                    editName.value = newName
                    editMac.value = ""
                    editKey.value = ""
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "添加门禁")
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

    CardEditorContent {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = door.name,
                    style = typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )

                if (!isEditing) {
                    IconButton(onClick = onToggleExpand, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "编辑",
                            tint = colorScheme.primary
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
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = colorScheme.error
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardEditorContent(content: @Composable () -> Unit) {
    content()
}
