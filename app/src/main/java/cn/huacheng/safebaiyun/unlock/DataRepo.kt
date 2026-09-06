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
            emptyList()
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
}
