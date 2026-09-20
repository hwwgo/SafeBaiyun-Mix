package cn.huacheng.safebaiyun.widget

import android.os.SystemClock

/**
 * Widget 开锁请求的进程内通信总线。
 *
 * 背景：ColorOS 等国产 ROM 在冷启动时，会拦截从 widget 启动的 Activity，
 *      强行先拉起 MainActivity，且**丢失 Intent extra**。
 *      因此用进程内的全局标志传递"刚才用户点了 widget 要开哪个门"。
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
     */
    @Synchronized
    fun consume(): String? {
        val now = SystemClock.elapsedRealtime()
        val id = pendingDoorId

        pendingDoorId = null
        pendingTimestamp = 0L

        return if (id != null && now - pendingTimestamp <= MAX_AGE_MS) id else null
    }
}
