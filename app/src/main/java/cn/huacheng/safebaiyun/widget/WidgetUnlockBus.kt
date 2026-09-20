package cn.huacheng.safebaiyun.widget

import android.os.SystemClock

/**
 * Widget 开锁请求的进程内通信总线。
 *
 * 背景：ColorOS 等国产 ROM 在冷启动时，会拦截从 widget 启动的 Activity，
 *      强行先拉起 MainActivity，且**丢失 Intent extra**。
 *      因此用进程内的全局标志传递"刚才用户点了 widget 要开哪个门"。
 *
 * 使用方式：
 *   1. Widget 点击 → requestUnlock(doorId)
 *   2. MainActivity / ShortcutActivity 启动时 → consume() 读取
 *   3. 只有 5 秒内的请求有效，超时自动作废
 */
object WidgetUnlockBus {

    private const val MAX_AGE_MS = 5000L

    @Volatile
    private var pendingDoorId: String? = null

    @Volatile
    private var pendingTimestamp: Long = 0L

    /** Widget 点击时调用，记录待处理的门禁 ID */
    fun requestUnlock(doorId: String) {
        pendingDoorId = doorId
        pendingTimestamp = SystemClock.elapsedRealtime()
    }

    /**
     * 读取并消费标志。返回非 null 表示"确实是 widget 触发的"。
     * 原子操作，只有一个调用方会拿到结果。
     */
    @Synchronized
    fun consume(): String? {
        val now = SystemClock.elapsedRealtime()
        val id = pendingDoorId

        // 清空状态（无论是否有效，避免残留）
        pendingDoorId = null
        pendingTimestamp = 0L

        return if (id != null && now - pendingTimestamp <= MAX_AGE_MS) id else null
    }

    /** 只读不消费（调试用，业务一般不用） */
    @Synchronized
    fun peek(): String? {
        val now = SystemClock.elapsedRealtime()
        val id = pendingDoorId
        return if (id != null && now - pendingTimestamp <= MAX_AGE_MS) id else null
    }
}
