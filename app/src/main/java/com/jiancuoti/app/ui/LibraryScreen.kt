package com.jiancuoti.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
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
import kotlinx.coroutines.withContext
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
    var timeRange by remember { mutableStateOf("全部") }
    var detail by remember { mutableStateOf<Mistake?>(null) }
    var editing by remember { mutableStateOf<Mistake?>(null) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    var version by remember { mutableIntStateOf(0) }

    // 实时刷新：错题数量或任一解析状态变化时重算列表
    val parsingCount = Store.mistakes.count { it.parsing }
    LaunchedEffect(Store.mistakes.size, parsingCount) {
        version++
    }

    val list = remember(kw, subject, status, timeRange, version) {
        val timeStart = when (timeRange) {
            "近1天" -> System.currentTimeMillis() - 1L * 86400000
            "近7天" -> System.currentTimeMillis() - 7L * 86400000
            "近1月" -> System.currentTimeMillis() - 30L * 86400000
            "近3月" -> System.currentTimeMillis() - 90L * 86400000
            else -> 0L
        }
        Store.mistakes.filter { m ->
            (subject == "全部" || m.subject == subject) &&
            m.createdAt >= timeStart &&
            when (status) {
                "未掌握" -> !m.mastered
                "已掌握" -> m.mastered
                "高频错题" -> m.errorCount >= 2
                "解析中" -> m.parsing
                else -> true
            } &&
            (kw.isBlank() || (m.question + m.answer + m.analysis + m.knowledge)
                .contains(kw, ignoreCase = true))
        }
    }

    Column(Modifier.fillMaxSize()) {
        // 搜索栏（玻璃拟态，高度自适应不截字）
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            shape = RoundedCornerShape(24.dp),
            color = glassColor(),
            border = glassBorder()
        ) {
            Row(
                Modifier.padding(start = 16.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                TextField(
                    value = kw, onValueChange = { kw = it },
                    placeholder = {
                        Text("搜索题干、答案、知识点…", fontSize = 13.5.sp)
                    },
                    modifier = Modifier.fillMaxWidth(),
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
        Row(Modifier.padding(horizontal = 14.dp).padding(bottom = 6.dp)
            .horizontalScroll(rememberScrollState())) {
            listOf("全部", "未掌握", "已掌握", "高频错题", "解析中").forEach { s ->
                FilterChip(
                    selected = status == s,
                    onClick = { status = s },
                    label = { Text(s, fontSize = 12.sp) },
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
        }

        // 时间筛选
        Row(Modifier.padding(horizontal = 14.dp).padding(bottom = 8.dp)
            .horizontalScroll(rememberScrollState())) {
            listOf("全部", "近1天", "近7天", "近1月", "近3月").forEach { t ->
                FilterChip(
                    selected = timeRange == t,
                    onClick = { timeRange = t },
                    label = { Text(t, fontSize = 12.sp) },
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
        }

        // 科目筛选
        Row(Modifier.padding(horizontal = 14.dp).padding(bottom = 8.dp)
            .horizontalScroll(rememberScrollState())) {
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            // 快速回看入口
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "共 ${list.size} 题",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { viewerIndex = 0 }) {
                    Text("快速回看 · 左右滑切题", fontSize = 12.5.sp)
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(list, key = { it.id }) { m ->
                    MistakeCard(m, onOpen = { detail = m }, onOpenImage = {
                        val idx = list.indexOfFirst { it.id == m.id }
                        viewerIndex = if (idx >= 0) idx else 0
                    })
                }
                item { Spacer(Modifier.height(90.dp)) }
            }
        }

        // 全屏快速回看
        viewerIndex?.let { idx ->
            val imgs = list.mapNotNull { Store.imgFile(it.imageFile) }
            if (imgs.isEmpty()) { viewerIndex = null } else {
                ImageViewer(
                    images = imgs,
                    initialIndex = idx,
                    titles = list.map { it.knowledge },
                    onClose = { viewerIndex = null }
                )
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
fun MistakeCard(m: Mistake, onOpen: () -> Unit, onOpenImage: () -> Unit = onOpen) {
    val imgFile = Store.imgFile(m.imageFile)
    GlassCard(
        Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(Modifier.padding(14.dp)) {
            if (imgFile != null) {
                AsyncImage(
                    model = imgFile,
                    contentDescription = null,
                    modifier = Modifier.size(82.dp).clip(RoundedCornerShape(16.dp))
                        .clickableNoRipple(onClick = onOpenImage),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    Modifier.size(82.dp).clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text("图", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    if (m.parsing) "AI 解析生成中…" else m.question.ifBlank { "图片题（点击查看详情）" },
                    fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = if (m.parsing) MaterialTheme.colorScheme.primary
                            else if (m.question.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                    lineHeight = 19.sp
                )
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fmtDate(m.createdAt), fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    var showVariants by remember { mutableStateOf(true) }
    var aiBusy by remember { mutableStateOf(false) }
    var aiMsg by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onClose) {
        Surface(
            Modifier.fillMaxWidth().fillMaxHeight(0.86f),
            shape = RoundedCornerShape(28.dp),
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
                    if (m.parsing) {
                        // 后台解析中提示
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                    RoundedCornerShape(14.dp))
                                .padding(12.dp)) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("AI 解析生成中…完成本题后自动更新，可关闭本页",
                                fontSize = 12.5.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    if (m.question.isNotBlank()) Section("题干") { Text(m.question, fontSize = 14.sp, lineHeight = 22.sp) }
                    // 知识点标签
                    val kps = m.knowledge.split('、', '，', ',', '；', ';', ' ').map { it.trim() }
                        .filter { it.isNotBlank() }
                    if (kps.isNotEmpty()) Section("知识点") {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            kps.forEach { kp ->
                                Text(
                                    kp, fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                            RoundedCornerShape(50)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    if (m.answer.isNotBlank()) Section("正确答案", Green) { Text(m.answer, fontSize = 14.sp, lineHeight = 22.sp) }
                    // 解题流程：按行拆分编号展示
                    if (m.analysis.isNotBlank()) Section("解题流程") {
                        val lines = m.analysis.split('\n', '；')
                            .map { it.trim().replace(Regex("^[①②③④⑤⑥⑦⑧⑨]|^\\d{1,2}[.、）)]\\s*"), "").trim() }
                            .filter { it.length > 1 }
                        if (lines.size > 1) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                lines.forEachIndexed { i, line ->
                                    Row {
                                        Text("${i + 1}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                RoundedCornerShape(50)
                                            ).padding(horizontal = 7.dp, vertical = 1.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(line, fontSize = 13.5.sp, lineHeight = 21.sp,
                                            modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        } else {
                            Text(m.analysis, fontSize = 14.sp, lineHeight = 22.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "录入 ${fmtDate(m.createdAt)} · 错误 ${m.errorCount} 次 · ${if (m.mastered) "已掌握" else "未掌握"}",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                // AI 操作区：重新解析 + 举一反三（均后台运行，关闭弹窗不中断）
                val vBusy = com.jiancuoti.app.net.BgTasks.variantsBusy[m.id] == true
                val vErr = com.jiancuoti.app.net.BgTasks.variantsError[m.id]
                val vDone = com.jiancuoti.app.net.BgTasks.variants[m.id]
                var preferReal by remember { mutableStateOf(true) }
                val aiMsg2 = when {
                    vBusy -> "变式题后台生成中…可关闭本页"
                    vErr != null -> vErr
                    aiMsg.isNotBlank() -> aiMsg
                    else -> ""
                }
                if (aiMsg2.isNotBlank()) {
                    Text(aiMsg2, fontSize = 12.sp,
                        color = if (vErr != null) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp))
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("优先匹配近年真题", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f))
                    Switch(checked = preferReal, onCheckedChange = { preferReal = it })
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (aiBusy) return@OutlinedButton
                            val f = Store.imgFile(m.imageFile)
                            if (f == null) { aiMsg = "该题没有图片，无法重新解析"; return@OutlinedButton }
                            if (!com.jiancuoti.app.net.AiParser.configured) {
                                aiMsg = "请先在「我的」页配置 AI 解析接口"; return@OutlinedButton
                            }
                            aiBusy = true; aiMsg = "AI 解析中…（关闭本页不中断）"
                            com.jiancuoti.app.net.BgTasks.scope.launch {
                                try {
                                    val bmp = withContext(kotlinx.coroutines.Dispatchers.IO) { loadBitmap(f, 1600) }
                                    val r = com.jiancuoti.app.net.AiParser.parse(bmp)
                                    m.subject = r.subject; m.knowledge = r.knowledge
                                    m.question = r.question; m.answer = r.answer
                                    m.analysis = r.analysis; m.parsedBy = "api"
                                    m.parsing = false
                                    m.updatedAt = System.currentTimeMillis()
                                    Store.saveMistakes()
                                    withContext(kotlinx.coroutines.Dispatchers.IO) { Supabase.pushMistake(m) }
                                    aiBusy = false
                                    onChanged()
                                } catch (e: Exception) {
                                    aiBusy = false
                                    aiMsg = "解析失败：${e.message?.take(90)}"
                                }
                            }
                        },
                        enabled = !aiBusy,
                        modifier = Modifier.weight(1f)
                    ) { Text("AI 重新解析", fontSize = 12.5.sp) }
                    Button(
                        onClick = {
                            if (!com.jiancuoti.app.net.AiParser.configured) {
                                aiMsg = "请先在「我的」页配置 AI 解析接口"; return@Button
                            }
                            com.jiancuoti.app.net.BgTasks.startVariants(m, preferReal)
                        },
                        enabled = !vBusy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            when {
                                vBusy -> "后台生成中…"
                                vDone != null -> "再看一次"
                                else -> "举一反三"
                            },
                            color = Color.White, fontSize = 12.5.sp
                        )
                    }
                }
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

    // 举一反三：变式题弹窗（结果来自全局后台任务，关弹窗再开也能看到）
    val doneVariants = com.jiancuoti.app.net.BgTasks.variants[m.id]
    if (doneVariants != null && showVariants) {
        VariantsDialog(
            doneVariants, m,
            onClose = { showVariants = false },
            onSavedVariant = { version++; onChanged() }
        )
    }
}

@Composable
private fun VariantsDialog(
    vs: List<com.jiancuoti.app.net.VariantQuestion>,
    origin: Mistake,
    onClose: () -> Unit,
    onSavedVariant: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var savedIds by remember { mutableStateOf(setOf<String>()) }
    Dialog(onDismissRequest = onClose) {
        Surface(
            Modifier.fillMaxWidth().fillMaxHeight(0.86f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("举一反三 · 变式练习", fontSize = 16.sp)
                        Text(
                            "点「存入错题本」加入组卷可选池",
                            fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onClose) { Text("关闭") }
                }
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    var showAns by remember { mutableStateOf(setOf<Int>()) }
                    vs.forEachIndexed { i, v ->
                        val key = "${origin.id}_v$i"
                        val saved = savedIds.contains(key)
                        GlassCard(shape = RoundedCornerShape(20.dp)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                // 头部：变式序号 + 来源 + 难度
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("变式 ${i + 1}", fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary)
                                    if (v.source.isNotBlank()) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(v.source, fontSize = 10.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.surfaceVariant,
                                                    RoundedCornerShape(50))
                                                .padding(horizontal = 8.dp, vertical = 2.dp),
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Text("★".repeat(v.difficulty) + "☆".repeat(3 - v.difficulty),
                                        fontSize = 11.sp, color = Amber)
                                }
                                Spacer(Modifier.height(10.dp))
                                // 题干
                                Text(v.question, fontSize = 14.5.sp, lineHeight = 24.sp)
                                // 答案与完整解析（点击展开）
                                if (showAns.contains(i)) {
                                    Spacer(Modifier.height(10.dp))
                                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                                    Spacer(Modifier.height(10.dp))
                                    Text("【参考答案】", fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height(3.dp))
                                    Text(v.answer, fontSize = 14.sp, color = Green,
                                        lineHeight = 22.sp)
                                    if (v.analysis.isNotBlank()) {
                                        Spacer(Modifier.height(10.dp))
                                        Text("【完整解析】", fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.height(3.dp))
                                        val steps = v.analysis.split('\n')
                                            .map { it.trim() }
                                            .filter { it.isNotBlank() }
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            steps.forEachIndexed { si, step ->
                                                Row {
                                                    Text("${si + 1}", fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier
                                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                                RoundedCornerShape(50))
                                                            .padding(horizontal = 6.dp, vertical = 1.dp))
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(step, fontSize = 13.sp, lineHeight = 20.sp,
                                                        modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(
                                        onClick = {
                                            showAns = if (showAns.contains(i)) showAns - i else showAns + i
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) { Text(if (showAns.contains(i)) "收起答案" else "查看答案与解析", fontSize = 12.5.sp) }
                                    Button(
                                        onClick = {
                                            if (saved) return@Button
                                            val m = Mistake(
                                                id = Store.uid(),
                                                subject = origin.subject,
                                                knowledge = origin.knowledge.ifBlank { v.source },
                                                question = v.question,
                                                answer = v.answer,
                                                analysis = v.analysis,
                                                parsedBy = "variant",
                                                variantOf = origin.id
                                            )
                                            Store.mistakes.add(0, m)
                                            Store.saveMistakes()
                                            savedIds = savedIds + key
                                            onSavedVariant()
                                            scope.launch { Supabase.pushMistake(m) }
                                        },
                                        enabled = !saved,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(if (saved) "已存入" else "存入错题本",
                                            color = Color.White, fontSize = 12.5.sp)
                                    }
                                }
                            }
                        }
                    }
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
fun EditDialog(
    m: Mistake,
    title: String = "编辑错题",
    onClose: () -> Unit,
    onSaved: () -> Unit
) {
    var subject by remember { mutableStateOf(m.subject) }
    var knowledge by remember { mutableStateOf(m.knowledge) }
    var question by remember { mutableStateOf(m.question) }
    var answer by remember { mutableStateOf(m.answer) }
    var analysis by remember { mutableStateOf(m.analysis) }
    val scope = rememberCoroutineScope()
    val imgFile = Store.imgFile(m.imageFile)

    Dialog(onDismissRequest = onClose) {
        Surface(
            Modifier.fillMaxWidth().fillMaxHeight(0.88f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, fontSize = 16.sp, modifier = Modifier.weight(1f))
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
                    // 原图预览
                    if (imgFile != null) {
                        AsyncImage(
                            model = imgFile,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth()
                                .heightIn(max = 180.dp)
                                .clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
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
