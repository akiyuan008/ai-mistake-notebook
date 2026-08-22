package com.jiancuoti.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class AlbumItem(val id: Long, val uri: Uri)

/** APP 内置相册：读取本机媒体库网格多选，支持导入 PDF（每页渲染为图片） */
@Composable
fun AlbumScreen(
    onConfirm: (List<Uri>) -> Unit,
    onImportPdf: (List<File>) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<AlbumItem>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var pdfBusy by remember { mutableStateOf(false) }
    var pdfMsg by remember { mutableStateOf("") }
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                if (android.os.Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
                else Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    // PDF 文件选择器
    val pdfScope = rememberCoroutineScope()
    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            pdfBusy = true; pdfMsg = "正在解析 PDF…"
            pdfScope.launch {
                val files = withContext(Dispatchers.IO) { renderPdfToFiles(context, uri) }
                pdfBusy = false
                if (files.isEmpty()) {
                    pdfMsg = "PDF 解析失败或无页面"
                } else {
                    onImportPdf(files)
                }
            }
        }
    }

    LaunchedEffect(granted) {
        if (!granted) {
            permLauncher.launch(
                if (android.os.Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
                else Manifest.permission.READ_EXTERNAL_STORAGE
            )
        } else {
            items = withContext(Dispatchers.IO) {
                val out = mutableListOf<AlbumItem>()
                try {
                    context.contentResolver.query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        arrayOf(MediaStore.Images.Media._ID),
                        null, null,
                        "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media.DATE_ADDED} DESC"
                    )?.use { cur ->
                        val idCol = cur.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                        var n = 0
                        while (cur.moveToNext() && n < 500) {
                            val id = cur.getLong(idCol)
                            out.add(AlbumItem(id, Uri.withAppendedPath(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())))
                            n++
                        }
                    }
                } catch (_: Exception) {}
                out
            }
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("相册", fontSize = 17.sp, modifier = Modifier.weight(1f))
            if (pdfBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp).padding(end = 8.dp),
                    strokeWidth = 2.dp
                )
                Text("PDF解析中", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 10.dp))
            } else {
                TextButton(onClick = { pdfLauncher.launch("application/pdf") }) {
                    Text("导入 PDF", fontSize = 13.sp)
                }
            }
            if (pdfMsg.isNotBlank()) {
                Text(pdfMsg, fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(end = 8.dp))
            }
            if (selected.isNotEmpty()) {
                Text("${selected.size} 张已选", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 12.dp))
            }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, null) }
        }
        if (!granted) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("未授予相册权限", color = MaterialTheme.colorScheme.outline)
            }
        } else if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("相册为空", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(items, key = { _, it -> it.id }) { _, item ->
                    val on = selected.contains(item.id)
                    Box(
                        modifier = Modifier.aspectRatio(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .border(
                                if (on) 3.dp else 1.dp,
                                if (on) SkyPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                RoundedCornerShape(18.dp)
                            )
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        AsyncImage(
                            model = item.uri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clickableNoRipple {
                                selected = if (on) selected - item.id else selected + item.id
                            },
                            contentScale = ContentScale.Crop
                        )
                        if (on) {
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(6.dp)
                                    .size(22.dp).clip(RoundedCornerShape(50))
                                    .background(SkyPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Check, null, tint = Color.White,
                                    modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
            Surface(
                Modifier.fillMaxWidth().navigationBarsPadding(),
                shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                color = glassColor(),
                border = glassBorder(),
                shadowElevation = 8.dp
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { selected = items.map { it.id }.toSet() }) {
                        Text("全选")
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = {
                            onConfirm(items.filter { selected.contains(it.id) }.map { it.uri })
                        },
                        enabled = selected.isNotEmpty(),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = SkyPrimary)
                    ) {
                        Text("导入 (${selected.size})", color = Color.White)
                    }
                }
            }
        }
    }
}

/** 用系统 PdfRenderer 把 PDF 每页渲染成图片文件 */
private fun renderPdfToFiles(context: android.content.Context, uri: Uri): List<File> {
    val out = mutableListOf<File>()
    try {
        val dir = File(context.cacheDir, "pdf_pages").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            android.graphics.pdf.PdfRenderer(pfd).use { renderer ->
                for (i in 0 until minOf(renderer.pageCount, 30)) {
                    renderer.openPage(i).use { page ->
                        val scale = 2f
                        val w = (page.width * scale).toInt().coerceAtMost(2000)
                        val h = (page.height * scale).toInt().coerceAtMost(2800)
                        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val f = File(dir, "pdf_${i}.jpg")
                        java.io.FileOutputStream(f).use {
                            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it)
                        }
                        bmp.recycle()
                        out.add(f)
                    }
                }
            }
        }
    } catch (_: Exception) {}
    return out
}
