package com.jiancuoti.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.hypot
import kotlin.math.roundToInt

/** 选区：归一化坐标 4 角点 + 拼接组 */
class QuadState(
    val pts: androidx.compose.runtime.snapshots.SnapshotStateList<Offset>,
    var group: Int = 0
)

val GROUP_COLORS = listOf(Color(0xFF7C3AED), Color(0xFFDB2777), Color(0xFF0D9488), Color(0xFFB45309))
private val HANDLE_BLUE = Color(0xFF0EA5E9)

@Composable
fun CropScreen(
    displayBitmap: Bitmap,
    pageIndex: Int,
    pageCount: Int,
    stitching: Boolean,
    onExtract: (List<Pair<List<Offset>, Int>>) -> Unit,
    onSwitchPage: (Int) -> Unit,
    onBack: () -> Unit,
    onToggleStitch: () -> Unit
) {
    var quads by remember {
        mutableStateOf(listOf(
            QuadState(mutableStateListOf(
                Offset(0.08f, 0.08f), Offset(0.92f, 0.08f),
                Offset(0.92f, 0.42f), Offset(0.08f, 0.42f)
            ))
        ))
    }
    var stitchSel by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val density = LocalDensity.current
    val dpPx = with(density) { 1.dp.toPx() }

    Column(
        Modifier.fillMaxSize()
            .background(Color(0xFF0B1220))
    ) {
        // 顶栏
        Row(
            Modifier.fillMaxWidth().statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("返回", color = Color.White)
            }
            Spacer(Modifier.weight(1f))
            Text(
                if (pageCount > 1) "第 ${pageIndex + 1} / $pageCount 页" else "框选题目",
                color = Color.White, fontSize = 14.sp
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                quads = quads + QuadState(mutableStateListOf(
                    Offset(0.25f, 0.55f), Offset(0.75f, 0.55f),
                    Offset(0.75f, 0.85f), Offset(0.25f, 0.85f)
                ))
            }) {
                Text("+选区", color = Color.White)
            }
        }

        // 裁剪画布
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val boxW = constraints.maxWidth.toFloat()
            val boxH = constraints.maxHeight.toFloat()
            val imgAspect = displayBitmap.width.toFloat() / displayBitmap.height
            val boxAspect = boxW / boxH
            val drawW: Float; val drawH: Float
            if (imgAspect > boxAspect) { drawW = boxW; drawH = boxW / imgAspect }
            else { drawH = boxH; drawW = boxH * imgAspect }
            val offX = (boxW - drawW) / 2
            val offY = (boxH - drawH) / 2

            // 底图（静态，只画一次）
            androidx.compose.foundation.Image(
                bitmap = displayBitmap.asImageBitmapCompat(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
            // 暗色遮罩（画布外区域）
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Color.Black.copy(alpha = 0.35f))
            }

            // 每个选区
            quads.forEachIndexed { qi, q ->
                val color = if (q.group > 0) GROUP_COLORS[(q.group - 1) % GROUP_COLORS.size]
                            else HANDLE_BLUE
                val inStitch = stitchSel.contains(qi)

                // 多边形 + 整体移动手势
                Canvas(
                    Modifier.fillMaxSize().pointerInput(qi) {
                        detectDragGestures(
                            onDragStart = { down ->
                                val nx = (down.x - offX) / drawW
                                val ny = (down.y - offY) / drawH
                                if (!pointInQuadN(Offset(nx, ny), q.pts)) {
                                    dragQuad = -1
                                } else dragQuad = qi
                            },
                            onDrag = { change, drag ->
                                change.consume()
                                if (dragQuad != qi) return@detectDragGestures
                                val dx = drag.x / drawW; val dy = drag.y / drawH
                                for (i in 0..3) {
                                    val p = q.pts[i]
                                    q.pts[i] = Offset(
                                        (p.x + dx).coerceIn(0f, 1f),
                                        (p.y + dy).coerceIn(0f, 1f)
                                    )
                                }
                                quads = quads.toMutableList().also { it[qi] = q }
                            },
                            onDragEnd = { dragQuad = -1 }
                        )
                    }
                ) {
                    val px = q.pts.map { Offset(offX + it.x * drawW, offY + it.y * drawH) }
                    val path = Path().apply {
                        moveTo(px[0].x, px[0].y)
                        for (i in 1..3) lineTo(px[i].x, px[i].y)
                        close()
                    }
                    // 清除选区内遮罩效果：画亮色填充
                    drawPath(path, Color.White.copy(alpha = if (inStitch) 0.28f else 0.14f))
                    drawPath(path, color, style = Stroke(width = 3f))
                    if (inStitch) drawPath(path, Color(0xFF7C3AED), style = Stroke(width = 5f))
                }

                // 8 个手柄（独立拖动）
                for (i in 0..3) {
                    // 角点
                    HandleDot(
                        color = color, isEdge = false,
                        posPx = {
                            val p = q.pts[i]
                            Offset(offX + p.x * drawW, offY + p.y * drawH)
                        },
                        onDrag = { d ->
                            val p = q.pts[i]
                            q.pts[i] = Offset(
                                (p.x + d.x / drawW).coerceIn(0f, 1f),
                                (p.y + d.y / drawH).coerceIn(0f, 1f)
                            )
                            quads = quads.toMutableList().also { it[qi] = q }
                        }
                    )
                    // 边中点
                    val a = i; val b = (i + 1) % 4
                    HandleDot(
                        color = color, isEdge = true,
                        posPx = {
                            val pa = q.pts[a]; val pb = q.pts[b]
                            Offset(offX + (pa.x + pb.x) / 2 * drawW, offY + (pa.y + pb.y) / 2 * drawH)
                        },
                        onDrag = { d ->
                            val dx = d.x / drawW; val dy = d.y / drawH
                            val pa = q.pts[a]; val pb = q.pts[b]
                            q.pts[a] = Offset((pa.x + dx).coerceIn(0f, 1f), (pa.y + dy).coerceIn(0f, 1f))
                            q.pts[b] = Offset((pb.x + dx).coerceIn(0f, 1f), (pb.y + dy).coerceIn(0f, 1f))
                            quads = quads.toMutableList().also { it[qi] = q }
                        }
                    )
                }

                // 悬浮菜单（选区上方）
                val topY = q.pts.minOf { it.y }
                val cx = q.pts.map { it.x }.average().toFloat()
                val menuX = (offX + cx * drawW).roundToInt()
                val menuY = (offY + topY * drawH - 44 * dpPx).roundToInt().coerceAtLeast(8)
                Row(
                    modifier = Modifier.offset { IntOffset(menuX - (90 * dpPx).roundToInt(), menuY) },
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (stitching) {
                        SmallBtn(if (inStitch) "已选" else "拼接",
                            if (inStitch) Color(0xFF7C3AED) else Color(0xFFF59E0B)) {
                            stitchSel = if (inStitch) stitchSel - qi else stitchSel + qi
                        }
                    } else {
                        SmallBtn("拼接", Color(0xFFF59E0B)) {
                            onToggleStitch()
                            stitchSel = setOf(qi)
                        }
                        SmallBtn("×", Color(0xFFF43F5E)) {
                            quads = quads.filterIndexed { idx, _ -> idx != qi }
                            stitchSel = emptySet()
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
                Button(
                    onClick = {
                        if (stitchSel.size >= 2) {
                            val gid = (quads.maxOfOrNull { it.group } ?: 0) + 1
                            stitchSel.forEach { qi -> quads[qi].group = gid }
                        }
                        onToggleStitch()
                        stitchSel = emptySet()
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                ) {
                    Text("完成拼接 (${stitchSel.size})", color = Color.White)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pageIndex > 0) {
                    OutlinedButton(onClick = { onSwitchPage(pageIndex - 1) },
                        modifier = Modifier.weight(1f)) {
                        Text("上一页", color = Color.White)
                    }
                }
                if (pageIndex < pageCount - 1) {
                    OutlinedButton(onClick = { onSwitchPage(pageIndex + 1) },
                        modifier = Modifier.weight(1f)) {
                        Text("下一页", color = Color.White)
                    }
                }
                Button(
                    onClick = {
                        onExtract(quads.map { it.pts.toList() to it.group })
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

private var dragQuad = -1

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

@Composable
private fun HandleDot(
    color: Color, isEdge: Boolean,
    posPx: () -> Offset,
    onDrag: (Offset) -> Unit
) {
    val density = LocalDensity.current
    val sizePx = with(density) { (if (isEdge) 20.dp else 18.dp).toPx() }
    Box(
        modifier = Modifier
            .offset {
                val p = posPx()
                IntOffset(p.x.roundToInt() - sizePx.roundToInt() / 2, p.y.roundToInt() - sizePx.roundToInt() / 2)
            }
            .size(if (isEdge) 20.dp else 18.dp)
            .clip(CircleShape)
            .background(Color.White)
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    onDrag(drag)
                }
            }
    ) {
        Box(
            modifier = Modifier.align(Alignment.Center)
                .size(if (isEdge) 13.dp else 11.dp)
                .clip(CircleShape)
                .background(color)
        )
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

@Composable
private fun Bitmap.asImageBitmapCompat(): androidx.compose.ui.graphics.ImageBitmap =
    this.asImageBitmap()
