package com.jiancuoti.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File

/**
 * 全屏图片查看器：
 * - 双指缩放 + 拖动
 * - 左右滑切换（配合筛选快速回看错题）
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
    // 每页独立的缩放/旋转状态
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    // 翻页时重置变换
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
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 6f)
                            if (scale > 1.05f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f; offsetY = 0f
                            }
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

        // 顶部：关闭 + 页码 + 标题
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
                        append(titles[pagerState.currentPage].take(18))
                    }
                },
                color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            // 旋转
            IconButton(onClick = { rotation = (rotation + 90f) % 360f }) {
                Icon(Icons.Default.RotateRight, null, tint = Color.White)
            }
        }

        // 底部提示（仅第一页显示）
        if (pagerState.currentPage == 0 && images.size > 1) {
            Text(
                "左右滑动切换 · 双指缩放",
                color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding().padding(bottom = 18.dp)
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            )
        }
    }
}
