package cn.huacheng.safebaiyun.unlock

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import cn.huacheng.safebaiyun.util.ContextHolder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object DataRepo {

    private val preferences: SharedPreferences by lazy {
        ContextHolder.get().getSharedPreferences("data", Context.MODE_PRIVATE)
    }
    private val gson = Gson()

    /** 迁移标记（只执行一次） */
    private var migrated = false

    // ---------- 兼容旧版单门禁 ----------
    fun readData(): Pair<String, String> {
        val mac = preferences.getString("mac", "") ?: ""
        val key = preferences.getString("key", "") ?: ""
        return mac to key
    }

    fun save(mac: String, key: String) {
        preferences.edit {
            putString("mac", mac)
            putString("key", key)
        }
    }

    // ---------- 多门禁管理 ----------
    fun getDoors(): List<DoorDevice> {
        val json = preferences.getString("doors", "[]") ?: "[]"
        val type = object : TypeToken<List<DoorDevice>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            // 解析失败时尝试迁移旧数据
            migrateIfNeeded()
            // 重新尝试读取
            try {
                gson.fromJson(json, type) ?: emptyList()
            } catch (e2: Exception) {
                emptyList()
            }
        }
    }

    fun saveDoors(doors: List<DoorDevice>) {
        val json = gson.toJson(doors)
        preferences.edit {
            putString("doors", json)
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

    // ---------- 获取选中的门禁（用于轮询） ----------
    fun getSelectedDoors(): List<DoorDevice> {
        return getDoors().filter { it.isSelected }
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

    // ---------- 数据迁移：将旧格式（id 为 Int）转换为新格式（id 为 String） ----------
    fun migrateIfNeeded() {
        if (migrated) return
        val json = preferences.getString("doors", null) ?: run {
            migrated = true
            return
        }
        // 尝试解析为旧格式（id 为 Int）
        val oldType = object : TypeToken<List<OldDoorDevice>>() {}.type
        val oldList: List<OldDoorDevice>? = try {
            gson.fromJson(json, oldType)
        } catch (e: Exception) {
            null
        }
        if (oldList != null && oldList.isNotEmpty()) {
            // 转换为新格式（id 为 String）
            val newList = oldList.map { old ->
                DoorDevice(
                    id = old.id.toString(),
                    name = old.name,
                    mac = old.mac,
                    key = old.key,
                    isSelected = old.isSelected ?: true
                )
            }
            saveDoors(newList)
            migrated = true
        } else {
            // 如果解析失败，可能是新格式或无数据，标记已完成
            migrated = true
        }
    }

    // ---------- 旧门禁数据类，仅用于迁移 ----------
    private data class OldDoorDevice(
        val id: Int,
        val name: String,
        val mac: String,
        val key: String,
        val isSelected: Boolean? = true
    )
}
