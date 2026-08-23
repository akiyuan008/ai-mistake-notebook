package com.jiancuoti.app.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.jiancuoti.app.data.Mistake
import com.jiancuoti.app.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** 导出选项 */
data class ExportOpts(
    val withAnswerPage: Boolean = true,
    val withKnowledge: Boolean = true,
    val withAnalysis: Boolean = false,
    val imagesOnly: Boolean = false,
    val withImage: Boolean = true
)

/**
 * 一站式导出（组卷与单题共用）：
 * - imagesOnly：题目图片直接保存到系统相册
 * - 否则：生成 PDF 并调起分享/打印
 * 返回提示文案
 */
suspend fun generateAndShare(context: Context, title: String, qs: List<Mistake>, opts: ExportOpts): String {
    if (opts.imagesOnly) {
        val imgs = qs.mapNotNull { Store.imgFile(it.imageFile) }
        if (imgs.isEmpty()) return "所选题目没有图片"
        val n = withContext(Dispatchers.IO) { saveFilesToGallery(context, imgs) }
        return if (n > 0) "已保存 $n 张图片到相册" else "保存到相册失败"
    }
    val file = generatePaperPdf(context, title, qs, opts) ?: return "生成失败"
    return try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "打印 / 分享试卷"))
        ""
    } catch (e: Exception) { "分享失败：${e.message}" }
}

/** 保存图片文件到系统相册（MediaStore），返回成功张数 */
fun saveFilesToGallery(context: Context, files: List<File>): Int {
    var ok = 0
    for (f in files) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, f.nameWithoutExtension + ".jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/简错题")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: continue
            context.contentResolver.openOutputStream(uri)?.use { out ->
                f.inputStream().use { it.copyTo(out) }
            }
            if (Build.VERSION.SDK_INT >= 29) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            }
            ok++
        } catch (_: Exception) {}
    }
    return ok
}

/** 保存单张位图到相册 */
fun saveBitmapToGallery(context: Context, bmp: Bitmap, name: String): Boolean {
    return try {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/简错题")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        context.contentResolver.openOutputStream(uri)?.use {
            bmp.compress(Bitmap.CompressFormat.JPEG, 95, it)
        }
        if (Build.VERSION.SDK_INT >= 29) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        }
        true
    } catch (_: Exception) { false }
}

/** 导出选项弹窗（组卷/单题共用） */
@Composable
fun TitleDialog(onClose: () -> Unit, onConfirm: (String, ExportOpts) -> Unit) {
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
                var withAnswerPage by remember { mutableStateOf(true) }
                var withKnowledge by remember { mutableStateOf(true) }
                var withAnalysis by remember { mutableStateOf(false) }
                var imagesOnly by remember { mutableStateOf(false) }
                var withImage by remember { mutableStateOf(true) }
                Text("导出内容", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                LabeledCheckbox(imagesOnly, "仅导出题目图片（直接保存到相册）") { imagesOnly = it }
                if (!imagesOnly) {
                    LabeledCheckbox(withAnswerPage, "附答案页（卷末汇总）") { withAnswerPage = it }
                    LabeledCheckbox(withKnowledge, "打印知识点标注") { withKnowledge = it }
                    LabeledCheckbox(withAnalysis, "打印解析步骤") { withAnalysis = it }
                    LabeledCheckbox(withImage, "卷面附题目原图") { withImage = it }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClose) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        onConfirm(
                            title.ifBlank { "错题复习卷" },
                            ExportOpts(
                                withAnswerPage = !imagesOnly && withAnswerPage,
                                withKnowledge = !imagesOnly && withKnowledge,
                                withAnalysis = !imagesOnly && withAnalysis,
                                imagesOnly = imagesOnly,
                                withImage = withImage
                            )
                        )
                    }) {
                        Text("确定并生成", color = androidx.compose.ui.graphics.Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun LabeledCheckbox(checked: Boolean, label: String, onChange: (Boolean) -> Unit) {
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

/** 原生 PDF 生成（A4：595x842pt，2 倍绘制） */
suspend fun generatePaperPdf(
    context: Context, title: String, qs: List<Mistake>, opts: ExportOpts
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

        fun pageBreakWrapped() { newPage() }

        qs.forEachIndexed { i, q ->
            newPageIfNeeded(90 * scale)
            val kpTag = if (opts.withKnowledge && q.knowledge.isNotBlank()) " · ${q.knowledge}" else ""
            canvas.drawText("${i + 1}.（${q.subject}$kpTag）", marginX, y, qPaint); y += 16 * scale
            if (q.question.isNotBlank()) {
                y = drawWrapped(canvas, renderMixedText(q.question), bodyPaint, marginX, y, contentW, pageH - 58 * scale) { pageBreakWrapped() }
                y += 3 * scale
            }
            val img = if (opts.withImage) Store.imgFile(q.imageFile) else null
            if (img != null) {
                try {
                    val bmp = BitmapFactory.decodeFile(img.absolutePath)
                    if (bmp != null && bmp.width > 0 && bmp.height > 0) {
                        val maxW = contentW * 0.9f
                        val maxH = (pageH - 116 * scale) * 0.30f
                        var bw = maxW
                        var bh = bmp.height.toFloat() * bw / bmp.width
                        if (bh > maxH) { bh = maxH; bw = bmp.width.toFloat() * bh / bmp.height }
                        newPageIfNeeded(bh + 10 * scale)
                        val scaled = Bitmap.createScaledBitmap(bmp, bw.toInt().coerceAtLeast(1), bh.toInt().coerceAtLeast(1), true)
                        canvas.drawBitmap(scaled, marginX + (contentW - bw) / 2f, y, null)
                        y += bh + 9 * scale
                        if (!bmp.isRecycled) bmp.recycle()
                    }
                } catch (_: Exception) {}
            }
            if (opts.withAnalysis && q.analysis.isNotBlank()) {
                y = drawWrapped(canvas, "【解析】" + renderMixedText(q.analysis), ansPaint, marginX, y, contentW, pageH - 58 * scale) { pageBreakWrapped() }
                y += 3 * scale
            }
            newPageIfNeeded(36 * scale)
            canvas.drawLine(marginX, y + 30 * scale, pageW - marginX, y + 30 * scale, dashPaint)
            canvas.drawText("答：", marginX + 4 * scale, y + 14 * scale, ansPaint)
            y += 36 * scale
        }

        if (opts.withAnswerPage && qs.any { it.answer.isNotBlank() }) {
            newPage()
            canvas.drawText("参考答案", pageW / 2f, y, ansTitlePaint); y += 26 * scale
            canvas.drawLine(marginX, y, pageW - marginX, y, dashPaint); y += 16 * scale
            qs.forEachIndexed { i, q ->
                if (q.answer.isNotBlank()) {
                    newPageIfNeeded(60 * scale)
                    canvas.drawText("${i + 1}. ", marginX, y, qPaint)
                    y = drawWrapped(canvas, renderMixedText(q.answer), bodyPaint, marginX + 20 * scale, y, contentW - 20 * scale, pageH - 58 * scale) { pageBreakWrapped() }
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
