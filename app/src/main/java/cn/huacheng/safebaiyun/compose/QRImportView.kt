package cn.huacheng.safebaiyun.compose

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import cn.huacheng.safebaiyun.theme.*
import cn.huacheng.safebaiyun.unlock.DataRepo
import cn.huacheng.safebaiyun.unlock.DoorDevice
import cn.huacheng.safebaiyun.util.QRCodeUtils
import cn.huacheng.safebaiyun.util.showToast
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRImportView(navController: NavHostController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember { mutableStateOf(false) }
    var scannedData by remember { mutableStateOf<String?>(null) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var importedDoors by remember { mutableStateOf<List<DoorDevice>?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            showToast("需要相机权限才能扫描二维码")
        }
    }

    LaunchedEffect(Unit) {
        hasCameraPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
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
            QRImportTopBar(onBack = { navController.popBackStack() })

            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                when {
                    showConfirmDialog && importedDoors != null -> {
                        ImportConfirmDialog(
                            doors = importedDoors!!,
                            onDismiss = {
                                showConfirmDialog = false
                                importedDoors = null
                                scannedData = null
                            },
                            onImport = { replaceAll ->
                                importDoors(importedDoors!!, replaceAll)
                                showConfirmDialog = false
                                importedDoors = null
                                scannedData = null
                                showToast("导入成功")
                                navController.popBackStack()
                            }
                        )
                    }
                    !hasCameraPermission -> {
                        CameraPermissionView(
                            onRequestPermission = {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        )
                    }
                    scannedData == null -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            CameraPreview(
                                onQrCodeDetected = { data ->
                                    if (!isProcessing) {
                                        isProcessing = true
                                        scannedData = data
                                        val decodedDoors = QRCodeUtils.decodeQRTodoors(data)
                                        if (decodedDoors != null && decodedDoors.isNotEmpty()) {
                                            importedDoors = decodedDoors
                                            showConfirmDialog = true
                                        } else {
                                            showToast("无效的二维码格式")
                                            scannedData = null
                                        }
                                        isProcessing = false
                                    }
                                }
                            )

                            ScannerOverlay()

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color.Black.copy(alpha = 0.6f)
                                    )
                                ) {
                                    Text(
                                        text = "请将二维码对准取景框",
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingState()
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QRImportTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                "扫描导入",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        },
        navigationIcon = {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        )
    )
}

@Composable
private fun CameraPermissionView(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "需要相机权限才能扫描二维码",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "授权后即可使用相机扫描二维码导入门禁配置",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 修复：去掉阴影
        Box(
            modifier = Modifier
                .height(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(ColorOSGradientStart, ColorOSGradientEnd)
                    )
                )
                .clickable(onClick = onRequestPermission),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "授予相机权限",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

@Composable
private fun ScannerOverlay() {
    val primaryColor = ColorOSPrimary

    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        val overlaySize = minOf(canvasWidth, canvasHeight) * 0.7f
        val left = (canvasWidth - overlaySize) / 2
        val top = (canvasHeight - overlaySize) / 2

        drawRect(
            color = Color.Black.copy(alpha = 0.5f),
            size = Size(canvasWidth, canvasHeight)
        )

        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = Size(overlaySize, overlaySize),
            cornerRadius = CornerRadius(24.dp.toPx()),
            blendMode = androidx.compose.ui.graphics.BlendMode.Clear
        )

        drawRoundRect(
            color = primaryColor,
            topLeft = Offset(left, top),
            size = Size(overlaySize, overlaySize),
            cornerRadius = CornerRadius(24.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
        )

        val cornerLength = 40.dp.toPx()
        val cornerWidth = 6.dp.toPx()

        drawLine(
            color = Color.White,
            start = Offset(left, top + cornerLength),
            end = Offset(left, top),
            strokeWidth = cornerWidth
        )
        drawLine(
            color = Color.White,
            start = Offset(left, top),
            end = Offset(left + cornerLength, top),
            strokeWidth = cornerWidth
        )

        drawLine(
            color = Color.White,
            start = Offset(left + overlaySize - cornerLength, top),
            end = Offset(left + overlaySize, top),
            strokeWidth = cornerWidth
        )
        drawLine(
            color = Color.White,
            start = Offset(left + overlaySize, top),
            end = Offset(left + overlaySize, top + cornerLength),
            strokeWidth = cornerWidth
        )

        drawLine(
            color = Color.White,
            start = Offset(left, top + overlaySize - cornerLength),
            end = Offset(left, top + overlaySize),
            strokeWidth = cornerWidth
        )
        drawLine(
            color = Color.White,
            start = Offset(left, top + overlaySize),
            end = Offset(left + cornerLength, top + overlaySize),
            strokeWidth = cornerWidth
        )

        drawLine(
            color = Color.White,
            start = Offset(left + overlaySize - cornerLength, top + overlaySize),
            end = Offset(left + overlaySize, top + overlaySize),
            strokeWidth = cornerWidth
        )
        drawLine(
            color = Color.White,
            start = Offset(left + overlaySize, top + overlaySize - cornerLength),
            end = Offset(left + overlaySize, top + overlaySize),
            strokeWidth = cornerWidth
        )
    }
}

@Composable
private fun ImportConfirmDialog(
    doors: List<DoorDevice>,
    onDismiss: () -> Unit,
    onImport: (Boolean) -> Unit
) {
    var replaceAll by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ColorOSPrimary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.QrCode,
                        contentDescription = null,
                        tint = ColorOSPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "发现门禁配置",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column {
                Text(
                    "即将导入以下 ${doors.size} 个门禁：",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        doors.take(5).forEach { door ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(ColorOSPrimary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = door.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        if (doors.size > 5) {
                            Text(
                                text = "... 还有 ${doors.size - 5} 个",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "导入方式：",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(8.dp))

                RadioOption(
                    selected = replaceAll,
                    onClick = { replaceAll = true },
                    text = "替换全部配置"
                )

                Spacer(modifier = Modifier.height(4.dp))

                RadioOption(
                    selected = !replaceAll,
                    onClick = { replaceAll = false },
                    text = "追加到现有配置"
                )
            }
        },
        confirmButton = {
            // 修复：去掉阴影，使用 Button
            Button(
                onClick = { onImport(replaceAll) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ColorOSPrimary
                )
            ) {
                Text(
                    "确认导入",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "取消",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    )
}

@Composable
private fun RadioOption(
    selected: Boolean,
    onClick: () -> Unit,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) 
                    ColorOSPrimary.copy(alpha = 0.1f) 
                else 
                    Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = ColorOSPrimary
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) 
                MaterialTheme.colorScheme.onSurface 
            else 
                MaterialTheme.colorScheme.onSurfaceVariant
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
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "正在处理...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CameraPreview(onQrCodeDetected: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                            processImageProxy(imageProxy, onQrCodeDetected)
                        }
                    }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalyzer
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(context))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

private fun processImageProxy(
    imageProxy: androidx.camera.core.ImageProxy,
    onQrCodeDetected: (String) -> Unit
) {
    val mediaImage = imageProxy.image ?: run {
        imageProxy.close()
        return
    }

    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    val scanner = BarcodeScanning.getClient()

    scanner.process(inputImage)
        .addOnSuccessListener { barcodes ->
            for (barcode in barcodes) {
                if (barcode.format == Barcode.FORMAT_QR_CODE) {
                    barcode.rawValue?.let { value ->
                        onQrCodeDetected(value)
                    }
                }
            }
        }
        .addOnFailureListener { e ->
            e.printStackTrace()
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}

private fun importDoors(newDoors: List<DoorDevice>, replaceAll: Boolean) {
    if (replaceAll) {
        DataRepo.saveDoors(newDoors)
    } else {
        val existingDoors = DataRepo.getDoors().toMutableList()
        val renumberedDoors = newDoors.map { door ->
            door.copy(id = UUID.randomUUID().toString())
        }
        existingDoors.addAll(renumberedDoors)
        DataRepo.saveDoors(existingDoors)
    }
}
