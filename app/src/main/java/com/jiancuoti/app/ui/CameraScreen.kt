package com.jiancuoti.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
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
import androidx.compose.material.icons.filled.Close
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
    var singleMode by remember { mutableStateOf(false) }
    var shots by remember { mutableStateOf<List<File>>(emptyList()) }
    var capturing by remember { mutableStateOf(false) }

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
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    imageCapture = capture
                    try {
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview, capture
                        )
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
                .border(1.5.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
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

        // 顶部：相册入口
        Row(
            Modifier.fillMaxWidth().align(Alignment.TopCenter)
                .statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickableNoRipple(onClick = onOpenAlbum)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.PhotoLibrary, null, tint = Color.White,
                    modifier = Modifier.size(20.dp))
                Text("相册", color = Color.White, fontSize = 10.5.sp)
            }
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
                                .clip(RoundedCornerShape(8.dp))
                                .border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
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
                TabPill("单页", !singleMode) { singleMode = false }
                TabPill("多页", singleMode) { singleMode = true }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.weight(1f))
                // 快门
                Button(
                    onClick = {
                        if (capturing) return@Button
                        capturing = true
                        takePhoto(imageCapture, context) { file ->
                            capturing = false
                            if (file == null) return@takePhoto
                            if (singleMode) {
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
            }
            if (!singleMode && shots.isNotEmpty()) {
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
            if (singleMode) {
                Spacer(Modifier.height(6.dp))
                Text("拍摄后直接进入框选", fontSize = 11.sp,
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
