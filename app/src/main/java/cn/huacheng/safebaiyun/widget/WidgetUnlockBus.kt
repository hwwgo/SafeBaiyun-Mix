package cn.huacheng.safebaiyun.widget

import android.content.Context

/**
 * Widget 开锁请求通信总线。
 *
 * ColorOS 冷启动时可能先启动 MainActivity，甚至导致 Widget ActionCallback
 * 与目标 Activity 不在同一个进程生命周期中。仅使用 Kotlin object 的内存字段
 * 不可靠，因此这里同时把请求持久化到 SharedPreferences。
 *
 * requestUnlock() 使用 commit()，确保 startActivity() 之前请求已经落盘。
 */
object WidgetUnlockBus {

    private const val PREFS_NAME = "widget_unlock_bus"
    private const val KEY_DOOR_ID = "pending_door_id"
    private const val KEY_TIMESTAMP = "pending_timestamp"
    private const val MAX_AGE_MS = 15_000L

    @Volatile
    private var pendingDoorId: String? = null

    @Volatile
    private var pendingTimestamp: Long = 0L

    /**
     * Widget 点击时调用。
     *
     * 必须在启动 Activity 前同步写入 SharedPreferences，
     * 这样即使 ColorOS 冷启动时重建进程，MainActivity 仍能拿到请求。
     */
    fun requestUnlock(context: Context, doorId: String) {
        val now = System.currentTimeMillis()

        pendingDoorId = doorId
        pendingTimestamp = now

        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DOOR_ID, doorId)
            .putLong(KEY_TIMESTAMP, now)
            .commit()
    }

    /**
     * 读取并消费待处理请求。
     *
     * 先保存 timestamp 再清除，避免原实现中：
     * pendingTimestamp = 0 后再计算时间差，导致请求永远被判定为过期。
     */
    @Synchronized
    fun consume(context: Context): String? {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val persistedId = prefs.getString(KEY_DOOR_ID, null)
        val persistedTimestamp = prefs.getLong(KEY_TIMESTAMP, 0L)

        val id = persistedId ?: pendingDoorId
        val timestamp = if (persistedTimestamp != 0L) {
            persistedTimestamp
        } else {
            pendingTimestamp
        }

        val valid = id != null &&
                timestamp > 0L &&
                System.currentTimeMillis() - timestamp in 0L..MAX_AGE_MS

        // 无论是否有效，都只消费一次。
        prefs.edit()
            .remove(KEY_DOOR_ID)
            .remove(KEY_TIMESTAMP)
            .commit()

        pendingDoorId = null
        pendingTimestamp = 0L

        return if (valid) id else null
    }
}
