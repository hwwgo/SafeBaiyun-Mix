package cn.huacheng.safebaiyun.unlock

data class DoorDevice(
    val id: String,
    val name: String,
    val mac: String,
    val key: String,
    val isSelected: Boolean = true  // 默认选中
)
