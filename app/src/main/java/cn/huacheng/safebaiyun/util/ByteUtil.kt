package cn.huacheng.safebaiyun.util

/**
 *
 *@description:
 *@author: guangzhou
 *@create: 2024-03-04
 */
object ByteUtil {

    fun hexToBytes(hexString: String): ByteArray {
        if (hexString.isBlank()) return byteArrayOf()
        val result = ByteArray(hexString.length / 2)
        for (i in result.indices) {
            val hi = Character.digit(hexString[i * 2], 16)
            val lo = Character.digit(hexString[i * 2 + 1], 16)
            result[i] = ((hi shl 4) or lo).toByte()
        }
        return result
    }

}

