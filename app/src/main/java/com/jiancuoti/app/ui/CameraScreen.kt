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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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

@Composable
fun CameraScreen(
    onShotsTaken: (List<File>) -> Unit,
    onOpenAlbum: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var flashOn by remember { mutableStateOf(false) }
    var shots by remember { mutableStateOf<List<File>>(emptyList()) }
    var capturing by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
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
            Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.68f)
                .align(Alignment.Center)
                .border(1.5.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
        ) {
            Text(
                "将试卷放入框内，保持平整",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                    .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 3.dp)
            )
        }

        // 顶部栏
        Row(
            Modifier.fillMaxWidth().align(Alignment.TopCenter)
                .statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, null, tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            Text("拍摄试卷", color = Color.White, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { flashOn = !flashOn }) {
                Icon(
                    if (flashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    null, tint = Color.White
                )
            }
        }

        // 底部控制
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .navigationBarsPadding().padding(bottom = 12.dp)
        ) {
            if (shots.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(shots) { f ->
                        AsyncImage(
                            model = f,
                            contentDescription = null,
                            modifier = Modifier.size(52.dp, 68.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = onOpenAlbum) {
                            Icon(
                                Icons.Default.PhotoLibrary, null,
                                tint = Color.White,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White.copy(alpha = 0.14f))
                                    .padding(10.dp)
                            )
                        }
                        Text("相册", color = Color.White, fontSize = 10.sp)
                    }
                }
                // 快门
                Button(
                    onClick = {
                        if (capturing) return@Button
                        capturing = true
                        takePhoto(imageCapture, context) { file ->
                            if (file != null) shots = shots + file
                            capturing = false
                        }
                    },
                    modifier = Modifier.size(74.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    contentPadding = PaddingValues(0.dp)
                ) {}
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (shots.isNotEmpty()) {
                        Button(
                            onClick = { onShotsTaken(shots) },
                            colors = ButtonDefaults.buttonColors(containerColor = SkyPrimary)
                        ) {
                            Text("完成(${shots.size})", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

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

/** 读取图片文件并按需旋转/缩放为 Bitmap（相机图通常较大） */
fun loadBitmap(file: File, maxSide: Int = 2400): Bitmap {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, opts)
    var sample = 1
    while (opts.outWidth / sample > maxSide || opts.outHeight / sample > maxSide) sample *= 2
    val bmp = BitmapFactory.decodeFile(file.absolutePath,
        BitmapFactory.Options().apply { inSampleSize = sample }) ?: return Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
    // 处理 EXIF 旋转
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
