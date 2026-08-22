package com.jiancuoti.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.jiancuoti.app.data.Mistake
import com.jiancuoti.app.data.Store
import com.jiancuoti.app.img.Enhance
import com.jiancuoti.app.img.Perspective
import com.jiancuoti.app.net.AiParser
import com.jiancuoti.app.net.Supabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** 工作台页面（拍摄/相册/文件入口 + 已导入页面管理 + 解析队列） */
@Composable
fun ImportScreen(onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 已导入的页面（原图文件）
    var pages by remember { mutableStateOf<List<File>>(emptyList()) }
    var curPage by remember { mutableStateOf(0) }
    var showCamera by remember { mutableStateOf(false) }
    var showAlbum by remember { mutableStateOf(false) }
    var cropPageIndex by remember { mutableStateOf(-1) }
    var toast by remember { mutableStateOf("") }
    var queue by remember { mutableStateOf<List<QueueItem>>(emptyList()) }

    // 权限
    var pendingCamera by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingCamera) showCamera = true
        else if (!granted) toast = "未授予相机权限"
        pendingCamera = false
    }

    // 相册选择（系统多选，原生流畅）
    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val files = uris.mapNotNull { uri -> copyUriToCache(context, uri) }
                pages = pages + files
                curPage = pages.size - 1
                toast = "已导入 ${files.size} 页，点页面开始框选"
            }
        }
    }

    if (toast.isNotBlank()) {
        LaunchedEffect(toast) { kotlinx.coroutines.delay(2400); toast = "" }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Surface(Modifier.padding(top = 70.dp), shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.inverseSurface) {
                Text(toast, color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp), fontSize = 13.sp)
            }
        }
    }

    // 相机全屏
    if (showCamera) {
        CameraScreen(
            onShotsTaken = { shots ->
                showCamera = false
                pages = pages + shots
                curPage = pages.size - 1
                toast = "已导入 ${shots.size} 页，点页面开始框选"
            },
            onOpenAlbum = { showCamera = false; showAlbum = true },
            onClose = { showCamera = false }
        )
        return
    }
    // 相册（原生多选）
    if (showAlbum) {
        LaunchedEffect(Unit) {
            showAlbum = false
            pickLauncher.launch("image/*")
        }
    }

    // 裁剪全屏
    if (cropPageIndex in pages.indices) {
        val bmp = remember(cropPageIndex) { loadBitmap(pages[cropPageIndex]) }
        CropScreen(
            bitmap = bmp,
            pageIndex = cropPageIndex,
            pageCount = pages.size,
            onSwitchPage = { cropPageIndex = it },
            onBack = { cropPageIndex = -1 },
            onExtract = { quadList ->
                cropPageIndex = -1
                toast = "正在裁剪与增强…"
                scope.launch {
                    extractQuads(context, bmp, quadList) { q ->
                        queue = queue + q
                    }
                    toast = "已提取 ${quadList.size} 道题进入解析队列"
                    onChanged()
                }
            }
        )
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Spacer(Modifier.height(8.dp))
        if (pages.isEmpty()) {
            // 入口卡片
            EntryCard(
                icon = Icons.Default.CameraAlt,
                title = "拍摄试卷",
                sub = "原生相机 · 连续多张拍摄",
                highlight = true
            ) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) showCamera = true
                else { pendingCamera = true; permLauncher.launch(Manifest.permission.CAMERA) }
            }
            Spacer(Modifier.height(10.dp))
            EntryCard(
                icon = Icons.Default.PhotoLibrary,
                title = "相册导入",
                sub = "系统相册多选，原生流畅"
            ) { pickLauncher.launch("image/*") }
            Spacer(Modifier.height(10.dp))
            EntryCard(
                icon = Icons.Default.Description,
                title = "使用流程",
                sub = "拍摄/导入 → 点页面框选(八点透视) → 批量提取 → 自动增强解析入库"
            ) {}
        } else {
            Text("已导入 ${pages.size} 页 · 点击页面进入框选", fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(pages.size) { i ->
                    Box {
                        AsyncImage(
                            model = pages[i],
                            contentDescription = null,
                            modifier = Modifier.width(92.dp).height(120.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { curPage = i; cropPageIndex = i },
                            contentScale = ContentScale.Crop
                        )
                        Text("${i + 1}", fontSize = 10.sp, color = Color.White,
                            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 1.dp))
                        Text("×", color = Color.White, fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Red.copy(alpha = 0.9f)).padding(horizontal = 7.dp, vertical = 2.dp)
                                .clickable {
                                    pages = pages.filterIndexed { idx, _ -> idx != i }
                                    curPage = (curPage).coerceAtMost(pages.size - 1)
                                })
                    }
                }
                item {
                    Box(
                        Modifier.width(92.dp).height(120.dp).clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                                    == PackageManager.PERMISSION_GRANTED) showCamera = true
                                else { pendingCamera = true; permLauncher.launch(Manifest.permission.CAMERA) }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CameraAlt, null, tint = SkyPrimary)
                            Text("继续拍", fontSize = 12.sp, color = SkyPrimaryDeep)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { cropPageIndex = curPage.coerceIn(0, pages.size - 1) }) {
                    Text("开始框选", color = Color.White)
                }
                OutlinedButton(onClick = { pages = emptyList() }) { Text("清空页面") }
            }
        }

        // 解析队列
        Spacer(Modifier.height(16.dp))
        if (queue.isNotEmpty()) {
            Text("解析队列", fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            queue.forEach { q ->
                QueueRow(q)
                Spacer(Modifier.height(6.dp))
            }
        }
        Spacer(Modifier.height(90.dp))
    }
}

data class QueueItem(val id: String, val status: String, val subject: String, val knowledge: String)

@Composable
private fun QueueRow(q: QueueItem) {
    Card(shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (q.subject.isNotBlank()) q.subject else "题目",
                fontSize = 13.sp, modifier = Modifier.weight(1f),
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            val (txt, color) = when (q.status) {
                "doing" -> "解析中" to SkyPrimary
                "done" -> "已完成" to Green
                "fail" -> "失败" to Red
                else -> "等待中" to MaterialTheme.colorScheme.outline
            }
            Text(txt, fontSize = 11.sp, color = color,
                modifier = Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
                    .padding(horizontal = 9.dp, vertical = 3.dp))
        }
    }
}

@Composable
private fun EntryCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String, sub: String, highlight: Boolean = false, onClick: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) MaterialTheme.colorScheme.primaryContainer
                             else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(50.dp).clip(RoundedCornerShape(15.dp))
                    .background(if (highlight) SkyPrimary else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = if (highlight) Color.White else SkyPrimaryDeep)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, fontSize = 15.sp,
                    color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface)
                Text(sub, fontSize = 12.sp,
                    color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.outline)
            }
        }
    }
}

private fun copyUriToCache(context: android.content.Context, uri: Uri): File? {
    return try {
        val dir = File(context.cacheDir, "imports").apply { mkdirs() }
        val f = File(dir, "img_${System.currentTimeMillis()}_${(0..999).random()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { ins ->
            val bmp = android.graphics.BitmapFactory.decodeStream(ins) ?: return null
            FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        }
        f
    } catch (e: Exception) { null }
}

/** 透视裁剪 + 增强 + 入队解析 */
private suspend fun extractQuads(
    context: android.content.Context,
    bmp: Bitmap,
    quadList: List<List<androidx.compose.ui.geometry.Offset>>,
    onQueue: (QueueItem) -> Unit
) {
    for (pts in quadList) {
        val srcPts = pts.map { Perspective.Pt(it.x * bmp.width, it.y * bmp.height) }
        val wTop = kotlin.math.hypot((srcPts[1].x - srcPts[0].x).toDouble(), (srcPts[1].y - srcPts[0].y).toDouble())
        val wBot = kotlin.math.hypot((srcPts[2].x - srcPts[3].x).toDouble(), (srcPts[2].y - srcPts[3].y).toDouble())
        val hL = kotlin.math.hypot((srcPts[3].x - srcPts[0].x).toDouble(), (srcPts[3].y - srcPts[0].y).toDouble())
        val hR = kotlin.math.hypot((srcPts[2].x - srcPts[1].x).toDouble(), (srcPts[2].y - srcPts[1].y).toDouble())
        val outW = maxOf(wTop, wBot).toInt().coerceIn(40, 2400)
        val outH = (outW * maxOf(hL, hR) / maxOf(1.0, maxOf(wTop, wBot))).toInt().coerceIn(40, 3200)

        val qid = Store.uid()
        onQueue(QueueItem(qid, "doing", "", ""))

        try {
            var cropped = withContext(Dispatchers.Default) {
                Perspective.warp(bmp, srcPts, outW, outH)
            }
            if (Store.settings["enhance"] != "0") {
                cropped = withContext(Dispatchers.Default) { Enhance.process(cropped) }
            }
            // 保存图片
            val imgName = "m_${System.currentTimeMillis()}_${(0..999).random()}.jpg"
            FileOutputStream(File(Store.imgDir, imgName)).use {
                cropped.compress(Bitmap.CompressFormat.JPEG, 88, it)
            }
            // AI 解析
            val result = AiParser.parse(cropped)
            val m = Mistake(
                id = Store.uid(),
                subject = result.subject,
                knowledge = result.knowledge,
                question = result.question,
                answer = result.answer,
                analysis = result.analysis,
                imageFile = imgName,
                parsedBy = result.by
            )
            Store.mistakes.add(0, m)
            Store.saveMistakes()
            withContext(Dispatchers.IO) { Supabase.pushMistake(m) }
            onQueue(QueueItem(qid, "done", m.subject, m.knowledge))
        } catch (e: Exception) {
            onQueue(QueueItem(qid, "fail", "", ""))
        }
    }
}
