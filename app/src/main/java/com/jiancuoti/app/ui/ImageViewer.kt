package com.jiancuoti.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File
import kotlin.math.hypot

/**
 * 全屏图片查看器（盖住 Dock）：
 * - 未放大：单指左右滑动切题
 * - 放大后：单指拖动平移，双指缩放
 * - 旋转按钮
 */
@Composable
fun ImageViewer(
    images: List<File>,
    initialIndex: Int,
    titles: List<String> = emptyList(),
    onClose: () -> Unit
) {
    if (images.isEmpty()) { onClose(); return }
    val pagerState = rememberPagerState(initialPage = initialIndex.coerceIn(0, images.size - 1)) {
        images.size
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(pagerState.currentPage) {
        scale = 1f; offsetX = 0f; offsetY = 0f; rotation = 0f
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val img = images[page]
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(page) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var prevDist = 0f
                            var prevCentroid: Offset? = null
                            var twoFingerUsed = false
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                if (pressed.size >= 2) {
                                    // 双指：缩放 + 平移（优先于翻页）
                                    val cx = pressed.map { it.position.x }.average().toFloat()
                                    val cy = pressed.map { it.position.y }.average().toFloat()
                                    val centroid = Offset(cx, cy)
                                    val p0 = pressed[0].position
                                    val p1 = pressed[1].position
                                    val dist = hypot((p0.x - p1.x).toDouble(), (p0.y - p1.y).toDouble()).toFloat()
                                    if (prevDist > 0f) {
                                        val zoom = dist / prevDist
                                        scale = (scale * zoom).coerceIn(1f, 6f)
                                        twoFingerUsed = true
                                    }
                                    if (prevCentroid != null && scale > 1.05f) {
                                        offsetX += centroid.x - prevCentroid!!.x
                                        offsetY += centroid.y - prevCentroid!!.y
                                    }
                                    prevDist = dist
                                    prevCentroid = centroid
                                    pressed.forEach { it.consume() }
                                } else if (pressed.size == 1) {
                                    val c = pressed[0]
                                    if (twoFingerUsed || scale > 1.05f) {
                                        // 放大状态：单指平移，阻止翻页
                                        if (prevCentroid != null) {
                                            offsetX += c.position.x - prevCentroid!!.x
                                            offsetY += c.position.y - prevCentroid!!.y
                                        }
                                        prevCentroid = c.position
                                        prevDist = 0f
                                        c.consume()
                                    } else {
                                        // 未放大：不消费，交给 Pager 处理左右切换
                                        prevCentroid = c.position
                                        prevDist = 0f
                                    }
                                }
                            } while (true)
                            if (scale <= 1.05f) { offsetX = 0f; offsetY = 0f }
                        }
                    }
            ) {
                AsyncImage(
                    model = img,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY,
                            rotationZ = rotation
                        )
                )
            }
        }

        // 顶部：关闭 + 页码 + 标题 + 旋转
        Row(
            Modifier.fillMaxWidth().statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, null, tint = Color.White)
            }
            Text(
                buildString {
                    append("${pagerState.currentPage + 1} / ${images.size}")
                    if (titles.getOrNull(pagerState.currentPage)?.isNotBlank() == true) {
                        append("  ·  ")
                        append(lastKnowledge(titles[pagerState.currentPage]).take(18))
                    }
                },
                color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { rotation = (rotation + 90f) % 360f }) {
                Icon(Icons.Default.RotateRight, null, tint = Color.White)
            }
        }

        // 底部提示
        if (pagerState.currentPage == 0 && images.size > 1) {
            Text(
                "单指左右滑切换 · 双指缩放 · 放大后拖动",
                color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding().padding(bottom = 18.dp)
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            )
        }
    }
}
