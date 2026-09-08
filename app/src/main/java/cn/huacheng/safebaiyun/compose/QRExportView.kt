package cn.huacheng.safebaiyun.compose

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import cn.huacheng.safebaiyun.theme.*
import cn.huacheng.safebaiyun.unlock.DataRepo
import cn.huacheng.safebaiyun.util.QRCodeUtils
import cn.huacheng.safebaiyun.util.showToast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRExportView(navController: NavHostController) {
    val context = LocalContext.current

    var qrBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var showSavedMessage by remember { mutableStateOf(false) }
    val doors = remember { DataRepo.getDoors() }

    LaunchedEffect(doors) {
        if (doors.isNotEmpty()) {
            try {
                val encodedData = QRCodeUtils.encodeDoorsToQR(doors)
                qrBitmap = QRCodeUtils.generateQRCode(encodedData, 512)
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("生成二维码失败")
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )
            )
    ) {
        Column {
            QRExportTopBar(onBack = { navController.popBackStack() })

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    qrBitmap != null -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            QRCard(qrBitmap!!)

                            Spacer(modifier = Modifier.height(20.dp))

                            Text(
                                text = "已配置 ${doors.size} 个门禁",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "请在新设备上打开应用扫描此二维码",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(28.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                ActionButton(
                                    onClick = {
                                        qrBitmap?.let { bitmap ->
                                            if (QRCodeUtils.saveQRCodeToGallery(context, bitmap)) {
                                                showToast("已保存到相册")
                                                showSavedMessage = true
                                            } else {
                                                showToast("保存失败")
                                            }
                                        }
                                    },
                                    icon = Icons.Default.Download,
                                    text = "保存到相册",
                                    modifier = Modifier.weight(1f)
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                ActionButton(
                                    onClick = {
                                        qrBitmap?.let { bitmap ->
                                            shareQRCode(context, bitmap)
                                        }
                                    },
                                    icon = Icons.Default.Share,
                                    text = "分享",
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            if (showSavedMessage) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = ColorOSSuccess,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "已保存到相册",
                                        color = ColorOSSuccess,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                    doors.isEmpty() -> {
                        EmptyState(
                            icon = Icons.Default.QrCode,
                            message = "暂无门禁配置，请先添加门禁"
                        )
                    }
                    else -> {
                        LoadingState()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QRExportTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                "导出配置",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        },
        navigationIcon = {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        )
    )
}

@Composable
private fun QRCard(bitmap: android.graphics.Bitmap) {
    Card(
        modifier = Modifier.size(240.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "门禁配置二维码",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun ActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(ColorOSGradientStart, ColorOSGradientEnd)
                )
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun EmptyState(
    icon: ImageVector,
    message: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LoadingState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = ColorOSPrimary,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "正在生成二维码...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun shareQRCode(context: Context, bitmap: android.graphics.Bitmap) {
    try {
        val cachePath = context.cacheDir
        val file = java.io.File(cachePath, "door_config_qr.png")
        file.delete()

        val stream = java.io.FileOutputStream(file)
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        stream.flush()
        stream.close()

        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "分享门禁配置"))
    } catch (e: Exception) {
        e.printStackTrace()
        showToast("分享失败")
    }
}
