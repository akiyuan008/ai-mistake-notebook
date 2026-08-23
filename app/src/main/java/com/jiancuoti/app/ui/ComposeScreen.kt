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
    var confirmDel by remember { mutableStateOf<Paper?>(null) }
    // 数据版本：组卷/删除/入库变更后自动刷新
    val rev = Store.revision.intValue

    val pool = remember(subject, kp, errMin, range, mastered, rev) {
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
        val myPapers = remember(rev) { Store.papers.toList() }
        if (myPapers.isNotEmpty()) {
            Text("已创建的试卷", fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                myPapers.forEach { p ->
                    PaperRow(p, onOpen = {
                        picked = p.questions.filter { id -> Store.mistakes.any { it.id == id } }.toSet()
                        if (picked.isEmpty()) { toast = "卷中题目已不在错题库" } else titleDialog = true
                    }, onDelete = {
                        confirmDel = p
                    })
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(90.dp))
            }
        }
    }

    // 删除确认
    confirmDel?.let { p ->
        AlertDialog(
            onDismissRequest = { confirmDel = null },
            title = { Text("删除试卷") },
            text = { Text("确定删除「${p.name}」吗？（不会删除题目本身）") },
            confirmButton = {
                TextButton(onClick = {
                    Store.papers.remove(p)
                    Store.savePapers()
                    confirmDel = null
                    onChanged()
                }) { Text("删除", color = Red) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDel = null }) { Text("取消") }
            }
        )
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
                val paper = Paper(
                    id = Store.uid(), name = title,
                    subjects = qs.map { it.subject }.distinct().joinToString("、"),
                    count = qs.size, questions = qs.map { it.id }
                )
                Store.papers.add(0, paper); Store.savePapers()
                onChanged()
                scope.launch {
                    val msg = generateAndShare(context, title, qs, opts)
                    if (msg.isNotBlank()) toast = msg
                }
            }
        )
    }
}

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
        Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${p.count} 题 · ${fmtDate(p.createdAt)}", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onOpen) { Text("查看") }
            TextButton(onClick = onDelete) { Text("删除", color = Red) }
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
                                    if (m.variantOf.isNotBlank()) {
                                        Spacer(Modifier.width(6.dp))
                                        Text("变式", fontSize = 10.sp, color = Amber,
                                            modifier = Modifier.background(Amber.copy(alpha = 0.12f),
                                                RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 1.dp))
                                    }
                                    if (m.errorCount > 1) {
                                        Spacer(Modifier.width(6.dp))
                                        Text("错${m.errorCount}次", fontSize = 11.sp, color = Red)
                                    }
                                }
                                Spacer(Modifier.height(3.dp))
                                MathText(
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
