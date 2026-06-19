package com.shenlong.sports.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.shenlong.sports.ui.theme.DragonGreen
import com.shenlong.sports.ui.theme.DragonOrange
import com.shenlong.sports.ui.theme.DragonRed
import com.shenlong.sports.util.QrCodeGenerator
import com.shenlong.sports.util.VibrationHelper
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

/**
 * 二维码扫描记圈页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScanScreen(
    athleteNumbers: List<String>,
    onLapRecorded: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val vibrationHelper = remember { VibrationHelper(context) }

    var hasCameraPermission by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf<String?>(null) }
    var lastScannedNumber by remember { mutableStateOf<String?>(null) }
    var lastScanTime by remember { mutableLongStateOf(0L) }
    var scanCount by remember { mutableIntStateOf(0) }
    var autoMode by remember { mutableStateOf(true) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var confirmNumber by remember { mutableStateOf<String?>(null) }

    // 大号码水印闪现
    var flashNumber by remember { mutableStateOf<String?>(null) }

    // 冷却时间5秒
    val cooldownMs = 5000L

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    // 检查相机权限
    DisposableEffect(Unit) {
        hasCameraPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
        onDispose { }
    }

    // 大号码自动消失
    LaunchedEffect(flashNumber) {
        if (flashNumber != null) {
            delay(500) // 显示0.5秒后自动消失
            flashNumber = null
        }
    }

    // 处理扫描结果
    fun handleScan(content: String) {
        val number = QrCodeGenerator.parseAthleteNumber(content) ?: return
        if (number !in athleteNumbers) return

        val now = System.currentTimeMillis()
        if (number == lastScannedNumber && (now - lastScanTime) < cooldownMs) return

        lastScannedNumber = number
        lastScanTime = now
        scanCount++

        // 震动反馈
        vibrationHelper.vibrateLap()

        // 大号码闪现
        flashNumber = number

        if (autoMode) {
            onLapRecorded(number)
            scanResult = "$number 号 - 已记圈"
        } else {
            confirmNumber = number
            showConfirmDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("扫码记圈", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DragonRed,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 摄像头预览区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (hasCameraPermission) {
                    CameraPreview(
                        onQrCodeScanned = { content ->
                            handleScan(content)
                        }
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("需要相机权限才能扫码", color = Color.White, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(containerColor = DragonRed)
                        ) {
                            Text("授予权限")
                        }
                    }
                }

                // 扫描框叠加层
                ScanOverlay()

                // 大号码水印闪现
                if (flashNumber != null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        // 半透明背景
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f))
                        )
                        // 大号码
                        Text(
                            text = flashNumber ?: "",
                            fontSize = 160.sp,
                            fontWeight = FontWeight.Black,
                            color = DragonRed,
                            modifier = Modifier
                                .align(Alignment.Center)
                        )
                        // "已记圈" 标签
                        Text(
                            text = "已记圈",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 60.dp)
                        )
                    }
                }
            }

            // 底部控制区
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // 扫描结果
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = scanResult ?: "等待扫描...",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (scanResult != null) DragonGreen else Color.Gray
                        )
                        Text(
                            text = "已识别: $scanCount",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 自动/确认模式切换
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("自动记圈", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Switch(
                            checked = autoMode,
                            onCheckedChange = { autoMode = it },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                    Text(
                        text = if (autoMode) "扫描后自动记圈（5秒冷却）" else "扫描后需确认才记圈",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 提示
                    Text(
                        text = "将号码布上的二维码对准扫描框，保持稳定1-2秒",
                        fontSize = 12.sp,
                        color = DragonOrange
                    )
                }
            }
        }
    }

    // 确认记圈对话框
    if (showConfirmDialog && confirmNumber != null) {
        AlertDialog(
            onDismissRequest = {
                showConfirmDialog = false
                scanResult = "$confirmNumber 号 - 已取消"
            },
            title = { Text("确认记圈") },
            text = { Text("${confirmNumber}号运动员记一圈？") },
            confirmButton = {
                Button(
                    onClick = {
                        onLapRecorded(confirmNumber!!)
                        scanResult = "$confirmNumber 号 - 已记圈"
                        showConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DragonGreen)
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("确认")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        scanResult = "$confirmNumber 号 - 已取消"
                        showConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                ) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("取消")
                }
            }
        )
    }
}

/**
 * CameraX 摄像头预览 + ZXing 二维码识别
 */
@Composable
private fun CameraPreview(
    onQrCodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var lastScanTime by remember { mutableLongStateOf(0L) }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdownNow()
            try { cameraProviderFuture.get().unbindAll() } catch (_: Exception) { }
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val reader = MultiFormatReader()

            imageAnalysis.setAnalyzer(executor) { imageProxy ->
                try {
                    val buffer = imageProxy.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)

                    val source = PlanarYUVLuminanceSource(
                        bytes,
                        imageProxy.width,
                        imageProxy.height,
                        0, 0,
                        imageProxy.width,
                        imageProxy.height,
                        false
                    )

                    val bitmap = BinaryBitmap(HybridBinarizer(source))
                    val result = reader.decode(bitmap)

                    if (result != null) {
                        val now = System.currentTimeMillis()
                        if (now - lastScanTime > 200) {
                            lastScanTime = now
                            onQrCodeScanned(result.text)
                        }
                    }
                } catch (_: Exception) {
                    // 识别失败是正常的
                } finally {
                    imageProxy.close()
                }
            }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * 扫描框叠加层
 */
@Composable
private fun ScanOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val scanBoxSize = minOf(canvasWidth, canvasHeight) * 0.6f
        val left = (canvasWidth - scanBoxSize) / 2
        val top = (canvasHeight - scanBoxSize) / 2

        // 半透明遮罩
        drawRect(Color.Black.copy(alpha = 0.5f))

        // 扫描框区域透明
        drawRect(
            Color.Transparent,
            topLeft = Offset(left, top),
            size = Size(scanBoxSize, scanBoxSize),
            blendMode = androidx.compose.ui.graphics.BlendMode.Clear
        )

        // 扫描框边框
        val strokeWidth = 3.dp.toPx()
        val cornerLength = 24.dp.toPx()
        val cornerRadius = 8.dp.toPx()

        // 四个角
        val corners = listOf(
            listOf(Offset(left, top + cornerLength), Offset(left, top), Offset(left + cornerLength, top)),
            listOf(Offset(left + scanBoxSize - cornerLength, top), Offset(left + scanBoxSize, top), Offset(left + scanBoxSize, top + cornerLength)),
            listOf(Offset(left, top + scanBoxSize - cornerLength), Offset(left, top + scanBoxSize), Offset(left + cornerLength, top + scanBoxSize)),
            listOf(Offset(left + scanBoxSize - cornerLength, top + scanBoxSize), Offset(left + scanBoxSize, top + scanBoxSize), Offset(left + scanBoxSize, top + scanBoxSize - cornerLength))
        )

        corners.forEach { points ->
            for (i in 0 until points.size - 1) {
                drawLine(
                    Color.White,
                    start = points[i],
                    end = points[i + 1],
                    strokeWidth = strokeWidth * 2,
                    pathEffect = PathEffect.cornerPathEffect(cornerRadius)
                )
            }
        }
    }
}
