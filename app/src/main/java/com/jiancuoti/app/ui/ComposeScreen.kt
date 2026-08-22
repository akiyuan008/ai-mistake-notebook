package com.jiancuoti.app.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.jiancuoti.app.data.Mistake
import com.jiancuoti.app.data.Paper
import com.jiancuoti.app.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(onChanged: () -> Unit) {
    var subject by remember { mutableStateOf("全部") }
    var kp by remember { mutableStateOf("") }
    var errMin by remember { mutableStateOf("全部") }
    var range by remember { mutableStateOf("全部") }
    var mastered by remember { mutableStateOf("全部") }
    var picking by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<Set<String>>(emptySet()) }
    var titleDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var toast by remember { mutableStateOf("") }

    val pool = remember(subject, kp, errMin, range, mastered) {
        Store.mistakes.filter { m ->
            (mastered == "全部" || (mastered == "已掌握") == m.mastered) &&
            (subject == "全部" || m.subject == subject) &&
            (errMin == "全部" ||
                (errMin == "≥2次" && m.errorCount >= 2) ||
                (errMin == "≥3次" && m.errorCount >= 3)) &&
            (kp.isBlank() || m.knowledge.contains(kp, ignoreCase = true)) &&
            (range == "全部" || run {
                val days = mapOf("近7天" to 7L, "近1月" to 30L, "近3月" to 90L, "近6月" to 180L)[range] ?: 0L
                m.createdAt >= System.currentTimeMillis() - days * 86400000L
            })
        }
    }

    if (toast.isNotBlank()) {
        LaunchedEffect(toast) {
            kotlinx.coroutines.delay(2200); toast = ""
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Surface(
                Modifier.padding(top = 60.dp),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.inverseSurface
            ) {
                Text(toast, color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp), fontSize = 13.sp)
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        // 抽题条件
        GlassCard(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("抽题条件设置", fontSize = 15.sp)
                OutlinedTextField(
                    value = kp, onValueChange = { kp = it },
                    placeholder = { Text("搜索考点…", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MenuField(subject, listOf("全部") + com.jiancuoti.app.data.SUBJECTS, "目标学科",
                        Modifier.weight(1f)) { subject = it }
                    MenuField(errMin, listOf("全部", "≥2次", "≥3次"), "错误次数",
                        Modifier.weight(1f)) { errMin = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("全部", "近7天", "近1月", "近3月", "近6月").forEach { r ->
                        FilterChip(selected = range == r, onClick = { range = r },
                            label = { Text(r, fontSize = 11.sp) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("未掌握", "已掌握", "全部").forEach { s ->
                        FilterChip(selected = mastered == s, onClick = { mastered = s },
                            label = { Text(s, fontSize = 11.sp) })
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 随机组卷
        BigAction(
            icon = { Icon(Icons.Default.Shuffle, null, tint = SkyPrimaryDeep) },
            title = "随机组卷",
            sub = "按条件自动抽题（当前符合 ${pool.size} 题）"
        ) {
            if (pool.isEmpty()) { toast = "当前条件下没有可抽的题目"; return@BigAction }
            picked = pool.shuffled().map { it.id }.toSet()
            picking = true
        }
        Spacer(Modifier.height(10.dp))
        BigAction(
            icon = { Icon(Icons.Default.TouchApp, null, tint = Color(0xFFB45309)) },
            title = "手动组卷",
            sub = "逐题挑选，自由组合"
        ) {
            picked = emptySet()
            picking = true
        }

        Spacer(Modifier.height(16.dp))

        // 已创建试卷
        val myPapers = Store.papers
        if (myPapers.isNotEmpty()) {
            Text("已创建的试卷", fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                myPapers.forEach { p ->
                    PaperRow(p, onOpen = {
                        picked = p.questions.filter { id -> Store.mistakes.any { it.id == id } }.toSet()
                        if (picked.isEmpty()) { toast = "卷中题目已不在错题库" } else titleDialog = true
                    }, onDelete = {
                        Store.papers.remove(p); Store.savePapers(); onChanged()
                    })
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(90.dp))
            }
        }
    }

    if (picking) {
        PickDialog(pool, picked, onClose = { picking = false },
            onConfirm = { picked = it; picking = false; titleDialog = true })
    }

    if (titleDialog) {
        TitleDialog(
            onClose = { titleDialog = false },
            onConfirm = { title ->
                titleDialog = false
                val qs = Store.mistakes.filter { picked.contains(it.id) }
                // 保存记录
                val paper = Paper(
                    id = Store.uid(), name = title,
                    subjects = qs.map { it.subject }.distinct().joinToString("、"),
                    count = qs.size, questions = qs.map { it.id }
                )
                Store.papers.add(0, paper); Store.savePapers()
                onChanged()
                // 生成 PDF 并分享
                scope.launch {
                    val file = generatePaperPdf(context, title, qs)
                    if (file != null) {
                        try {
                            val uri = FileProvider.getUriForFile(context,
                                "${context.packageName}.fileprovider", file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "打印 / 分享试卷"))
                        } catch (e: Exception) { toast = "分享失败：${e.message}" }
                    } else toast = "生成失败"
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuField(value: String, options: List<String>, label: String,
                      modifier: Modifier, onPick: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value, onValueChange = {}, readOnly = true,
            label = { Text(label, fontSize = 12.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(o) }, onClick = { onPick(o); expanded = false })
            }
        }
    }
}

@Composable
private fun BigAction(icon: @Composable () -> Unit, title: String, sub: String, onClick: () -> Unit) {
    GlassCard(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) { icon() }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, fontSize = 15.sp)
                Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun PaperRow(p: Paper, onOpen: () -> Unit, onDelete: () -> Unit) {
    GlassCard(shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${p.count} 题 · ${fmtDate(p.createdAt)}", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline)
            }
            TextButton(onClick = onOpen) { Text("查看") }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun PickDialog(pool: List<Mistake>, initial: Set<String>,
                       onClose: () -> Unit, onConfirm: (Set<String>) -> Unit) {
    var sel by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onClose) {
        Surface(
            Modifier.fillMaxWidth().fillMaxHeight(0.88f),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("选择题目", fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Text("${sel.size} 题", color = Amber, fontSize = 14.sp)
                    TextButton(onClick = { sel = pool.map { it.id }.toSet() }) { Text("全选") }
                    TextButton(onClick = { sel = emptySet() }) { Text("清空") }
                }
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                LazyColumn(Modifier.weight(1f)) {
                    items(pool, key = { it.id }) { m ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                sel = if (sel.contains(m.id)) sel - m.id else sel + m.id
                            }.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = sel.contains(m.id),
                                onCheckedChange = { c ->
                                    sel = if (c) sel + m.id else sel - m.id
                                }
                            )
                            val img = Store.imgFile(m.imageFile)
                            if (img != null) {
                                AsyncImage(model = img, contentDescription = null,
                                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop)
                                Spacer(Modifier.width(10.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Row {
                                    SubjectTag(m.subject)
                                    if (m.errorCount > 1) {
                                        Spacer(Modifier.width(6.dp))
                                        Text("错${m.errorCount}次", fontSize = 11.sp, color = Red)
                                    }
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    m.question.ifBlank { "图片题" },
                                    fontSize = 12.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Button(
                    onClick = { onConfirm(sel) },
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    enabled = sel.isNotEmpty()
                ) { Text("确认组卷（${sel.size} 题）", color = Color.White) }
            }
        }
    }
}

@Composable
private fun TitleDialog(onClose: () -> Unit, onConfirm: (String) -> Unit) {
    var title by remember { mutableStateOf("错题复习卷 ${fmtDate(System.currentTimeMillis())}") }
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp)) {
                Text("创建试卷", fontSize = 16.sp)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = title, onValueChange = { if (it.length <= 100) title = it },
                    label = { Text("试卷标题") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClose) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onConfirm(title.ifBlank { "错题复习卷" }) }) {
                        Text("确定并生成", color = Color.White)
                    }
                }
            }
        }
    }
}

/** 原生 PDF 生成（A4：595x842pt，按 72dpi 的 2 倍绘制保证清晰） */
private suspend fun generatePaperPdf(
    context: android.content.Context, title: String, qs: List<Mistake>
): File? = withContext(Dispatchers.IO) {
    try {
        val doc = PdfDocument()
        val pageW = 595 * 2; val pageH = 842 * 2
        var pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, 1).create()
        var page = doc.startPage(pageInfo)
        var canvas = page.canvas
        val scale = 2f

        val titlePaint = Paint().apply { textSize = 26 * scale; textAlign = Paint.Align.CENTER; isAntiAlias = true }
        val subPaint = Paint().apply { textSize = 11 * scale; textAlign = Paint.Align.CENTER; color = android.graphics.Color.GRAY; isAntiAlias = true }
        val qPaint = Paint().apply { textSize = 13 * scale; isAntiAlias = true }
        val bodyPaint = Paint().apply { textSize = 12.5f * scale; isAntiAlias = true }
        val ansPaint = Paint().apply { textSize = 12 * scale; color = android.graphics.Color.rgb(60, 60, 60); isAntiAlias = true }
        val dashPaint = Paint().apply { color = android.graphics.Color.LTGRAY; strokeWidth = 1.5f; style = Paint.Style.STROKE }

        var y = 60f * scale
        fun newPageIfNeeded(need: Float) {
            if (y + need > pageH - 60 * scale) {
                doc.finishPage(page)
                pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageInfo.pageNumber + 1).create()
                page = doc.startPage(pageInfo)
                canvas = page.canvas
                y = 60f * scale
            }
        }

        canvas.drawText(title, pageW / 2f, y, titlePaint); y += 24 * scale
        canvas.drawText("简错题自动生成 · 共 ${qs.size} 题 · ${fmtDate(System.currentTimeMillis())}",
            pageW / 2f, y, subPaint); y += 22 * scale
        canvas.drawText("姓名：＿＿＿＿＿＿    班级：＿＿＿＿＿＿", 60 * scale, y, ansPaint); y += 26 * scale

        qs.forEachIndexed { i, q ->
            newPageIfNeeded(120 * scale)
            canvas.drawText("${i + 1}.（${q.subject}${if (q.knowledge.isNotBlank()) " · " + q.knowledge else ""}）",
                60 * scale, y, qPaint); y += 20 * scale
            if (q.question.isNotBlank()) {
                y = drawWrapped(canvas, q.question, bodyPaint, 60 * scale, y, pageW - 120 * scale, pageH - 60 * scale) {
                    doc.finishPage(page)
                    val pi = PdfDocument.PageInfo.Builder(pageW, pageH, pageInfo.pageNumber + 1).create()
                    page = doc.startPage(pi); canvas = page.canvas; y = 60f * scale
                }
            }
            val img = Store.imgFile(q.imageFile)
            if (img != null) {
                try {
                    val bmp = BitmapFactory.decodeFile(img.absolutePath)
                    if (bmp != null) {
                        val maxW = (pageW - 140 * scale)
                        val bw = minOf(bmp.width.toFloat(), maxW)
                        val bh = bmp.height * bw / bmp.width
                        newPageIfNeeded(bh + 10)
                        canvas.drawBitmap(
                            Bitmap.createScaledBitmap(bmp, bw.toInt(), bh.toInt(), true),
                            70 * scale, y, null
                        )
                        y += bh + 12 * scale
                    }
                } catch (_: Exception) {}
            }
            // 作答区
            newPageIfNeeded(50 * scale)
            canvas.drawRect(60 * scale, y, pageW - 60 * scale, y + 44 * scale, dashPaint)
            canvas.drawText("答：", 70 * scale, y + 18 * scale, ansPaint)
            y += 56 * scale
        }
        doc.finishPage(page)

        val dir = File(context.cacheDir, "papers").apply { mkdirs() }
        val out = File(dir, "${title.take(20)}.pdf")
        val fos = FileOutputStream(out)
        doc.writeTo(fos)
        fos.close()
        doc.close()
        out
    } catch (e: Exception) { null }
}

private fun drawWrapped(
    canvas: android.graphics.Canvas, text: String, paint: Paint,
    x: Float, startY: Float, maxWidth: Float, maxY: Float,
    onPageBreak: () -> Unit
): Float {
    var y = startY
    var line = StringBuilder()
    for (ch in text) {
        line.append(ch)
        if (paint.measureText(line.toString()) > maxWidth || ch == '\n') {
            if (ch == '\n') line.deleteCharAt(line.length - 1)
            canvas.drawText(line.toString(), x, y, paint)
            y += paint.textSize * 1.5f
            line = StringBuilder()
            if (y > maxY) { onPageBreak() }
        }
    }
    if (line.isNotEmpty()) {
        canvas.drawText(line.toString(), x, y, paint)
        y += paint.textSize * 1.5f
    }
    return y
}
