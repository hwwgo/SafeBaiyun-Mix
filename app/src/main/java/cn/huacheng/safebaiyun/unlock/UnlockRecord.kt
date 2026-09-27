package cn.huacheng.safebaiyun.unlock

data class UnlockRecord(
    val id: Long,
    val doorName: String,
    val timestamp: Long,
    val success: Boolean,
    val durationMs: Long,
    val result: String
)
