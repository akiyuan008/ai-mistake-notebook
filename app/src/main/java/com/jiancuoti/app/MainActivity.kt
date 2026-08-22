package com.jiancuoti.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.jiancuoti.app.data.Mistake
import com.jiancuoti.app.data.Store
import com.jiancuoti.app.img.Enhance
import com.jiancuoti.app.img.Perspective
import com.jiancuoti.app.net.AiParser
import com.jiancuoti.app.net.Supabase
import com.jiancuoti.app.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            var themeMode by remember {
                mutableStateOf(
                    when (Store.settings["theme"]) {
                        "light" -> ThemeMode.LIGHT
                        "dark" -> ThemeMode.DARK
                        else -> ThemeMode.AUTO
                    }
                )
            }
            AppTheme(mode = themeMode) {
                MainScaffold(
                    themeMode = themeMode,
                    onThemeMode = { m ->
                        themeMode = m
                        Store.settings["theme"] = when (m) {
                            ThemeMode.LIGHT -> "light"
                            ThemeMode.DARK -> "dark"
                            else -> "auto"
                        }
                        Store.saveSettings()
                    }
                )
            }
        }
        if (Supabase.configured) {
            lifecycleScope.launch { Supabase.pull() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(themeMode: ThemeMode, onThemeMode: (ThemeMode) -> Unit) {
    val dark = isDarkActive(themeMode)
    var tab by remember { mutableIntStateOf(0) }
    var version by remember { mutableIntStateOf(0) }
    val bump: () -> Unit = { version++ }

    // 拍摄/导入后的待裁剪队列
    var cropFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var cropIndex by remember { mutableIntStateOf(0) }
    var toast by remember { mutableStateOf("") }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val bgTop = if (dark) Color(0xFF0A111C) else Color(0xFFEAF6FF)
    val bgBottom = if (dark) Color(0xFF132338) else Color(0xFFD8ECFC)

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(bgTop, bgBottom)))
    ) {
        // 装饰光斑（深色下更亮，制造层次）
        Box(Modifier.size(300.dp).offset(x = 200.dp, y = (-100).dp)
            .background(
                Brush.radialGradient(listOf(
                    if (dark) Color(0xFF2C5A88).copy(alpha = 0.45f) else Color(0xFF7DD3FC).copy(alpha = 0.35f),
                    Color.Transparent)),
                CircleShape
            ))
        Box(Modifier.size(260.dp).offset(x = (-120).dp, y = 500.dp)
            .background(
                Brush.radialGradient(listOf(
                    if (dark) Color(0xFF1B4666).copy(alpha = 0.35f) else Color(0xFFBAE6FD).copy(alpha = 0.3f),
                    Color.Transparent)),
                CircleShape
            ))

        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                Box {
                    // 悬浮玻璃 Dock：圆角胶囊、半透明、高光描边
                    NavigationBar(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .navigationBarsPadding(),
                        containerColor = if (dark) Color(0xFF16222F).copy(alpha = 0.82f)
                                         else Color.White.copy(alpha = 0.72f),
                        tonalElevation = 0.dp
                    ) {
                        val items: List<Triple<String, ImageVector, Int>> = listOf(
                            Triple("错题库", Icons.Default.Book, 0),
                            Triple("组卷", Icons.Default.Assignment, 1),
                            Triple("", Icons.Default.CameraAlt, 2),
                            Triple("统计", Icons.Default.BarChart, 3),
                            Triple("我的", Icons.Default.Person, 4)
                        )
                        items.forEach { (label, icon, idx) ->
                            if (idx == 2) {
                                // 中间占位（真正的拍摄按钮画在 Dock 外层，避免被裁剪）
                                NavigationBarItem(
                                    selected = false,
                                    onClick = {},
                                    enabled = false,
                                    icon = { Box(Modifier.size(1.dp)) },
                                    label = { Text("拍摄", fontSize = 10.sp, color = Color.Transparent) }
                                )
                            } else {
                                NavigationBarItem(
                                    selected = tab == idx,
                                    onClick = { tab = idx },
                                    icon = { Icon(icon, null) },
                                    label = { Text(label, fontSize = 10.5.sp) }
                                )
                            }
                        }
                    }
                    // 拍摄按钮：覆盖层，凸出于 Dock 顶部，完整显示
                    Box(
                        Modifier.align(Alignment.TopCenter)
                            .offset(y = (-14).dp)
                            .size(58.dp)
                            .shadow(10.dp, CircleShape, clip = false)
                            .background(
                                Brush.linearGradient(
                                    listOf(SkyPrimary, SkyPrimaryDeep)
                                ),
                                CircleShape
                            )
                            .border(2.5.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                            .clickableNoRipple { tab = 2 },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CameraAlt, null, tint = Color.White,
                            modifier = Modifier.size(26.dp))
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (tab) {
                    0 -> LibraryScreen(onChanged = bump)
                    1 -> ComposeScreen(onChanged = bump)
                    2 -> CameraScreen(
                        onSingleShot = { f ->
                            cropFiles = listOf(f); cropIndex = 0
                        },
                        onMultiShots = { fs ->
                            cropFiles = fs; cropIndex = 0
                        },
                        onOpenAlbum = { tab = 5 }
                    )
                    3 -> StatsScreen()
                    4 -> SettingsScreen(themeMode = themeMode, onThemeMode = onThemeMode)
                    5 -> AlbumScreen(
                        onClose = { tab = 2 },
                        onConfirm = { uris ->
                            tab = 2
                            scope.launch {
                                val files = uris.mapNotNull { copyUri(context, it) }
                                if (files.isNotEmpty()) {
                                    cropFiles = files; cropIndex = 0
                                }
                            }
                        },
                        onImportPdf = { files ->
                            tab = 2
                            if (files.isNotEmpty()) {
                                cropFiles = files; cropIndex = 0
                            }
                        }
                    )
                }
            }
        }

        // 裁剪全屏层
        if (cropFiles.isNotEmpty() && cropIndex < cropFiles.size) {
            val displayBmps = remember(cropFiles) {
                cropFiles.map { loadBitmap(it, 1200) }
            }
            CropScreen(
                displayBitmaps = displayBmps,
                pageIndex = cropIndex,
                onSwitchPage = { cropIndex = it },
                onBack = { cropFiles = emptyList() },
                onExtract = { items ->
                    val files = cropFiles
                    cropFiles = emptyList()
                    tab = 0
                    scope.launch {
                        // 预载各页高清图
                        val fullBmps = withContext(Dispatchers.IO) {
                            files.map { loadBitmap(it, 2400) }
                        }
                        var saved = 0
                        var parsed = 0
                        val total = items.size
                        for ((idx, parts) in items.withIndex()) {
                            try {
                                val crops = parts.map { part ->
                                    val bmp = fullBmps[part.page]
                                    val srcPts = part.pts.map {
                                        Perspective.Pt(it.x * bmp.width, it.y * bmp.height)
                                    }
                                    val wTop = kotlin.math.hypot((srcPts[1].x - srcPts[0].x).toDouble(), (srcPts[1].y - srcPts[0].y).toDouble())
                                    val wBot = kotlin.math.hypot((srcPts[2].x - srcPts[3].x).toDouble(), (srcPts[2].y - srcPts[3].y).toDouble())
                                    val hL = kotlin.math.hypot((srcPts[3].x - srcPts[0].x).toDouble(), (srcPts[3].y - srcPts[0].y).toDouble())
                                    val hR = kotlin.math.hypot((srcPts[2].x - srcPts[1].x).toDouble(), (srcPts[2].y - srcPts[1].y).toDouble())
                                    val outW = maxOf(wTop, wBot).toInt().coerceIn(40, 2400)
                                    val outH = (outW * maxOf(hL, hR) / maxOf(1.0, maxOf(wTop, wBot))).toInt().coerceIn(40, 3200)
                                    var c = withContext(Dispatchers.Default) { Perspective.warp(bmp, srcPts, outW, outH) }
                                    if (Store.settings["enhance"] != "0") {
                                        c = withContext(Dispatchers.Default) { Enhance.process(c) }
                                    }
                                    c
                                }
                                // 多部分（跨页拼接）→ 等宽合成一张
                                val finalBmp = if (crops.size > 1) stitchVertical(crops) else crops[0]
                                val imgName = "m_${System.currentTimeMillis()}_${(0..999).random()}.jpg"
                                withContext(Dispatchers.IO) {
                                    FileOutputStream(File(Store.imgDir, imgName)).use {
                                        finalBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it)
                                    }
                                }
                                // AI 解析失败不丢图：保留图片待手动/重新解析
                                val result = try {
                                    toast = "AI 解析中 ${idx + 1}/$total"
                                    val r = AiParser.parse(finalBmp)
                                    parsed++
                                    r
                                } catch (_: Exception) {
                                    com.jiancuoti.app.net.ParseResult(
                                        subject = Store.settings["defaultSubject"] ?: "其他",
                                        knowledge = "", question = "", answer = "",
                                        analysis = "", by = "manual"
                                    )
                                }
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
                                saved++
                            } catch (_: Exception) {}
                        }
                        bump()
                        toast = "提取完成：共 $saved 题（AI 解析 $parsed 题）"
                    }
                }
            )
        }

        // Toast
        if (toast.isNotBlank()) {
            LaunchedEffect(toast) { kotlinx.coroutines.delay(2400); toast = "" }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Surface(
                    Modifier.padding(top = 70.dp),
                    shape = RoundedCornerShapeT,
                    color = if (dark) Color(0xFFE4EEF6) else Color(0xFF0C2B42)
                ) {
                    Text(toast,
                        color = if (dark) Color(0xFF0C2B42) else Color.White,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                        fontSize = 13.sp)
                }
            }
        }
    }
}

private val RoundedCornerShapeT = androidx.compose.foundation.shape.RoundedCornerShape(50)

/** 垂直拼接：各部分先等宽缩放对齐，再上下合成（白色底，小间隙） */
private fun stitchVertical(crops: List<android.graphics.Bitmap>): android.graphics.Bitmap {
    val w = crops.maxOf { it.width }
    val gap = 14
    // 等宽对齐
    val aligned = crops.map { c ->
        if (c.width == w) c
        else android.graphics.Bitmap.createScaledBitmap(
            c, w, (c.height * w.toFloat() / c.width).toInt().coerceAtLeast(1), true)
    }
    val h = aligned.sumOf { it.height } + gap * (aligned.size - 1)
    val out = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    canvas.drawColor(android.graphics.Color.WHITE)
    var y = 0f
    for (c in aligned) {
        canvas.drawBitmap(c, 0f, y, null)
        y += c.height + gap
    }
    return out
}

private fun copyUri(context: android.content.Context, uri: Uri): File? {
    return try {
        val dir = File(context.cacheDir, "imports").apply { mkdirs() }
        val f = File(dir, "img_${System.currentTimeMillis()}_${(0..999).random()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { ins ->
            val bmp = android.graphics.BitmapFactory.decodeStream(ins) ?: return null
            FileOutputStream(f).use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
        }
        f
    } catch (e: Exception) { null }
}
