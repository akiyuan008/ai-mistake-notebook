package com.jiancuoti.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import java.io.File

/**
 * 原生相机页：支持 单页（拍一张直接进裁剪）/ 多页（连拍收集）
 */
@Composable
fun CameraScreen(
    onSingleShot: (File) -> Unit,
    onMultiShots: (List<File>) -> Unit,
    onOpenAlbum: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    // singlePage=true：拍一张直接进框选；false：多页连拍收集
    var singlePage by remember { mutableStateOf(true) }
    var shots by remember { mutableStateOf<List<File>>(emptyList()) }
    var capturing by remember { mutableStateOf(false) }

    // 相机权限：进入页面自动申请
    var camGranted by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    var camDenied by remember { mutableStateOf(false) }
    val camLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        camGranted = granted
        if (!granted) camDenied = true
    }
    LaunchedEffect(Unit) {
        if (!camGranted) camLauncher.launch(android.Manifest.permission.CAMERA)
    }

    if (!camGranted) {
        // 权限引导页
        Box(Modifier.fillMaxSize().background(Color(0xFF0B1220)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)) {
                Icon(Icons.Default.CameraAlt, null, tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(18.dp))
                Text("需要相机权限", color = Color.White, fontSize = 17.sp)
                Spacer(Modifier.height(8.dp))
                Text("用于拍摄试卷、提取错题", color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { camLauncher.launch(android.Manifest.permission.CAMERA) },
                    shape = RoundedCornerShape(50)
                ) { Text(if (camDenied) "再次申请权限" else "授予相机权限", color = Color.White) }
                if (camDenied) {
                    Spacer(Modifier.height(10.dp))
                    Text("若多次被拒，请到系统设置中手动开启",
                        color = Color.White.copy(alpha = 0.5f), fontSize = 11.5.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
        return
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B1220))) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setJpegQuality(96)
                        .build()
                    imageCapture = capture
                    try {
                        provider.unbindAll()
                        camera = provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview, capture
                        )
                        camera?.cameraControl?.enableTorch(torchOn)
                    } catch (_: Exception) {}
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // 引导框
        Box(
            Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.66f)
                .align(Alignment.Center)
                .border(1.5.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
        ) {
            Text(
                "将试卷放入框内，保持平整",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            )
        }

        // 顶部右侧：闪光灯开关（玻璃质感）
        Box(
            Modifier.align(Alignment.TopEnd)
                .statusBarsPadding().padding(end = 16.dp, top = 10.dp)
                .size(42.dp)
                .clip(CircleShape)
                .background(if (torchOn) Amber.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.18f))
                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                .clickableNoRipple {
                    torchOn = !torchOn
                    camera?.cameraControl?.enableTorch(torchOn)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (torchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                null, tint = Color.White, modifier = Modifier.size(20.dp)
            )
        }

        // 底部控制区
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                .navigationBarsPadding().padding(bottom = 14.dp)
        ) {
            // 连拍缩略图
            if (shots.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    items(shots) { f ->
                        AsyncImage(
                            model = f,
                            contentDescription = null,
                            modifier = Modifier.size(50.dp, 66.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        )
                    }
                }
            }
            // 单页/多页切换
            Row(
                Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(4.dp)
            ) {
                TabPill("单页", singlePage) { singlePage = true }
                TabPill("多页", !singlePage) { singlePage = false }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左下角：相册入口（玻璃质感）
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                        .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                        .clickableNoRipple(onClick = onOpenAlbum)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, null, tint = Color.White,
                        modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(2.dp))
                    Text("相册", color = Color.White, fontSize = 10.5.sp)
                }
                Spacer(Modifier.weight(1f))
                // 快门
                Button(
                    onClick = {
                        if (capturing) return@Button
                        capturing = true
                        takePhoto(imageCapture, context) { file ->
                            capturing = false
                            if (file == null) return@takePhoto
                            if (singlePage) {
                                onSingleShot(file)
                            } else {
                                shots = shots + file
                            }
                        }
                    },
                    modifier = Modifier.size(76.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    contentPadding = PaddingValues(0.dp)
                ) {}
                Spacer(Modifier.weight(1f))
                // 占位，保持快门居中
                Spacer(Modifier.size(64.dp))
            }
            if (!singlePage && shots.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { onMultiShots(shots) },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                        .fillMaxWidth(0.6f),
                    colors = ButtonDefaults.buttonColors(containerColor = SkyPrimary)
                ) {
                    Text("开始框选 (${shots.size} 页)", color = Color.White)
                }
            }
            if (!singlePage) {
                Spacer(Modifier.height(6.dp))
                Text("连拍多页后统一框选，可跨页拼接", fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
private fun TabPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) Color.White else Color.Transparent)
            .clickableNoRipple(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 6.dp)
    ) {
        Text(label, fontSize = 13.sp,
            color = if (selected) Color(0xFF0B1220) else Color.White)
    }
}

@Composable
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(
        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
        indication = null,
        onClick = onClick
    ))

private fun takePhoto(
    capture: ImageCapture?,
    context: android.content.Context,
    onDone: (File?) -> Unit
) {
    if (capture == null) { onDone(null); return }
    val dir = File(context.cacheDir, "shots").apply { mkdirs() }
    val file = File(dir, "shot_${System.currentTimeMillis()}.jpg")
    val output = ImageCapture.OutputFileOptions.Builder(file).build()
    capture.takePicture(
        output,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                onDone(file)
            }
            override fun onError(exception: ImageCaptureException) {
                onDone(null)
            }
        }
    )
}

/** 读取图片并按需缩放 + EXIF 旋转 */
fun loadBitmap(file: File, maxSide: Int = 2400): Bitmap {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, opts)
    var sample = 1
    while (opts.outWidth / sample > maxSide || opts.outHeight / sample > maxSide) sample *= 2
    val bmp = BitmapFactory.decodeFile(file.absolutePath,
        BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
    return try {
        val exif = androidx.exifinterface.media.ExifInterface(file.absolutePath)
        val orient = exif.getAttributeInt(
            androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
            androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
        )
        val angle = when (orient) {
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (angle != 0f) {
            val m = Matrix().apply { postRotate(angle) }
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        } else bmp
    } catch (e: Exception) { bmp }
}
