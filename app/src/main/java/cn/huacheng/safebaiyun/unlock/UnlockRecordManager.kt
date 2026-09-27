package cn.huacheng.safebaiyun.unlock

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

object UnlockRecordManager {
    private const val PREFS_NAME = "unlock_records"
    private const val KEY_RECORDS = "records"
    private const val MAX_RECORDS = 500

    private lateinit var prefs: SharedPreferences
    private val _records = MutableStateFlow<List<UnlockRecord>>(emptyList())
    val records: StateFlow<List<UnlockRecord>> = _records

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _records.value = loadRecords()
    }

    @Synchronized
    fun addRecord(
        doorName: String,
        success: Boolean,
        durationMs: Long,
        result: String
    ) {
        if (!::prefs.isInitialized || !cn.huacheng.safebaiyun.util.ConfigManager.isUnlockRecordEnabled()) return

        val record = UnlockRecord(
            id = System.currentTimeMillis(),
            doorName = doorName.ifBlank { "未知门禁" },
            timestamp = System.currentTimeMillis(),
            success = success,
            durationMs = durationMs.coerceAtLeast(0L),
            result = result
        )

        val updated = (listOf(record) + _records.value).take(MAX_RECORDS)
        _records.value = updated
        saveRecords(updated)
    }

    @Synchronized
    fun clearRecords() {
        if (!::prefs.isInitialized) return
        _records.value = emptyList()
        prefs.edit().remove(KEY_RECORDS).apply()
    }

    private fun loadRecords(): List<UnlockRecord> {
        val raw = prefs.getString(KEY_RECORDS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(
                        UnlockRecord(
                            id = item.optLong("id"),
                            doorName = item.optString("doorName", "未知门禁"),
                            timestamp = item.optLong("timestamp"),
                            success = item.optBoolean("success", false),
                            durationMs = item.optLong("durationMs", 0L),
                            result = item.optString("result", "未知")
                        )
                    )
                }
            }.sortedByDescending { it.timestamp }.take(MAX_RECORDS)
        }.getOrDefault(emptyList())
    }

    private fun saveRecords(records: List<UnlockRecord>) {
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject().apply {
                    put("id", record.id)
                    put("doorName", record.doorName)
                    put("timestamp", record.timestamp)
                    put("success", record.success)
                    put("durationMs", record.durationMs)
                    put("result", record.result)
                }
            )
        }
        prefs.edit().putString(KEY_RECORDS, array.toString()).apply()
    }
}
