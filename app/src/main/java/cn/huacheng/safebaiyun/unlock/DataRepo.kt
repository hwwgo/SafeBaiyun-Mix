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

    /** 读-改-写：对门禁列表做一次变换后保存 */
    private inline fun mutateDoors(crossinline transform: (MutableList<DoorDevice>) -> Unit) {
        val list = getDoors().toMutableList()
        transform(list)
        saveDoors(list)
    }

    private fun MutableList<DoorDevice>.indexOfId(id: String) = indexOfFirst { it.id == id }

    fun addDoor(door: DoorDevice) = mutateDoors { it.add(door) }

    fun updateDoor(door: DoorDevice) = mutateDoors { list ->
        val index = list.indexOfId(door.id)
        if (index >= 0) list[index] = door
    }

    fun deleteDoor(id: String) = mutateDoors { list ->
        list.removeAll { it.id == id }
    }

    // ---------- 切换选中状态 ----------
    fun toggleSelected(id: String) = mutateDoors { list ->
        val index = list.indexOfId(id)
        if (index >= 0) {
            list[index] = list[index].copy(isSelected = !list[index].isSelected)
        }
    }

    // ---------- 移动门禁顺序（上移/下移） ----------
    fun moveDoor(id: String, direction: Int) = mutateDoors { list ->
        val index = list.indexOfId(id)
        val newIndex = index + direction
        if (index >= 0 && newIndex in list.indices) {
            list.add(newIndex, list.removeAt(index))
        }
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
