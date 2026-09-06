package cn.huacheng.safebaiyun.unlock

import kotlinx.serialization.Serializable

@Serializable
data class DoorDevice(
    val id: String,
    val name: String,
    val mac: String,
    val key: String,
    val isSelected: Boolean = true
)
