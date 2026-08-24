package com.jiancuoti.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.jiancuoti.app.net.VariantQuestion
import com.jiancuoti.app.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** BlurView 安全包装：绘制异常时自动关闭模糊（防个别机型 RenderEffect 崩溃） */
class SafeBlurView(ctx: android.content.Context) : eightbitlab.com.blurview.BlurView(ctx) {
    @Volatile private var drawFailed = false
    override fun draw(canvas: android.graphics.Canvas) {
        if (drawFailed) return
        try {
            super.draw(canvas)
        } catch (t: Throwable) {
            drawFailed = true
            try { setBlurEnabled(false) } catch (_: Throwable) {}
        }
    }
}

class MainActivity : ComponentActivity() {

    /** 崩溃日志屏：纯原生 View（不依赖 Compose），保证任何崩溃后都能看到堆栈 */
    private fun showCrashScreen(crashFile: File) {
        val log = try { crashFile.readText().takeLast(3000) } catch (e: Exception) { "日志读取失败：$e" }
        val scroll = android.widget.ScrollView(this)
        val col = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 120, 48, 48)
        }
        val title = android.widget.TextView(this).apply {
            text = "⚠ 上次启动发生了崩溃"
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val hint = android.widget.TextView(this).apply {
            text = "请截图下面的内容发给开发者，然后点「清除并尝试进入」：\n"
            textSize = 13f
            setPadding(0, 24, 0, 24)
        }
        val tv = android.widget.TextView(this).apply {
            text = log
            textSize = 11f
            setTextIsSelectable(true)
            setTypeface(android.graphics.Typeface.MONOSPACE)
        }
        val btnRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(0, 40, 0, 0)
        }
        val btnGo = android.widget.Button(this).apply { text = "清除并尝试进入" }
        btnGo.setOnClickListener {
            crashFile.delete()
            recreate()
        }
        val btnCopy = android.widget.Button(this).apply { text = "复制日志" }
        btnCopy.setOnClickListener {
            val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("crash", log))
            android.widget.Toast.makeText(this, "已复制", android.widget.Toast.LENGTH_SHORT).show()
        }
        btnRow.addView(btnGo)
        btnRow.addView(btnCopy)
        col.addView(title); col.addView(hint); col.addView(tv); col.addView(btnRow)
        scroll.addView(col)
        setContentView(scroll)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 崩溃日志：写到应用目录，便于排查闪退
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                File(filesDir, "crash_log.txt").writeText(
                    "${java.util.Date()} | ${android.os.Build.MODEL} API${android.os.Build.VERSION.SDK_INT}\n" +
                    e.stackTraceToString()
                )
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(t, e)
        }
        // 上次启动崩溃过 → 先显示日志屏（不走正常启动，保证日志一定看得到）
        val crashFile = File(filesDir, "crash_log.txt")
        if (crashFile.exists()) {
            showCrashScreen(crashFile)
            return
        }
        Store.init(applicationContext)
        enableEdgeToEdge()
        try {
            window.attributes = window.attributes.apply {
                preferredRefreshRate = 120f
                preferredDisplayModeId = 0
            }
        } catch (_: Exception) {}
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
        // 非关键任务延迟启动（让首帧先渲染出来）：云同步 + 续析 + 轮询
        lifecycleScope.launch {
            kotlinx.coroutines.delay(600)
            if (Supabase.configured) {
                Supabase.pull()
                Supabase.pullPapers()
            }
            com.jiancuoti.app.net.BgTasks.resumePending()
            com.jiancuoti.app.net.BgTasks.startSyncLoop()
        }
    }
}

/** 全屏页面（覆盖 Dock 栏） */
sealed class FullPage {
    data class Detail(val m: Mistake) : FullPage()
    data class Edit(val m: Mistake) : FullPage()
    data class Variants(val vs: List<VariantQuestion>, val origin: Mistake) : FullPage()
    data class Chat(val ctx: String, val img: File?) : FullPage()
    data class Viewer(val images: List<File>, val index: Int, val titles: List<String>) : FullPage()
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

    // 全屏页面栈（详情/编辑/变式/对话/看图），渲染在 Scaffold 之上 → 盖住 Dock
    var pageStack by remember { mutableStateOf(listOf<FullPage>()) }
    fun push(p: FullPage) { pageStack = pageStack + p }
    fun pop() { pageStack = pageStack.dropLast(1) }

    val nav = remember {
        PageNav(
            openDetail = { push(FullPage.Detail(it)) },
            openEdit = { push(FullPage.Edit(it)) },
            openChat = { ctx, img -> push(FullPage.Chat(ctx, img)) },
            openVariants = { vs, origin -> push(FullPage.Variants(vs, origin)) },
            openViewer = { imgs, idx, titles -> push(FullPage.Viewer(imgs, idx, titles)) }
        )
    }

    // 系统返回键：先退全屏页面栈 → 再回错题库首页 → 最后退出
    BackHandler(enabled = pageStack.isNotEmpty() || tab != 0) {
        if (pageStack.isNotEmpty()) pop()
        else if (tab != 0) tab = 0
    }

    // 后台任务提示（解析完成等）→ 前台 Toast
    val gToast = com.jiancuoti.app.net.BgTasks.globalToast.value
    LaunchedEffect(gToast.first) {
        if (gToast.second.isNotBlank()) toast = gToast.second
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val bgTop = if (dark) Color(0xFF0A111C) else Color(0xFFEAF6FF)
    val bgBottom = if (dark) Color(0xFF132338) else Color(0xFFD8ECFC)

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(bgTop, bgBottom)))
    ) {
        // 装饰光斑
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
                // Dock：真·毛玻璃（BlurView 模糊背后内容，教程三层结构）
                // 防护：延迟到首帧后创建 + 工厂异常降级，避免个别机型闪退
                val supportBlur = android.os.Build.VERSION.SDK_INT >= 31
                val rootContent = (context as? android.app.Activity)
                    ?.findViewById<android.view.ViewGroup>(android.R.id.content)
                var blurReady by remember { mutableStateOf(false) }
                var blurFailed by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(80)
                    blurReady = true
                }
                Box(
                    Modifier.fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 30.dp)
                        .padding(bottom = 6.dp)
                ) {
                    // 底层：半透明玻璃胶囊（BlurView 失败/未就绪时的保底外观）
                    Box(
                        Modifier.fillMaxWidth().height(58.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(
                                Brush.verticalGradient(
                                    if (dark) listOf(Color(0xFF22344A).copy(alpha = 0.9f), Color(0xFF1A2736).copy(alpha = 0.82f))
                                    else listOf(Color.White.copy(alpha = 0.8f), Color.White.copy(alpha = 0.6f))
                                )
                            )
                    )
                    // 中间层：BlurView（模糊背后的内容层）
                    if (supportBlur && rootContent != null && blurReady && !blurFailed) {
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { ctx ->
                                try {
                                    SafeBlurView(ctx).apply {
                                        setupWith(rootContent, eightbitlab.com.blurview.RenderEffectBlur())
                                            .setFrameClearDrawable(null)
                                            .setBlurRadius(18f)
                                        setOverlayColor(
                                            if (dark) 0x99101B29.toInt() else 0x59FFFFFF
                                        )
                                        outlineProvider = object : android.view.ViewOutlineProvider() {
                                            override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                                                outline.setRoundRect(0, 0, view.width, view.height, view.height / 2f)
                                            }
                                        }
                                        clipToOutline = true
                                    }
                                } catch (t: Throwable) {
                                    blurFailed = true
                                    android.view.View(ctx) // 降级：由底层玻璃胶囊兜底
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(58.dp)
                        )
                    }
                    // 前景层：高光描边 + 图标
                    Box(
                        Modifier.fillMaxWidth().height(58.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .border(
                                1.dp,
                                if (dark) Color(0xFFBFE0F5).copy(alpha = 0.22f)
                                else Color.White.copy(alpha = 0.9f),
                                RoundedCornerShape(percent = 50)
                            )
                    ) {
                        Row(
                            Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val items: List<Pair<String, Int>> = listOf(
                                "错题库" to 0,
                                "组卷" to 1,
                                "拍摄" to 2,
                                "统计" to 3,
                                "我的" to 4
                            )
                            val ink = MaterialTheme.colorScheme.onSurface
                            val dim = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            items.forEach { (_, idx) ->
                                Box(
                                    Modifier.weight(1f).fillMaxHeight()
                                        .clickableNoRipple { tab = idx },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (idx == 2) {
                                        // 中间拍摄：玻璃白圆 + 墨色图标
                                        Box(
                                            Modifier.size(42.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (dark) Color(0xFFDCEBF7).copy(alpha = 0.92f)
                                                    else Color.White.copy(alpha = 0.95f)
                                                )
                                                .border(
                                                    1.dp,
                                                    if (dark) Color.White.copy(alpha = 0.3f)
                                                    else Color.White,
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            DockIcon(2, Color(0xFF16324A),
                                                modifier = Modifier.size(23.dp))
                                        }
                                    } else {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            DockIcon(idx, if (tab == idx) ink else dim,
                                                modifier = Modifier.size(24.dp))
                                            Box(
                                                Modifier.padding(top = 3.dp)
                                                    .size(if (tab == idx) 4.dp else 0.dp)
                                                    .clip(CircleShape)
                                                    .background(ink)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (tab) {
                    0 -> LibraryScreen(
                        onChanged = bump,
                        nav = nav,
                        onResumeDrafts = {
                            val drafts = Store.drafts()
                            if (drafts.isNotEmpty()) {
                                cropFiles = drafts
                                cropIndex = 0
                            }
                        }
                    )
                    1 -> ComposeScreen(onChanged = bump)
                    2 -> CameraScreen(
                        onSingleShot = { f ->
                            Store.saveDraft(f)
                            cropFiles = listOf(f); cropIndex = 0
                        },
                        onMultiShots = { fs ->
                            fs.forEach { Store.saveDraft(it) }
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
                                    files.forEach { Store.saveDraft(it) }
                                    cropFiles = files; cropIndex = 0
                                }
                            }
                        },
                        onImportPdf = { files ->
                            tab = 2
                            if (files.isNotEmpty()) {
                                files.forEach { Store.saveDraft(it) }
                                cropFiles = files; cropIndex = 0
                            }
                        }
                    )
                }
            }
        }

        // 裁剪全屏层（异步解码图片，不阻塞首帧）
        if (cropFiles.isNotEmpty() && cropIndex < cropFiles.size) {
            val displayBmps by androidx.compose.runtime.produceState<List<android.graphics.Bitmap>?>(
                initialValue = null, key1 = cropFiles
            ) {
                value = withContext(Dispatchers.IO) {
                    cropFiles.map { loadBitmap(it, 1200) }
                }
            }
            val bmps = displayBmps
            if (bmps == null) {
                Box(Modifier.fillMaxSize().background(Color(0xFF0B1220)),
                    contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(Modifier.height(12.dp))
                        Text("正在加载图片…", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                    }
                }
            } else {
            CropScreen(
                displayBitmaps = bmps,
                pageIndex = cropIndex,
                onSwitchPage = { cropIndex = it },
                onBack = { cropFiles = emptyList() },
                onExtract = { items, rotations ->
                    val files = cropFiles
                    cropFiles = emptyList()
                    tab = 0
                    files.forEach { Store.removeDraft(it.name) }
                    com.jiancuoti.app.net.BgTasks.scope.launch {
                        val fullBmps = withContext(Dispatchers.IO) {
                            files.mapIndexed { i, f ->
                                val b = loadBitmap(f, 2400)
                                val ang = rotations[i] ?: 0f
                                if (ang % 360f != 0f) rotateBitmap(b, ang) else b
                            }
                        }
                        var parsed = 0
                        val savedList = mutableListOf<Mistake>()
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
                                val finalBmp = if (crops.size > 1) stitchVertical(crops) else crops[0]
                                val imgName = "m_${Store.uid()}.jpg"
                                withContext(Dispatchers.IO) {
                                    FileOutputStream(File(Store.imgDir, imgName)).use {
                                        finalBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it)
                                    }
                                }
                                val willParse = Store.settings["autoParse"] != "0" && AiParser.configured
                                val willKnowledge = Store.settings["autoKnowledge"] != "0" && AiParser.configured
                                val m = Mistake(
                                    id = Store.uid(),
                                    imageFile = imgName,
                                    parsedBy = "manual",
                                    parsing = willParse || willKnowledge
                                )
                                Store.mistakes.add(0, m)
                                Store.saveMistakes()
                                savedList.add(m)
                                bump()
                                // 云同步异步推送（只同步文字）
                                launch { Supabase.pushMistake(m) }
                                // 解析进入全局队列（串行排队，返回不中断）
                                when {
                                    willParse -> {
                                        com.jiancuoti.app.net.BgTasks.enqueueParse(m.id, "full")
                                        parsed++
                                    }
                                    willKnowledge -> com.jiancuoti.app.net.BgTasks.enqueueParse(m.id, "knowledge")
                                }
                            } catch (_: Exception) {}
                        }
                        toast = if (savedList.isEmpty()) "未提取到题目"
                                else if (parsed > 0) "已保存 ${savedList.size} 题，$parsed 题进入解析队列"
                                else "已保存 ${savedList.size} 题"
                    }
                }
            )
            }
        }

        // 全屏页面栈渲染（最上层，盖住 Dock）
        pageStack.lastOrNull()?.let { page ->
            when (page) {
                is FullPage.Detail -> DetailPage(
                    page.m, onBack = { pop() }, nav = nav, onChanged = bump
                )
                is FullPage.Edit -> EditPage(
                    page.m, onBack = { pop() },
                    onSaved = { pop(); bump() }
                )
                is FullPage.Variants -> VariantsPage(
                    page.vs, page.origin, onBack = { pop() },
                    onSavedVariant = bump
                )
                is FullPage.Chat -> ChatPage(
                    contextText = page.ctx, contextImage = page.img,
                    onClose = { pop() }
                )
                is FullPage.Viewer -> ImageViewer(
                    images = page.images, initialIndex = page.index,
                    titles = page.titles, onClose = { pop() }
                )
            }
        }

        // Toast
        if (toast.isNotBlank()) {
            LaunchedEffect(toast) { kotlinx.coroutines.delay(2400); toast = "" }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Surface(
                    Modifier.padding(top = 70.dp),
                    shape = RoundedCornerShape(50),
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

/** 任意角度旋转位图 */
private fun rotateBitmap(src: android.graphics.Bitmap, degrees: Float): android.graphics.Bitmap {
    val m = android.graphics.Matrix().apply { postRotate(degrees) }
    return android.graphics.Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}

/** 垂直拼接 */
private fun stitchVertical(crops: List<android.graphics.Bitmap>): android.graphics.Bitmap {
    val w = crops.maxOf { it.width }
    val gap = 14
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

private var importCounter = 0L
private fun copyUri(context: android.content.Context, uri: Uri): File? {
    return try {
        val dir = File(context.cacheDir, "imports").apply { mkdirs() }
        importCounter++
        val f = File(dir, "img_${System.currentTimeMillis()}_$importCounter.jpg")
        context.contentResolver.openInputStream(uri)?.use { ins ->
            val bmp = android.graphics.BitmapFactory.decodeStream(ins) ?: return null
            FileOutputStream(f).use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
        }
        f
    } catch (e: Exception) { null }
}
