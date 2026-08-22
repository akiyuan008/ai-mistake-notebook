package com.jiancuoti.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiancuoti.app.img.Perspective
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** 一个选区（归一化坐标 0..1）：4 角点 */
data class Quad(
    val pts: MutableList<Offset> = mutableListOf(
        Offset(0.08f, 0.08f), Offset(0.92f, 0.08f),
        Offset(0.92f, 0.42f), Offset(0.08f, 0.42f)
    ),
    var group: Int = 0
)

val GROUP_COLORS = listOf(Color(0xFF7C3AED), Color(0xFFDB2777), Color(0xFF0D9488), Color(0xFFB45309))

@Composable
fun CropScreen(
    bitmap: Bitmap,
    pageIndex: Int,
    pageCount: Int,
    onExtract: (List<List<Offset>>) -> Unit,
    onSwitchPage: (Int) -> Unit,
    onBack: () -> Unit
) {
    var quads by remember { mutableStateOf(listOf(Quad())) }
    var stitchMode by remember { mutableStateOf(false) }
    var stitchGroup by remember { mutableStateOf<List<Int>>(emptyList()) }
    val activeQuad by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 顶栏
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("返回") }
            Spacer(Modifier.weight(1f))
            Text("第 ${pageIndex + 1} / $pageCount 页", fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            if (stitchMode) {
                Button(
                    onClick = {
                        if (stitchGroup.size >= 2) {
                            val gid = (quads.maxOfOrNull { it.group } ?: 0) + 1
                            val newQuads = quads.mapIndexed { i, q ->
                                if (stitchGroup.contains(i)) Quad(q.pts.toMutableList(), gid) else q
                            }
                            quads = newQuads
                        }
                        stitchMode = false; stitchGroup = emptyList()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                ) {
                    Text("完成拼接(${stitchGroup.size})", color = Color.White, fontSize = 12.sp)
                }
            } else {
                TextButton(onClick = { quads = quads + Quad() }) { Text("+加框") }
            }
        }

        // 画布区
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val boxW = constraints.maxWidth.toFloat()
            val boxH = constraints.maxHeight.toFloat()
            val imgAspect = bitmap.width.toFloat() / bitmap.height
            val boxAspect = boxW / boxH
            // 图片居中适配后的尺寸与偏移
            val (drawW, drawH) = if (imgAspect > boxAspect) {
                boxW to (boxW / imgAspect)
            } else {
                (boxH * imgAspect) to boxH
            }
            val offX = (boxW - drawW) / 2
            val offY = (boxH - drawH) / 2

            // 显示的图片（用 Canvas 画 bitmap 更可控）
            Canvas(Modifier.fillMaxSize()) {
                drawImage(
                    bitmap.asImageBitmap(),
                    srcSize = IntSize(bitmap.width, bitmap.height),
                    dstOffset = IntOffsetPx(offX, offY),
                    dstSize = IntSizePx(drawW, drawH)
                )
            }

            // 选区层
            Canvas(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { down ->
                            // 命中测试：角点 > 边中点 > 框内移动
                            val hit = hitTest(quads, down, offX, offY, drawW, drawH)
                            dragTarget = hit
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val t = dragTarget ?: return@detectDragGestures
                            val dx = dragAmount.x / drawW
                            val dy = dragAmount.y / drawH
                            val newQuads = quads.toMutableList()
                            val q = newQuads[t.quad]
                            when (t.type) {
                                HitType.CORNER -> {
                                    val p = q.pts[t.index]
                                    q.pts[t.index] = clampN(p + Offset(dx, dy))
                                }
                                HitType.EDGE -> {
                                    val a = (t.index); val b = (t.index + 1) % 4
                                    q.pts[a] = clampN(q.pts[a] + Offset(dx, dy))
                                    q.pts[b] = clampN(q.pts[b] + Offset(dx, dy))
                                }
                                HitType.MOVE -> {
                                    for (i in 0..3) q.pts[i] = clampN(q.pts[i] + Offset(dx, dy))
                                }
                                else -> {}
                            }
                            newQuads[t.quad] = q
                            quads = newQuads
                        },
                        onDragEnd = { dragTarget = null }
                    )
                }
            ) {
                quads.forEachIndexed { qi, q ->
                    val color = if (q.group > 0)
                        GROUP_COLORS[(q.group - 1) % GROUP_COLORS.size] else Color(0xFF38BDF8)
                    val ptsPx = q.pts.map { Offset(offX + it.x * drawW, offY + it.y * drawH) }
                    // 半透明填充
                    val path = Path().apply {
                        moveTo(ptsPx[0].x, ptsPx[0].y)
                        for (i in 1..3) lineTo(ptsPx[i].x, ptsPx[i].y)
                        close()
                    }
                    drawPath(path, color.copy(alpha = 0.12f))
                    drawPath(path, color, style = Stroke(width = 3f))
                    // 4 角点
                    ptsPx.forEach { p ->
                        drawCircle(Color.White, 9f, p)
                        drawCircle(color, 6f, p)
                    }
                    // 4 边中点
                    for (i in 0..3) {
                        val mid = Offset((ptsPx[i].x + ptsPx[(i + 1) % 4].x) / 2,
                            (ptsPx[i].y + ptsPx[(i + 1) % 4].y) / 2)
                        drawCircle(Color.White, 8f, mid)
                        drawCircle(color, 5f, mid)
                    }
                }
            }
        }

        // 底部操作栏
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                .navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (pageIndex > 0) {
                TextButton(onClick = { onSwitchPage(pageIndex - 1) }) { Text("上一页") }
            }
            Spacer(Modifier.weight(1f))
            if (!stitchMode && quads.size >= 2) {
                OutlinedButton(onClick = { stitchMode = true; stitchGroup = emptyList() }) {
                    Text("拼接模式", fontSize = 12.sp)
                }
                Spacer(Modifier.width(8.dp))
            }
            Button(onClick = {
                // 输出选区（含分组信息编码进返回）
                onExtract(quads.map { it.pts.toList() })
            }) {
                Text("批量提取(${quads.size})", color = Color.White)
            }
            if (pageIndex < pageCount - 1) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onSwitchPage(pageIndex + 1) }) { Text("下一页") }
            }
        }
    }
}

// 拖拽命中的临时状态
private var dragTarget: HitTarget? = null

enum class HitType { CORNER, EDGE, MOVE, NONE }
data class HitTarget(val quad: Int, val type: HitType, val index: Int = 0)

private fun IntOffsetPx(x: Float, y: Float) = androidx.compose.ui.unit.IntOffset(x.toInt(), y.toInt())
private fun IntSizePx(w: Float, h: Float) = IntSize(w.toInt(), h.toInt())

private fun clampN(p: Offset) = Offset(p.x.coerceIn(0f, 1f), p.y.coerceIn(0f, 1f))

/** 命中测试（屏幕像素 -> 归一化） */
private fun hitTest(
    quads: List<Quad>, down: Offset,
    offX: Float, offY: Float, drawW: Float, drawH: Float
): HitTarget {
    val threshold = 36f
    // 角点优先
    quads.forEachIndexed { qi, q ->
        q.pts.forEachIndexed { pi, p ->
            val px = Offset(offX + p.x * drawW, offY + p.y * drawH)
            if (hypot(down.x - px.x, down.y - px.y) < threshold)
                return HitTarget(qi, HitType.CORNER, pi)
        }
    }
    // 边中点
    quads.forEachIndexed { qi, q ->
        for (i in 0..3) {
            val a = q.pts[i]; val b = q.pts[(i + 1) % 4]
            val mid = Offset(offX + (a.x + b.x) / 2 * drawW, offY + (a.y + b.y) / 2 * drawH)
            if (hypot(down.x - mid.x, down.y - mid.y) < threshold)
                return HitTarget(qi, HitType.EDGE, i)
        }
    }
    // 框内
    quads.forEachIndexed { qi, q ->
        if (pointInQuad(down, q, offX, offY, drawW, drawH))
            return HitTarget(qi, HitType.MOVE)
    }
    return HitTarget(0, HitType.NONE)
}

private fun pointInQuad(p: Offset, q: Quad, offX: Float, offY: Float, w: Float, h: Float): Boolean {
    val pts = q.pts.map { Offset(offX + it.x * w, offY + it.y * h) }
    var inside = false
    var j = 3
    for (i in 0..3) {
        if ((pts[i].y > p.y) != (pts[j].y > p.y) &&
            p.x < (pts[j].x - pts[i].x) * (p.y - pts[i].y) / (pts[j].y - pts[i].y) + pts[i].x
        ) inside = !inside
        j = i
    }
    return inside
}
