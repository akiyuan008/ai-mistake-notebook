package com.jiancuoti.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiancuoti.app.data.Store
import java.util.Calendar

/**
 * 统计页（多邻国风格）：大数字卡片 + 连续学习 + 周柱状图 + 掌握度圆环 + 科目条
 */
@Composable
fun StatsScreen() {
    val rev = Store.revision.intValue
    val ms = remember(rev) { Store.mistakes.toList() }

    val mastered = ms.count { it.mastered }
    val totalErr = ms.sumOf { it.errorCount }
    val bySubject = remember(rev) { ms.groupingBy { it.subject }.eachCount() }
    val maxSub = (bySubject.values.maxOrNull() ?: 1).coerceAtLeast(1)
    val topKp = remember(rev) {
        ms.filter { it.knowledge.isNotBlank() }
            .groupingBy { it.knowledge }.aggregate { _, acc: Int?, m, _ -> (acc ?: 0) + m.errorCount }
            .entries.sortedByDescending { it.value }.take(6)
    }

    // 最近 7 天数据
    val days = remember(rev) {
        (6 downTo 0).map { back ->
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_YEAR, -back)
            }
            val s = cal.timeInMillis; val e = s + 86400000L
            val n = ms.count { it.createdAt >= s && it.createdAt < e }
            val wd = when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "一"; Calendar.TUESDAY -> "二"; Calendar.WEDNESDAY -> "三"
                Calendar.THURSDAY -> "四"; Calendar.FRIDAY -> "五"; Calendar.SATURDAY -> "六"
                else -> "日"
            }
            Triple(wd, n, back == 0)
        }
    }
    val maxDay = days.maxOf { it.second }.coerceAtLeast(1)

    // 连续学习天数
    val streak = remember(rev) {
        var count = 0
        var offset = 0
        // 今天还没有记录时，从昨天开始算
        val todayHas = ms.any {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            it.createdAt >= cal.timeInMillis
        }
        if (!todayHas) offset = 1
        while (true) {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_YEAR, -(offset + count))
            }
            val s = cal.timeInMillis; val e = s + 86400000L
            if (ms.any { it.createdAt >= s && it.createdAt < e }) count++
            else break
        }
        count
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(6.dp))

        // 顶部三连卡片
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ColorStatCard(
                num = ms.size, label = "错题总数",
                brush = Brush.linearGradient(listOf(Color(0xFF38BDF8), Color(0xFF0284C7))),
                modifier = Modifier.weight(1f)
            )
            ColorStatCard(
                num = streak, label = "连续学习·天",
                brush = Brush.linearGradient(listOf(Color(0xFFFBBF24), Color(0xFFF59E0B))),
                modifier = Modifier.weight(1f)
            )
            ColorStatCard(
                num = mastered, label = "已掌握",
                brush = Brush.linearGradient(listOf(Color(0xFF34D399), Color(0xFF059669))),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))

        // 本周学习柱状图
        GlassCard(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("最近 7 天", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    Text("共 ${days.sumOf { it.second }} 题", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth().height(130.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    days.forEach { (wd, n, isToday) ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (n > 0) "$n" else "",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isToday) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            val hFrac = if (n == 0) 0.06f else (n.toFloat() / maxDay).coerceIn(0.12f, 1f)
                            Box(
                                Modifier.fillMaxWidth(0.55f)
                                    .weight(1f),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Box(
                                    Modifier.fillMaxWidth()
                                        .fillMaxHeight(hFrac)
                                        .clip(RoundedCornerShape(percent = 50))
                                        .background(
                                            if (isToday) Brush.verticalGradient(
                                                listOf(Color(0xFF38BDF8), Color(0xFF0284C7)))
                                            else Brush.verticalGradient(
                                                listOf(
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                                ))
                                        )
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (isToday) "今天" else wd,
                                fontSize = 11.sp,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                color = if (isToday) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 掌握度圆环 + 科目分布
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // 圆环
            GlassCard(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(0.85f)
            ) {
                Column(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("掌握度", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    val frac = if (ms.isEmpty()) 0f else mastered.toFloat() / ms.size
                    val ringBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val sw = 10.dp.toPx()
                            drawCircle(
                                color = ringBg,
                                style = Stroke(width = sw)
                            )
                            if (frac > 0f) {
                                drawArc(
                                    brush = Brush.linearGradient(
                                        listOf(Color(0xFF34D399), Color(0xFF059669))),
                                    startAngle = -90f,
                                    sweepAngle = 360f * frac,
                                    useCenter = false,
                                    style = Stroke(width = sw, cap = StrokeCap.Round)
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${(frac * 100).toInt()}%",
                                fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF059669)
                            )
                            Text("已掌握", fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "$mastered / ${ms.size} 题",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // 累计错误 + 高频
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniStatTile("累计错误", "$totalErr", Color(0xFFF43F5E))
                MiniStatTile("高频错题", "${ms.count { it.errorCount >= 2 }}", Color(0xFFF59E0B))
            }
        }

        Spacer(Modifier.height(12.dp))

        // 科目分布
        GlassCard(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(18.dp)) {
                Text("科目分布", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                if (bySubject.isEmpty()) {
                    Text("暂无数据", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    bySubject.entries.sortedByDescending { it.value }.forEach { (s, n) ->
                        val c = subjColor(s)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 10.dp)
                        ) {
                            Text(s, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.width(44.dp))
                            Box(
                                Modifier.weight(1f).height(12.dp)
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(c.copy(alpha = 0.13f))
                            ) {
                                Box(
                                    Modifier.fillMaxHeight()
                                        .fillMaxWidth((n.toFloat() / maxSub).coerceIn(0.04f, 1f))
                                        .clip(RoundedCornerShape(percent = 50))
                                        .background(
                                            Brush.horizontalGradient(listOf(c.copy(alpha = 0.75f), c))
                                        )
                                )
                            }
                            Text("$n", fontSize = 12.sp, textAlign = TextAlign.End,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(30.dp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 薄弱知识点
        GlassCard(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(18.dp)) {
                Text("薄弱知识点", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (topKp.isEmpty()) {
                    Text("填写知识点后自动生成", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    topKp.forEachIndexed { i, (k, n) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 7.dp)
                        ) {
                            Box(
                                Modifier.size(24.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (i < 3) Color(0xFFF59E0B).copy(alpha = 0.16f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${i + 1}", fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (i < 3) Color(0xFFB45309)
                                            else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                lastKnowledge(k), fontSize = 13.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "错 $n 次", fontSize = 11.5.sp,
                                color = Color(0xFFF43F5E), fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun ColorStatCard(
    num: Int, label: String, brush: Brush,
    modifier: Modifier = Modifier, emoji: String = ""
) {
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(brush)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (emoji.isNotBlank()) "$emoji $num" else "$num",
                fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White
            )
            Spacer(Modifier.height(2.dp))
            Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.88f))
        }
    }
}

@Composable
private fun MiniStatTile(label: String, value: String, color: Color) {
    GlassCard(shape = RoundedCornerShape(20.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape)
                    .background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text(value.take(3), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = color)
                Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
