package com.jiancuoti.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.hypot
import kotlin.math.roundToInt

/** 拼接组颜色 */
val GROUP_COLORS = listOf(Color(0xFF7C3AED), Color(0xFFDB2777), Color(0xFF0D9488), Color(0xFFB45309))
private val HANDLE_BLUE = Color(0xFF0EA5E9)

/** 一个选区（归一化坐标 + 所属页 + 拼接组） */
class QuadState(
    val id: Int,
    val page: Int,
    val pts: SnapshotStateList<Offset>,
    var group: Int = 0
)

/** 提取项的一个部分：第几页 + 四角归一化坐标 */
data class CropPart(val page: Int, val pts: List<Offset>)

/** 拖动目标 */
private sealed class DragTarget {
    data class Handle(val quad: QuadState, val index: Int, val edge: Boolean) : DragTarget()
    data class Move(val quad: QuadState) : DragTarget()
}

/**
 * 框选页（高性能单画布版本）：
 * - 只有一个 Canvas 绘制全部选区/手柄/遮罩，拖动只改 SnapshotStateList，不重建列表
 * - 选区按页管理，切换页保留；拼接可跨页（把两页上的同一题合成一题）
 * - onExtract：每个元素是一道题（拼接项含多个部分，按页码+位置排序）
 */
@Composable
fun CropScreen(
    displayBitmaps: List<Bitmap>,
    pageIndex: Int,
    onExtract: (List<List<CropPart>>) -> Unit,
    onSwitchPage: (Int) -> Unit,
    onBack: () -> Unit
) {
    var nextId by remember { mutableIntStateOf(0) }
    var quads by remember { mutableStateOf(listOf<QuadState>()) }
    var stitching by remember { mutableStateOf(false) }
    var stitchSel by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val density = LocalDensity.current
    val touchRadius = with(density) { 26.dp.toPx() }
    val handleRadius = with(density) { 7.dp.toPx() }
    var dragTarget by remember { mutableStateOf<DragTarget?>(null) }

    val bitmap = displayBitmaps[pageIndex]

    // 初始给当前页一个选区
    LaunchedEffect(pageIndex) {
        if (quads.none { it.page == pageIndex }) {
            quads = quads + QuadState(nextId++, pageIndex, mutableStateListOf(
                Offset(0.08f, 0.10f), Offset(0.92f, 0.10f),
                Offset(0.92f, 0.44f), Offset(0.08f, 0.44f)
            ))
        }
    }

    val pageQuads = quads.filter { it.page == pageIndex }

    Column(Modifier.fillMaxSize().background(Color(0xFF0B1220))) {
        // 顶栏
        Row(
            Modifier.fillMaxWidth().statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("返回", color = Color.White) }
            Spacer(Modifier.weight(1f))
            Text(
                if (displayBitmaps.size > 1) "第 ${pageIndex + 1} / ${displayBitmaps.size} 页" else "框选题目",
                color = Color.White, fontSize = 14.sp
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                quads = quads + QuadState(nextId++, pageIndex, mutableStateListOf(
                    Offset(0.25f, 0.55f), Offset(0.75f, 0.55f),
                    Offset(0.75f, 0.85f), Offset(0.25f, 0.85f)
                ))
            }) { Text("+选区", color = Color.White) }
        }

        if (stitching) {
            Text(
                "拼接模式：点选 ≥2 个选区（可跨页），完成后合成一道题。已选 ${stitchSel.size} 个",
                color = Color(0xFFFDE68A), fontSize = 11.5.sp,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp)
            )
        }

        // 裁剪画布（单个 Canvas，高性能）
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val boxW = constraints.maxWidth.toFloat()
            val boxH = constraints.maxHeight.toFloat()
            val imgAspect = bitmap.width.toFloat() / bitmap.height
            val boxAspect = boxW / boxH
            val drawW: Float; val drawH: Float
            if (imgAspect > boxAspect) { drawW = boxW; drawH = boxW / imgAspect }
            else { drawH = boxH; drawW = boxH * imgAspect }
            val offX = (boxW - drawW) / 2
            val offY = (boxH - drawH) / 2

            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

            // 手势层：命中检测（手柄优先，其次选区整体移动）
            fun toN(p: Offset) = Offset((p.x - offX) / drawW, (p.y - offY) / drawH)
            fun toPx(n: Offset) = Offset(offX + n.x * drawW, offY + n.y * drawH)

            Canvas(
                Modifier.fillMaxSize().pointerInput(pageIndex, quads.size, stitching) {
                    detectDragGestures(
                        onDragStart = { down ->
                            dragTarget = null
                            // 1) 命中手柄（角点/边中点），按倒序（上层优先）
                            for (qi in pageQuads.indices.reversed()) {
                                val q = pageQuads[qi]
                                // 角点
                                for (i in 0..3) {
                                    if (hypot(down.x - toPx(q.pts[i]).x, down.y - toPx(q.pts[i]).y) < touchRadius) {
                                        dragTarget = DragTarget.Handle(q, i, false); return@detectDragGestures
                                    }
                                }
                                // 边中点
                                for (i in 0..3) {
                                    val a = q.pts[i]; val b = q.pts[(i + 1) % 4]
                                    val m = toPx(Offset((a.x + b.x) / 2, (a.y + b.y) / 2))
                                    if (hypot(down.x - m.x, down.y - m.y) < touchRadius) {
                                        dragTarget = DragTarget.Handle(q, i, true); return@detectDragGestures
                                    }
                                }
                            }
                            // 2) 命中选区内部 → 整体移动（倒序，后加的在上层）
                            for (qi in pageQuads.indices.reversed()) {
                                val q = pageQuads[qi]
                                if (pointInQuadN(toN(down), q.pts)) {
                                    dragTarget = DragTarget.Move(q); return@detectDragGestures
                                }
                            }
                        },
                        onDrag = { change, drag ->
                            change.consume()
                            val t = dragTarget ?: return@detectDragGestures
                            val dx = drag.x / drawW; val dy = drag.y / drawH
                            when (t) {
                                is DragTarget.Handle -> {
                                    val q = t.quad
                                    if (t.edge) {
                                        // 边中点：拖动整条边
                                        val a = t.index; val b = (t.index + 1) % 4
                                        val pa = q.pts[a]; val pb = q.pts[b]
                                        q.pts[a] = Offset((pa.x + dx).coerceIn(0f, 1f), (pa.y + dy).coerceIn(0f, 1f))
                                        q.pts[b] = Offset((pb.x + dx).coerceIn(0f, 1f), (pb.y + dy).coerceIn(0f, 1f))
                                    } else {
                                        val p = q.pts[t.index]
                                        q.pts[t.index] = Offset((p.x + dx).coerceIn(0f, 1f), (p.y + dy).coerceIn(0f, 1f))
                                    }
                                }
                                is DragTarget.Move -> {
                                    val q = t.quad
                                    for (i in 0..3) {
                                        val p = q.pts[i]
                                        q.pts[i] = Offset((p.x + dx).coerceIn(0f, 1f), (p.y + dy).coerceIn(0f, 1f))
                                    }
                                }
                            }
                        },
                        onDragEnd = { dragTarget = null },
                        onDragCancel = { dragTarget = null }
                    )
                }
            ) {
                // 暗色遮罩 + 挖洞（even-odd）
                val mask = Path().apply { fillType = PathFillType.EvenOdd }
                mask.addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                for (q in pageQuads) {
                    val p0 = toPx(q.pts[0])
                    mask.moveTo(p0.x, p0.y)
                    for (i in 1..3) {
                        val p = toPx(q.pts[i]); mask.lineTo(p.x, p.y)
                    }
                    mask.close()
                }
                drawPath(mask, Color.Black.copy(alpha = 0.4f), style = Fill)

                // 选区边框 + 手柄
                for (q in pageQuads) {
                    val color = if (q.group > 0) GROUP_COLORS[(q.group - 1) % GROUP_COLORS.size] else HANDLE_BLUE
                    val selected = stitchSel.contains(q.id)
                    val path = Path().apply {
                        val p0 = toPx(q.pts[0]); moveTo(p0.x, p0.y)
                        for (i in 1..3) { val p = toPx(q.pts[i]); lineTo(p.x, p.y) }
                        close()
                    }
                    if (selected) drawPath(path, Color(0xFF7C3AED).copy(alpha = 0.25f))
                    drawPath(path, color, style = Stroke(width = if (selected) 6f else 3f))
                    // 手柄：4 角 + 4 边中点
                    for (i in 0..3) {
                        drawCircle(Color.White, radius = handleRadius, center = toPx(q.pts[i]))
                        drawCircle(color, radius = handleRadius * 0.55f, center = toPx(q.pts[i]))
                    }
                    for (i in 0..3) {
                        val a = q.pts[i]; val b = q.pts[(i + 1) % 4]
                        val m = toPx(Offset((a.x + b.x) / 2, (a.y + b.y) / 2))
                        drawCircle(Color.White, radius = handleRadius * 0.8f, center = m)
                        drawCircle(color, radius = handleRadius * 0.45f, center = m)
                    }
                }
            }

            // 每个选区上方的悬浮菜单（轻量，offset 延迟读取）
            pageQuads.forEach { q ->
                val menuW = (if (stitching) 70 else 118).dp
                Row(
                    modifier = Modifier
                        .offset {
                            val topY = q.pts.minOf { it.y }
                            val cx = q.pts.map { it.x }.average().toFloat()
                            IntOffset(
                                (offX + cx * drawW - with(density) { (menuW / 2).toPx() }).roundToInt(),
                                (offY + topY * drawH - with(density) { 40.dp.toPx() }).roundToInt().coerceAtLeast(8)
                            )
                        },
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (stitching) {
                        val on = stitchSel.contains(q.id)
                        SmallBtn(if (on) "已选" else "拼接", if (on) Color(0xFF7C3AED) else Color(0xFFF59E0B)) {
                            stitchSel = if (on) stitchSel - q.id else stitchSel + q.id
                        }
                    } else {
                        SmallBtn("拼接", Color(0xFFF59E0B)) {
                            stitching = true
                            stitchSel = setOf(q.id)
                        }
                        SmallBtn("×", Color(0xFFF43F5E)) {
                            quads = quads.filter { it.id != q.id }
                            stitchSel = stitchSel - q.id
                        }
                    }
                }
            }
        }

        // 底部操作栏
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (stitching) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    OutlinedButton(
                        onClick = { stitching = false; stitchSel = emptySet() },
                        modifier = Modifier.weight(1f)
                    ) { Text("取消", color = Color.White) }
                    Button(
                        onClick = {
                            if (stitchSel.size >= 2) {
                                val gid = (quads.maxOfOrNull { it.group } ?: 0) + 1
                                quads.filter { stitchSel.contains(it.id) }.forEach { it.group = gid }
                            }
                            stitching = false
                            stitchSel = emptySet()
                        },
                        enabled = stitchSel.size >= 2,
                        modifier = Modifier.weight(2f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                    ) {
                        Text("完成拼接 (${stitchSel.size})", color = Color.White)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pageIndex > 0) {
                    OutlinedButton(onClick = { onSwitchPage(pageIndex - 1) },
                        modifier = Modifier.weight(1f)) { Text("上一页", color = Color.White) }
                }
                if (pageIndex < displayBitmaps.size - 1) {
                    OutlinedButton(onClick = { onSwitchPage(pageIndex + 1) },
                        modifier = Modifier.weight(1f)) { Text("下一页", color = Color.White) }
                }
                Button(
                    onClick = {
                        // group=0 → 各自成题；group>0 → 同组合成一道（按页+位置排序）
                        val singles = quads.filter { it.group == 0 }
                            .map { listOf(CropPart(it.page, it.pts.toList())) }
                        val grouped = quads.filter { it.group > 0 }
                            .groupBy { it.group }
                            .map { (_, qs) ->
                                qs.sortedWith(compareBy({ it.page }, { it.pts.minOf { p -> p.y } }))
                                    .map { CropPart(it.page, it.pts.toList()) }
                            }
                        onExtract(singles + grouped)
                    },
                    modifier = Modifier.weight(2f),
                    colors = ButtonDefaults.buttonColors(containerColor = SkyPrimary)
                ) {
                    Text("批量提取 (${quads.size})", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun SmallBtn(text: String, bg: Color, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(bg)
            .clickableNoRipple(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(text, color = Color.White, fontSize = 11.sp)
    }
}

private fun pointInQuadN(p: Offset, pts: List<Offset>): Boolean {
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
