package com.jiancuoti.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiancuoti.app.data.Store
import java.util.Calendar

@Composable
fun StatsScreen() {
    var range by remember { mutableStateOf("最近 7 天") }
    val ms = Store.mistakes

    val start = when (range) {
        "今天" -> Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        "本周" -> Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        "本月" -> Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        "最近 7 天" -> System.currentTimeMillis() - 7L * 86400000
        "最近 30 天" -> System.currentTimeMillis() - 30L * 86400000
        "本学期" -> {
            val c = Calendar.getInstance()
            if (c.get(Calendar.MONTH) >= 8 || c.get(Calendar.MONTH) < 2) c.set(c.get(Calendar.YEAR), 8, 1) else c.set(c.get(Calendar.YEAR), 1, 1)
            c.timeInMillis
        }
        else -> 0L
    }
    val inRange = ms.count { it.createdAt >= start }
    val mastered = ms.count { it.mastered }
    val totalErr = ms.sumOf { it.errorCount }
    val bySubject = ms.groupingBy { it.subject }.eachCount()
    val maxSub = (bySubject.values.maxOrNull() ?: 1).coerceAtLeast(1)
    val topKp = ms.filter { it.knowledge.isNotBlank() }
        .groupingBy { it.knowledge }.aggregate { _, acc: Int?, m, _ -> (acc ?: 0) + m.errorCount }
        .entries.sortedByDescending { it.value }.take(6)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
    ) {
        Row(Modifier.padding(vertical = 8.dp)) {
            listOf("今天", "本周", "本月", "最近 7 天", "最近 30 天", "全部时间").forEach { r ->
                FilterChip(
                    selected = range == r, onClick = { range = r },
                    label = { Text(r, fontSize = 11.sp) },
                    modifier = Modifier.padding(end = 5.dp)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("错题总数", "${ms.size}", SkyPrimaryDeep, Modifier.weight(1f))
            StatCard("时段内新增", "$inRange", SkyPrimaryDeep, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("已掌握", "$mastered", Green, Modifier.weight(1f))
            StatCard("累计错误", "$totalErr", Red, Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))

        GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(18.dp)) {
                Text("科目分布", fontSize = 15.sp)
                Spacer(Modifier.height(14.dp))
                if (bySubject.isEmpty()) {
                    Text("暂无数据", color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
                } else {
                    bySubject.entries.sortedByDescending { it.value }.forEach { (s, n) ->
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 10.dp)) {
                            Text(s, fontSize = 13.sp, modifier = Modifier.width(48.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Box(Modifier.weight(1f).height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)) {
                                Box(Modifier.fillMaxHeight()
                                    .fillMaxWidth((n.toFloat() / maxSub).coerceIn(0.02f, 1f))
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(SkyPrimary))
                            }
                            Text("$n", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.width(32.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(18.dp)) {
                Text("薄弱知识点", fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                if (topKp.isEmpty()) {
                    Text("填写知识点后自动生成", color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
                } else {
                    topKp.forEachIndexed { i, (k, n) ->
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 7.dp)) {
                            Box(
                                Modifier.size(22.dp).clip(RoundedCornerShape(7.dp))
                                    .background(if (i < 3) Amber.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${i + 1}", fontSize = 11.sp,
                                    color = if (i < 3) Color(0xFFB45309) else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(k, fontSize = 13.5.sp, modifier = Modifier.weight(1f),
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text("错 $n 次", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier) {
    GlassCard(modifier, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(value, fontSize = 28.sp, color = color)
            Spacer(Modifier.height(2.dp))
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
        }
    }
}
