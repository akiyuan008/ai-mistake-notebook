package com.jiancuoti.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.jiancuoti.app.data.SUBJECTS
import com.jiancuoti.app.data.Mistake
import com.jiancuoti.app.data.Store
import com.jiancuoti.app.net.Supabase
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val SUBJ_COLORS = mapOf(
    "数学" to Color(0xFF059669), "语文" to Color(0xFFE11D48), "英语" to Color(0xFF7C3AED),
    "物理" to Color(0xFFEA580C), "化学" to Color(0xFF0D9488), "生物" to Color(0xFFDB2777),
    "历史" to Color(0xFFB45309), "地理" to Color(0xFF2563EB), "政治" to Color(0xFF4F46E5),
    "其他" to Color(0xFF64748B)
)

fun subjColor(s: String) = SUBJ_COLORS[s] ?: Color(0xFF64748B)
fun fmtDate(t: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(t))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onChanged: () -> Unit) {
    var kw by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("全部") }
    var status by remember { mutableStateOf("全部") }
    var detail by remember { mutableStateOf<Mistake?>(null) }
    var editing by remember { mutableStateOf<Mistake?>(null) }
    var version by remember { mutableStateOf(0) }

    val list = remember(kw, subject, status, version) {
        Store.mistakes.filter { m ->
            (subject == "全部" || m.subject == subject) &&
            when (status) {
                "未掌握" -> !m.mastered
                "已掌握" -> m.mastered
                "高频错题" -> m.errorCount >= 2
                else -> true
            } &&
            (kw.isBlank() || (m.question + m.answer + m.analysis + m.knowledge)
                .contains(kw, ignoreCase = true))
        }
    }

    Column(Modifier.fillMaxSize()) {
        // 搜索栏（不悬浮，随内容布局）
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                TextField(
                    value = kw, onValueChange = { kw = it },
                    placeholder = { Text("搜索题干、答案、知识点…", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true
                )
            }
        }

        // 状态筛选
        Row(Modifier.padding(horizontal = 14.dp).padding(bottom = 6.dp)) {
            listOf("全部", "未掌握", "已掌握", "高频错题").forEach { s ->
                FilterChip(
                    selected = status == s,
                    onClick = { status = s },
                    label = { Text(s, fontSize = 12.sp) },
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
        }

        // 科目筛选
        Row(Modifier.padding(horizontal = 14.dp).padding(bottom = 8.dp)) {
            val subjects = listOf("全部") + Store.mistakes.map { it.subject }.distinct()
            subjects.forEach { s ->
                FilterChip(
                    selected = subject == s,
                    onClick = { subject = s },
                    label = { Text(if (s == "全部") "全部科目" else s, fontSize = 12.sp) },
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
        }

        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("暂无错题", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text("拍摄或导入试卷后即可管理错题", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(list, key = { it.id }) { m ->
                    MistakeCard(m) { detail = m }
                }
                item { Spacer(Modifier.height(90.dp)) }
            }
        }
    }

    detail?.let { m ->
        DetailDialog(
            m,
            onClose = { detail = null },
            onEdit = { editing = m; detail = null },
            onChanged = { version++; onChanged() }
        )
    }
    editing?.let { m ->
        EditDialog(
            m,
            onClose = { editing = null },
            onSaved = { editing = null; version++; onChanged() }
        )
    }
}

@Composable
fun MistakeCard(m: Mistake, onClick: () -> Unit) {
    val imgFile = Store.imgFile(m.imageFile)
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(Modifier.padding(12.dp)) {
            if (imgFile != null) {
                AsyncImage(
                    model = imgFile,
                    contentDescription = null,
                    modifier = Modifier.size(82.dp).clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    Modifier.size(82.dp).clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text("图", color = MaterialTheme.colorScheme.outline)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SubjectTag(m.subject)
                    if (m.knowledge.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(m.knowledge, fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false))
                    }
                    if (m.mastered) {
                        Spacer(Modifier.width(6.dp))
                        Text("已掌握", fontSize = 10.sp, color = Green)
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    m.question.ifBlank { "图片题（点击查看详情）" },
                    fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = if (m.question.isBlank()) MaterialTheme.colorScheme.outline
                            else MaterialTheme.colorScheme.onSurface,
                    lineHeight = 19.sp
                )
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fmtDate(m.createdAt), fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline)
                    if (m.errorCount > 1) {
                        Spacer(Modifier.width(8.dp))
                        Text("错 ${m.errorCount} 次", fontSize = 11.sp, color = Red)
                    }
                }
            }
        }
    }
}

@Composable
fun SubjectTag(s: String) {
    val c = subjColor(s)
    Text(
        s, fontSize = 10.sp, color = c,
        modifier = Modifier.background(c.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@Composable
fun DetailDialog(
    m: Mistake, onClose: () -> Unit, onEdit: () -> Unit, onChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val imgFile = Store.imgFile(m.imageFile)
    Dialog(onDismissRequest = onClose) {
        Surface(
            Modifier.fillMaxWidth().fillMaxHeight(0.86f),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SubjectTag(m.subject)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        m.knowledge.ifBlank { "错题详情" },
                        fontSize = 15.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onClose) { Text("关闭") }
                }
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    if (imgFile != null) {
                        AsyncImage(
                            model = imgFile,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    if (m.question.isNotBlank()) Section("题干") { Text(m.question, fontSize = 14.sp, lineHeight = 22.sp) }
                    if (m.answer.isNotBlank()) Section("正确答案", Green) { Text(m.answer, fontSize = 14.sp, lineHeight = 22.sp) }
                    if (m.analysis.isNotBlank()) Section("解析") { Text(m.analysis, fontSize = 14.sp, lineHeight = 22.sp) }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "录入 ${fmtDate(m.createdAt)} · 错误 ${m.errorCount} 次 · ${if (m.mastered) "已掌握" else "未掌握"}",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.outline
                    )
                }
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            Store.mistakes.remove(m)
                            Store.imgFile(m.imageFile)?.delete()
                            Store.saveMistakes()
                            scope.launch { Supabase.deleteMistake(m.id) }
                            onChanged(); onClose()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("删除", color = Red) }
                    OutlinedButton(
                        onClick = {
                            m.errorCount++; m.updatedAt = System.currentTimeMillis()
                            Store.saveMistakes()
                            scope.launch { Supabase.pushMistake(m) }
                            onChanged()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("又错了") }
                    Button(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f)
                    ) { Text("编辑", color = Color.White) }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, color: Color = MaterialTheme.colorScheme.primary, content: @Composable () -> Unit) {
    Text(title, fontSize = 12.sp, color = color,
        modifier = Modifier.padding(bottom = 4.dp))
    content()
    Spacer(Modifier.height(12.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDialog(m: Mistake, onClose: () -> Unit, onSaved: () -> Unit) {
    var subject by remember { mutableStateOf(m.subject) }
    var knowledge by remember { mutableStateOf(m.knowledge) }
    var question by remember { mutableStateOf(m.question) }
    var answer by remember { mutableStateOf(m.answer) }
    var analysis by remember { mutableStateOf(m.analysis) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onClose) {
        Surface(
            Modifier.fillMaxWidth().fillMaxHeight(0.88f),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("编辑错题", fontSize = 16.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text("取消") }
                    Button(onClick = {
                        m.subject = subject; m.knowledge = knowledge.trim()
                        m.question = question.trim(); m.answer = answer.trim()
                        m.analysis = analysis.trim(); m.updatedAt = System.currentTimeMillis()
                        Store.saveMistakes()
                        scope.launch { Supabase.pushMistake(m) }
                        onSaved()
                    }) { Text("保存", color = Color.White) }
                }
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expanded, onExpandedChange = { expanded = it },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = subject, onValueChange = {},
                                readOnly = true, label = { Text("科目") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                SUBJECTS.forEach { s ->
                                    DropdownMenuItem(text = { Text(s) }, onClick = {
                                        subject = s; expanded = false
                                    })
                                }
                            }
                        }
                        OutlinedTextField(
                            value = knowledge, onValueChange = { knowledge = it },
                            label = { Text("知识点") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = question, onValueChange = { question = it },
                        label = { Text("题干") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3, maxLines = 6
                    )
                    OutlinedTextField(
                        value = answer, onValueChange = { answer = it },
                        label = { Text("正确答案") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2, maxLines = 4
                    )
                    OutlinedTextField(
                        value = analysis, onValueChange = { analysis = it },
                        label = { Text("解析") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3, maxLines = 8
                    )
                }
            }
        }
    }
}
