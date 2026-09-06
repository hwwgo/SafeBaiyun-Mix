package cn.huacheng.safebaiyun.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import cn.huacheng.safebaiyun.unlock.DoorDevice
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object QRCodeUtils {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true  // 确保 isSelected 也被序列化
    }

    private const val PREFIX = "SBY|"

    fun encodeDoorsToQR(doors: List<DoorDevice>): String {
        return try {
            val jsonStr = json.encodeToString(doors)
            val encoded = Base64.encodeToString(jsonStr.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            "$PREFIX$encoded"
        } catch (e: Exception) {
            e.printStackTrace()
            throw IllegalStateException("序列化门禁数据失败: ${e.message}", e)
        }
    }

    fun decodeQRTodoors(data: String): List<DoorDevice>? {
        if (!data.startsWith(PREFIX)) {
            return null
        }
        return try {
            val base64Data = data.removePrefix(PREFIX)
            val jsonString = String(Base64.decode(base64Data, Base64.DEFAULT), Charsets.UTF_8)
            json.decodeFromString<List<DoorDevice>>(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun generateQRCode(data: String, size: Int = 512): Bitmap {
        val hints = mapOf<EncodeHintType, Any>(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 1,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H
        )

        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, size, size, hints)

        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }

        return bitmap
    }

    fun saveQRCodeToGallery(context: Context, bitmap: Bitmap): Boolean {
        return try {
            val filename = "door_config_${System.currentTimeMillis()}.png"

            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(
                    android.provider.MediaStore.Images.Media.DATE_ADDED,
                    System.currentTimeMillis() / 1000
                )
            }

            val uri = context.contentResolver.insert(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: return false

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.flush()
            } ?: return false

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
