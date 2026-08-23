package com.jiancuoti.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * 自绘线性 Dock 图标（Canvas 描边 1.8、圆角端点，简约高级）
 * type: 0 错题库 1 组卷 2 拍摄 3 统计 4 我的
 */
@Composable
fun DockIcon(type: Int, tint: Color, modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier) {
        val sc = size.width / 24f
        val sw = 1.8f * sc
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun pt(x: Float, y: Float) = Offset(x * sc, y * sc)
        when (type) {
            0 -> {
                // 圆角笔记本 + 书脊 + 文字线
                drawRoundRect(
                    tint,
                    topLeft = pt(5f, 3f),
                    size = Size(14f * sc, 18f * sc),
                    cornerRadius = CornerRadius(2.5f * sc),
                    style = stroke
                )
                drawLine(tint, pt(9f, 3.5f), pt(9f, 20.5f), sw, StrokeCap.Round)
                drawLine(tint, pt(12f, 9f), pt(16f, 9f), sw, StrokeCap.Round)
                drawLine(tint, pt(12f, 13f), pt(15f, 13f), sw, StrokeCap.Round)
            }
            1 -> {
                // 文档 + 折角 + 对勾
                val doc = Path().apply {
                    moveTo(14f * sc, 3f * sc)
                    lineTo(8f * sc, 3f * sc)
                    cubicTo(6.9f * sc, 3f * sc, 6f * sc, 3.9f * sc, 6f * sc, 5f * sc)
                    lineTo(6f * sc, 19f * sc)
                    cubicTo(6f * sc, 20.1f * sc, 6.9f * sc, 21f * sc, 8f * sc, 21f * sc)
                    lineTo(16f * sc, 21f * sc)
                    cubicTo(17.1f * sc, 21f * sc, 18f * sc, 20.1f * sc, 18f * sc, 19f * sc)
                    lineTo(18f * sc, 7f * sc)
                    close()
                }
                drawPath(doc, tint, style = stroke)
                val fold = Path().apply {
                    moveTo(14f * sc, 3f * sc)
                    lineTo(14f * sc, 7f * sc)
                    lineTo(18f * sc, 7f * sc)
                }
                drawPath(fold, tint, style = stroke)
                val check = Path().apply {
                    moveTo(9.3f * sc, 14.2f * sc)
                    lineTo(11.3f * sc, 16.2f * sc)
                    lineTo(14.8f * sc, 12.2f * sc)
                }
                drawPath(check, tint, style = stroke)
            }
            2 -> {
                // 相机机身 + 镜头
                val body = Path().apply {
                    moveTo(4f * sc, 8.5f * sc)
                    cubicTo(4f * sc, 7.4f * sc, 4.9f * sc, 6.5f * sc, 6f * sc, 6.5f * sc)
                    lineTo(7.6f * sc, 6.5f * sc)
                    lineTo(9f * sc, 4.5f * sc)
                    lineTo(15f * sc, 4.5f * sc)
                    lineTo(16.4f * sc, 6.5f * sc)
                    lineTo(18f * sc, 6.5f * sc)
                    cubicTo(19.1f * sc, 6.5f * sc, 20f * sc, 7.4f * sc, 20f * sc, 8.5f * sc)
                    lineTo(20f * sc, 17.5f * sc)
                    cubicTo(20f * sc, 18.6f * sc, 19.1f * sc, 19.5f * sc, 18f * sc, 19.5f * sc)
                    lineTo(6f * sc, 19.5f * sc)
                    cubicTo(4.9f * sc, 19.5f * sc, 4f * sc, 18.6f * sc, 4f * sc, 17.5f * sc)
                    close()
                }
                drawPath(body, tint, style = stroke)
                drawCircle(tint, radius = 3.4f * sc, center = pt(12f, 13f), style = stroke)
            }
            3 -> {
                // 坐标轴 + 上升折线 + 箭头
                val axis = Path().apply {
                    moveTo(4f * sc, 4f * sc)
                    lineTo(4f * sc, 20f * sc)
                    lineTo(20f * sc, 20f * sc)
                }
                drawPath(axis, tint, style = stroke)
                val line = Path().apply {
                    moveTo(7f * sc, 15.5f * sc)
                    lineTo(10.5f * sc, 11.5f * sc)
                    lineTo(13.2f * sc, 13.8f * sc)
                    lineTo(17.5f * sc, 8.5f * sc)
                }
                drawPath(line, tint, style = stroke)
                val arrow = Path().apply {
                    moveTo(17.5f * sc, 11.5f * sc)
                    lineTo(17.5f * sc, 8.5f * sc)
                    lineTo(14.5f * sc, 8.5f * sc)
                }
                drawPath(arrow, tint, style = stroke)
            }
            4 -> {
                // 头 + 肩
                drawCircle(tint, radius = 3.5f * sc, center = pt(12f, 7.5f), style = stroke)
                val shoulder = Path().apply {
                    moveTo(5.5f * sc, 19.5f * sc)
                    cubicTo(6.3f * sc, 16.2f * sc, 8.8f * sc, 14.5f * sc, 12f * sc, 14.5f * sc)
                    cubicTo(15.2f * sc, 14.5f * sc, 17.7f * sc, 16.2f * sc, 18.5f * sc, 19.5f * sc)
                }
                drawPath(shoulder, tint, style = stroke)
            }
        }
    }
}
