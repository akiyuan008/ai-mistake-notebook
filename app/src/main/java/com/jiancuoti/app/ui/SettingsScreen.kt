package com.jiancuoti.app.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.jiancuoti.app.data.Store
import com.jiancuoti.app.net.AiParser
import com.jiancuoti.app.net.Supabase
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SettingsScreen(themeMode: ThemeMode, onThemeMode: (ThemeMode) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var supaUrl by remember { mutableStateOf(Store.settings["supaUrl"] ?: "") }
    var supaKey by remember { mutableStateOf(Store.settings["supaKey"] ?: "") }
    var apiUrl by remember { mutableStateOf(Store.settings["apiUrl"] ?: "") }
    var apiKey by remember { mutableStateOf(Store.settings["apiKey"] ?: "") }
    var apiModel by remember { mutableStateOf(Store.settings["apiModel"] ?: "") }
    var modelStatus by remember { mutableStateOf("") }
    var syncStatus by remember { mutableStateOf(if (Supabase.configured) "已配置" else "未配置") }
    var enhance by remember { mutableStateOf(Store.settings["enhance"] != "0") }
    var autoParse by remember { mutableStateOf(Store.settings["autoParse"] != "0") }

    // 模型选择列表
    var modelPicker by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var modelsLoading by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(10.dp))

        // 外观
        SettingsCard("外观") {
            Text("深色模式", fontSize = 13.5.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                mapOf("自动" to ThemeMode.AUTO, "浅色" to ThemeMode.LIGHT, "深色" to ThemeMode.DARK)
                    .forEach { (label, mode) ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = { onThemeMode(mode) },
                            label = { Text(label) }
                        )
                    }
            }
            Spacer(Modifier.height(6.dp))
            Text("「自动」跟随系统设置", fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(14.dp))

        // Supabase
        SettingsCard("Supabase 云同步") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("当前：$syncStatus", fontSize = 13.sp,
                    color = if (syncStatus == "已配置" || syncStatus.startsWith("已连接")) Green else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f))
            }
            if (supaUrl.isBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("填写 Supabase 项目的 URL 和 anon Key（控制台 → 项目设置 → API）",
                    fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 17.sp)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = supaUrl, onValueChange = { supaUrl = it },
                label = { Text("项目 URL") }, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://xxxx.supabase.co", fontSize = 13.sp) },
                singleLine = true)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = supaKey, onValueChange = { supaKey = it },
                label = { Text("anon Key") }, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("eyJhbGciOi…", fontSize = 13.sp) },
                singleLine = true)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    Store.settings["supaUrl"] = supaUrl.trim()
                    Store.settings["supaKey"] = supaKey.trim()
                    Store.saveSettings()
                    scope.launch {
                        syncStatus = if (Supabase.testConnection()) {
                            val n = Supabase.pull()
                            "已连接" + if (n > 0) "（合并 $n 条）" else ""
                        } else "连接失败，请检查 URL 与数据表"
                    }
                }) { Text("保存并连接", color = androidx.compose.ui.graphics.Color.White) }
                if (Supabase.configured) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            val n = Supabase.pull()
                            syncStatus = "已拉取 $n 条"
                        }
                    }) { Text("拉取云端") }
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        // AI 解析
        SettingsCard("AI 解析配置") {
            Text("填写 OpenAI 兼容多模态接口即可自动识题、举一反三", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = apiUrl, onValueChange = { apiUrl = it },
                label = { Text("接口地址") }, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://api.openai.com/v1/chat/completions", fontSize = 12.sp) },
                singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = apiKey, onValueChange = { apiKey = it },
                label = { Text("API Key") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = apiModel, onValueChange = { apiModel = it },
                    label = { Text("模型") }, modifier = Modifier.weight(1f), singleLine = true)
                Button(onClick = {
                    Store.settings["apiUrl"] = apiUrl.trim()
                    Store.settings["apiKey"] = apiKey.trim()
                    Store.saveSettings()
                    models = emptyList()
                    modelsLoading = true
                    modelPicker = true
                    scope.launch {
                        val list = AiParser.fetchModels()
                        models = list
                        modelsLoading = false
                        modelStatus = if (list.isEmpty()) "未获取到模型列表，可手动填写"
                        else "共 ${list.size} 个模型可选"
                    }
                }) { Text("选择模型", color = androidx.compose.ui.graphics.Color.White, fontSize = 13.sp) }
            }
            Text("当前模型：${apiModel.ifBlank { "未选择（默认 gpt-4o-mini）" }}",
                fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (modelStatus.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(modelStatus, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    Store.settings["apiUrl"] = apiUrl.trim()
                    Store.settings["apiKey"] = apiKey.trim()
                    Store.settings["apiModel"] = apiModel.trim()
                    Store.saveSettings()
                    modelStatus = "已保存，测试中…"
                    scope.launch {
                        modelStatus = try {
                            // 纯文本测试请求，验证地址/Key/模型
                            AiParser.testConnection()
                            "接口可用 ✓"
                        } catch (e: Exception) {
                            "测试失败：${e.message?.take(90)}"
                        }
                    }
                }, modifier = Modifier.weight(1f)) {
                    Text("保存并测试", color = androidx.compose.ui.graphics.Color.White, fontSize = 13.sp)
                }
                Button(onClick = {
                    Store.settings["apiUrl"] = apiUrl.trim()
                    Store.settings["apiKey"] = apiKey.trim()
                    Store.settings["apiModel"] = apiModel.trim()
                    Store.saveSettings()
                    modelStatus = "已保存"
                }, modifier = Modifier.weight(1f)) {
                    Text("仅保存", color = androidx.compose.ui.graphics.Color.White, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        // 提取选项
        SettingsCard("提取选项") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("自动增强画质", fontSize = 13.5.sp)
                    Text("去阴影、提亮纸张、加深字迹", fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enhance, onCheckedChange = {
                    enhance = it
                    Store.settings["enhance"] = if (it) "1" else "0"
                    Store.saveSettings()
                })
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("裁剪后 AI 自动解析", fontSize = 13.5.sp)
                    Text("裁剪完成后 AI 自动识别科目/知识点并预填，弹窗中可再编辑；关闭则只存图片", fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = autoParse, onCheckedChange = {
                    autoParse = it
                    Store.settings["autoParse"] = if (it) "1" else "0"
                    Store.saveSettings()
                })
            }
        }
        Spacer(Modifier.height(14.dp))

        // 数据
        SettingsCard("数据管理") {
            Text("错题 ${Store.mistakes.size} 条 · 试卷 ${Store.papers.size} 份",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    try {
                        val dir = File(context.cacheDir, "export").apply { mkdirs() }
                        val f = File(dir, "简错题备份-${fmtDate(System.currentTimeMillis())}.json")
                        f.writeText(Store.exportAll())
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "导出备份"))
                    } catch (_: Exception) {}
                }) { Text("导出数据") }
                OutlinedButton(onClick = {
                    Store.mistakes.clear(); Store.papers.clear()
                    Store.imgDir.listFiles()?.forEach { it.delete() }
                    Store.saveMistakes(); Store.savePapers()
                }, colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error)) { Text("清空数据") }
            }
        }
        Spacer(Modifier.height(14.dp))

        SettingsCard("关于") {
            Text("简错题 v5.2 · 原生版", fontSize = 13.sp)
        }
        Spacer(Modifier.height(100.dp))
    }

    if (modelPicker) {
        ModelPickerDialog(
            models = models,
            loading = modelsLoading,
            current = apiModel,
            onPick = { apiModel = it; modelPicker = false },
            onClose = { modelPicker = false }
        )
    }
}

/** 模型选择弹窗：可搜索 + 单选列表 */
@Composable
private fun ModelPickerDialog(
    models: List<String>,
    loading: Boolean,
    current: String,
    onPick: (String) -> Unit,
    onClose: () -> Unit
) {
    var kw by remember { mutableStateOf("") }
    val filtered = remember(kw, models) {
        if (kw.isBlank()) models else models.filter { it.contains(kw, ignoreCase = true) }
    }
    Dialog(onDismissRequest = onClose) {
        Surface(
            Modifier.fillMaxWidth().fillMaxHeight(0.82f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("选择模型", fontSize = 16.sp)
                        Text(
                            if (loading) "正在获取模型列表…"
                            else if (models.isEmpty()) "未获取到列表，可关闭后手动填写"
                            else "${filtered.size} / ${models.size} 个模型",
                            fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, null) }
                }
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                // 搜索框
                Surface(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        BasicTextField(
                            value = kw, onValueChange = { kw = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 13.5.sp,
                                color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.padding(vertical = 10.dp).weight(1f),
                            decorationBox = { inner ->
                                if (kw.isBlank()) Text("搜索模型名称…", fontSize = 13.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                inner()
                            }
                        )
                        if (kw.isNotBlank()) {
                            Icon(Icons.Default.Close, null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp).clickable { kw = "" })
                        }
                    }
                }
                when {
                    loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    filtered.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(if (models.isEmpty()) "列表为空" else "无匹配模型",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> LazyColumn(Modifier.weight(1f)) {
                        items(filtered, key = { it }) { m ->
                            val selected = m == current
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { onPick(m) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(m, fontSize = 13.sp, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f))
                                if (selected) {
                                    Icon(Icons.Default.Check, null,
                                        tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 统一的玻璃拟态设置卡片：全圆角、无棱角、规格一致 */
@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}
