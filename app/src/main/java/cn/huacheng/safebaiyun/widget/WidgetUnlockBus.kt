package cn.huacheng.safebaiyun.widget

import android.content.Context
import android.os.SystemClock
import cn.huacheng.safebaiyun.util.ContextHolder

/**
 * Widget 开锁请求的进程内 + 持久化通信总线。
 *
 * ColorOS 等桌面在应用冷启动时，可能不会直接把 Widget 的 Activity
 * Intent 交给 ShortcutActivity，而是先拉起 MainActivity，甚至丢失
 * Intent extra。仅使用进程内变量在这种情况下会因为进程刚创建/重建而丢失请求。
 *
 * 因此这里同时把最近一次 Widget 开锁请求写入 SharedPreferences。
 * 即使应用进程刚刚冷启动，MainActivity / ShortcutActivity 仍能取到请求。
 */
object WidgetUnlockBus {

    private const val PREFS_NAME = "widget_unlock_bus"
    private const val KEY_DOOR_ID = "pending_door_id"
    private const val KEY_TIMESTAMP = "pending_timestamp"

    /** 请求最长有效时间，避免旧请求在用户正常打开 App 时被误触发。 */
    private const val MAX_AGE_MS = 5000L

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Widget 点击时调用。
     *
     * 同时写入内存和持久化存储，兼容：
     * 1. 热启动：直接在当前进程内传递；
     * 2. 冷启动：ColorOS 先拉起 MainActivity 时仍可恢复请求。
     */
    @Synchronized
    fun requestUnlock(doorId: String) {
        val timestamp = SystemClock.elapsedRealtime()

        // 保留原有的进程内快速通道
        pendingDoorId = doorId
        pendingTimestamp = timestamp

        // 冷启动兜底通道
        prefs(ContextHolder.get()).edit()
            .putString(KEY_DOOR_ID, doorId)
            .putLong(KEY_TIMESTAMP, timestamp)
            .apply()
    }

    /**
     * 读取并消费标志。
     *
     * 注意：先保存 timestamp 再清理数据，避免原实现中先把
     * pendingTimestamp 置 0 后再进行年龄判断导致请求永远失效的问题。
     */
    @Synchronized
    fun consume(): String? {
        val now = SystemClock.elapsedRealtime()

        val memoryId = pendingDoorId
        val memoryTimestamp = pendingTimestamp

        val p = prefs(ContextHolder.get())
        val storedId = p.getString(KEY_DOOR_ID, null)
        val storedTimestamp = p.getLong(KEY_TIMESTAMP, 0L)

        // 进程内数据优先；进程被杀/重建时使用持久化数据。
        val id = memoryId ?: storedId
        val timestamp = if (memoryId != null) memoryTimestamp else storedTimestamp

        // 无论是否过期，都消费掉这次请求，避免旧请求残留。
        pendingDoorId = null
        pendingTimestamp = 0L
        p.edit()
            .remove(KEY_DOOR_ID)
            .remove(KEY_TIMESTAMP)
            .apply()

        return if (
            !id.isNullOrEmpty() &&
            timestamp > 0L &&
            now >= timestamp &&
            now - timestamp <= MAX_AGE_MS
        ) {
            id
        } else {
            null
        }
    }

    @Volatile
    private var pendingDoorId: String? = null

    @Volatile
    private var pendingTimestamp: Long = 0L
}
