package cn.huacheng.safebaiyun.unlock

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import cn.huacheng.safebaiyun.util.ContextHolder
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object DataRepo {

    private val preferences: SharedPreferences by lazy {
        ContextHolder.get().getSharedPreferences("data", Context.MODE_PRIVATE)
    }
    private val json = Json { ignoreUnknownKeys = true }

    // ---------- 多门禁管理 ----------
    fun getDoors(): List<DoorDevice> {
        // 先尝试从新键读取
        val storedJson = preferences.getString("doors", null)
        if (storedJson != null) {
            return runCatching {
                json.decodeFromString<List<DoorDevice>>(storedJson)
            }.getOrElse { emptyList() }
        }
        
        // 如果新键没有数据，尝试从旧键 "doors_json" 读取并迁移
        val oldJson = preferences.getString("doors_json", null)
        if (oldJson != null) {
            val oldList: List<OldDoorDevice>? = runCatching {
                json.decodeFromString<List<OldDoorDevice>>(oldJson)
            }.getOrNull()
            if (oldList != null && oldList.isNotEmpty()) {
                val newList = oldList.map { old ->
                    DoorDevice(
                        id = old.id.toString(),
                        name = old.name,
                        mac = old.mac,
                        key = old.key,
                        isSelected = true
                    )
                }
                saveDoors(newList)
                // 迁移后删除旧键
                preferences.edit { remove("doors_json") }
                return newList
            }
        }
        
        return emptyList()
    }

    fun saveDoors(doors: List<DoorDevice>) {
        val encoded = json.encodeToString(doors)
        preferences.edit {
            putString("doors", encoded)
        }
    }

    fun addDoor(door: DoorDevice) {
        val list = getDoors().toMutableList()
        list.add(door)
        saveDoors(list)
    }

    fun updateDoor(door: DoorDevice) {
        val list = getDoors().toMutableList()
        val index = list.indexOfFirst { it.id == door.id }
        if (index >= 0) list[index] = door
        saveDoors(list)
    }

    fun deleteDoor(id: String) {
        val list = getDoors().filter { it.id != id }
        saveDoors(list)
    }


    // ---------- 切换选中状态 ----------
    fun toggleSelected(id: String) {
        val list = getDoors().toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index >= 0) {
            list[index] = list[index].copy(isSelected = !list[index].isSelected)
            saveDoors(list)
        }
    }

    // ---------- 移动门禁顺序（上移/下移） ----------
    fun moveDoor(id: String, direction: Int) {
        val list = getDoors().toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return
        val newIndex = index + direction
        if (newIndex < 0 || newIndex >= list.size) return
        val item = list.removeAt(index)
        list.add(newIndex, item)
        saveDoors(list)
    }

    // ---------- 旧门禁数据类，仅用于迁移 ----------
    @Serializable
    private data class OldDoorDevice(
        val id: Int,
        val name: String,
        val mac: String,
        val key: String
    )
}
