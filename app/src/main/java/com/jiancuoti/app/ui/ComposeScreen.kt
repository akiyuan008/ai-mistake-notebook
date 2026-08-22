package com.jiancuoti.app.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
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
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("全部", "近7天", "近1月", "近3月", "近6月").forEach { r ->
                        FilterChip(selected = range == r, onClick = { range = r },
                            label = { Text(r, fontSize = 11.sp) })
                    }
                }
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
            onConfirm = { title, opts ->
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
                    val file = generatePaperPdf(context, title, qs, opts)
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

/** 导出选项 */
data class ExportOpts(
    val withAnswerPage: Boolean = true,   // 附答案页
    val withKnowledge: Boolean = true,    // 打印知识点标注
    val withAnalysis: Boolean = false     // 打印解析步骤
)

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
                Text(sub, fontSize = 12.sp, lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun TitleDialog(onClose: () -> Unit, onConfirm: (String, ExportOpts) -> Unit) {
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
                Spacer(Modifier.height(14.dp))
                // 导出选项
                var withAnswerPage by remember { mutableStateOf(true) }
                var withKnowledge by remember { mutableStateOf(true) }
                var withAnalysis by remember { mutableStateOf(false) }
                Text("导出内容", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                LabeledCheckbox(withAnswerPage, "附答案页（卷末汇总）") { withAnswerPage = it }
                LabeledCheckbox(withKnowledge, "打印知识点标注") { withKnowledge = it }
                LabeledCheckbox(withAnalysis, "打印解析步骤") { withAnalysis = it }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClose) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        onConfirm(
                            title.ifBlank { "错题复习卷" },
                            ExportOpts(withAnswerPage, withKnowledge, withAnalysis)
                        )
                    }) {
                        Text("确定并生成", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledCheckbox(checked: Boolean, label: String, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onChange(!checked) }
            .padding(vertical = 4.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, fontSize = 13.sp)
    }
}

/** 原生 PDF 生成（A4：595x842pt，2 倍绘制）：图片保持真实宽高比且限高，可选知识点/解析/答案页 */
private suspend fun generatePaperPdf(
    context: android.content.Context, title: String, qs: List<Mistake>, opts: ExportOpts
): File? = withContext(Dispatchers.IO) {
    try {
        val doc = PdfDocument()
        val pageW = 595 * 2; val pageH = 842 * 2
        var pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, 1).create()
        var page = doc.startPage(pageInfo)
        var canvas = page.canvas
        val scale = 2f
        val marginX = 58f * scale
        val contentW = pageW - 2 * marginX

        val titlePaint = Paint().apply { textSize = 26 * scale; textAlign = Paint.Align.CENTER; isAntiAlias = true; isFakeBoldText = true }
        val subPaint = Paint().apply { textSize = 11 * scale; textAlign = Paint.Align.CENTER; color = android.graphics.Color.GRAY; isAntiAlias = true }
        val qPaint = Paint().apply { textSize = 13 * scale; isAntiAlias = true; isFakeBoldText = true }
        val bodyPaint = Paint().apply { textSize = 12.5f * scale; isAntiAlias = true }
        val kpPaint = Paint().apply { textSize = 10 * scale; color = android.graphics.Color.rgb(2, 100, 180); isAntiAlias = true }
        val ansPaint = Paint().apply { textSize = 12 * scale; color = android.graphics.Color.rgb(60, 60, 60); isAntiAlias = true }
        val ansTitlePaint = Paint().apply { textSize = 18 * scale; isAntiAlias = true; isFakeBoldText = true }
        val dashPaint = Paint().apply { color = android.graphics.Color.LTGRAY; strokeWidth = 1.2f; style = Paint.Style.STROKE }

        var y = 60f * scale
        var pageCount = 1
        fun newPage() {
            doc.finishPage(page)
            pageCount++
            pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageCount).create()
            page = doc.startPage(pageInfo)
            canvas = page.canvas
            y = 60f * scale
        }
        fun newPageIfNeeded(need: Float) {
            if (y + need > pageH - 58 * scale) newPage()
        }

        canvas.drawText(title, pageW / 2f, y, titlePaint); y += 24 * scale
        canvas.drawText("简错题自动生成 · 共 ${qs.size} 题 · ${fmtDate(System.currentTimeMillis())}",
            pageW / 2f, y, subPaint); y += 22 * scale
        canvas.drawText("姓名：＿＿＿＿＿＿    班级：＿＿＿＿＿＿    日期：＿＿＿＿＿＿",
            marginX, y, ansPaint); y += 24 * scale
        canvas.drawLine(marginX, y, pageW - marginX, y, dashPaint); y += 18 * scale

        fun pageBreakWrapped() {
            newPage()
        }

        qs.forEachIndexed { i, q ->
            newPageIfNeeded(140 * scale)
            // 题头：题号 + 科目（知识点可选）
            val kpTag = if (opts.withKnowledge && q.knowledge.isNotBlank()) " · ${q.knowledge}" else ""
            canvas.drawText("${i + 1}.（${q.subject}$kpTag）", marginX, y, qPaint); y += 19 * scale
            if (q.question.isNotBlank()) {
                y = drawWrapped(canvas, q.question, bodyPaint, marginX, y, contentW, pageH - 58 * scale) { pageBreakWrapped() }
                y += 4 * scale
            }
            // 题图：按真实宽高比缩放，宽度上限为版心，高度上限约半页（小图题不会占满整页）
            val img = Store.imgFile(q.imageFile)
            if (img != null) {
                try {
                    val bmp = BitmapFactory.decodeFile(img.absolutePath)
                    if (bmp != null && bmp.width > 0 && bmp.height > 0) {
                        val maxW = contentW
                        val maxH = (pageH - 116 * scale) * 0.62f   // 限高：不超过版心高度 62%
                        var bw = maxW
                        var bh = bmp.height.toFloat() * bw / bmp.width
                        if (bh > maxH) { bh = maxH; bw = bmp.width.toFloat() * bh / bmp.height }
                        newPageIfNeeded(bh + 14 * scale)
                        val scaled = Bitmap.createScaledBitmap(bmp, bw.toInt().coerceAtLeast(1), bh.toInt().coerceAtLeast(1), true)
                        canvas.drawBitmap(scaled, marginX + (maxW - bw) / 2f, y, null)
                        y += bh + 12 * scale
                        if (!bmp.isRecycled) bmp.recycle()
                    }
                } catch (_: Exception) {}
            }
            // 解析（可选）
            if (opts.withAnalysis && q.analysis.isNotBlank()) {
                y = drawWrapped(canvas, "【解析】" + q.analysis, ansPaint, marginX, y, contentW, pageH - 58 * scale) { pageBreakWrapped() }
                y += 4 * scale
            }
            // 作答区
            newPageIfNeeded(46 * scale)
            canvas.drawLine(marginX, y + 38 * scale, pageW - marginX, y + 38 * scale, dashPaint)
            canvas.drawText("答：", marginX + 4 * scale, y + 16 * scale, ansPaint)
            y += 48 * scale
        }

        // 答案页（可选）
        if (opts.withAnswerPage && qs.any { it.answer.isNotBlank() }) {
            newPage()
            canvas.drawText("参考答案", pageW / 2f, y, ansTitlePaint); y += 26 * scale
            canvas.drawLine(marginX, y, pageW - marginX, y, dashPaint); y += 16 * scale
            qs.forEachIndexed { i, q ->
                if (q.answer.isNotBlank()) {
                    newPageIfNeeded(60 * scale)
                    canvas.drawText("${i + 1}. ", marginX, y, qPaint)
                    y = drawWrapped(canvas, q.answer, bodyPaint, marginX + 20 * scale, y, contentW - 20 * scale, pageH - 58 * scale) { pageBreakWrapped() }
                    y += 6 * scale
                }
            }
        }
        doc.finishPage(page)

        val dir = File(context.cacheDir, "papers").apply { mkdirs() }
        val out = File(dir, "${title.take(20)}.pdf")
        FileOutputStream(out).use { doc.writeTo(it) }
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
