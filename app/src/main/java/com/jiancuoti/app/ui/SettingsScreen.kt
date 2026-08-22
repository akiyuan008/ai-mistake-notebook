package com.jiancuoti.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
    ) {
        // 外观
        SettingsCard("外观") {
            Text("深色模式", fontSize = 13.5.sp)
            Spacer(Modifier.height(8.dp))
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
                color = MaterialTheme.colorScheme.outline)
        }
        Spacer(Modifier.height(12.dp))

        // Supabase
        SettingsCard("Supabase 云同步") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("当前：$syncStatus", fontSize = 13.sp,
                    color = if (syncStatus == "已配置") Green else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = supaUrl, onValueChange = { supaUrl = it },
                label = { Text("项目 URL") }, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://xxxx.supabase.co", fontSize = 13.sp) },
                singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = supaKey, onValueChange = { supaKey = it },
                label = { Text("Anon Key") }, modifier = Modifier.fillMaxWidth(),
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
                        } else "连接失败，请检查配置"
                    }
                }) { Text("保存并测试", color = androidx.compose.ui.graphics.Color.White) }
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
        Spacer(Modifier.height(12.dp))

        // AI 解析
        SettingsCard("AI 解析配置") {
            Text("填写 OpenAI 兼容多模态接口即可自动识题", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = apiUrl, onValueChange = { apiUrl = it },
                label = { Text("接口地址") }, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://api.openai.com/v1/chat/completions", fontSize = 12.sp) },
                singleLine = true)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = apiKey, onValueChange = { apiKey = it },
                    label = { Text("API Key") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = apiModel, onValueChange = { apiModel = it },
                    label = { Text("模型") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedButton(onClick = {
                    Store.settings["apiUrl"] = apiUrl.trim()
                    Store.settings["apiKey"] = apiKey.trim()
                    Store.saveSettings()
                    modelStatus = "获取中…"
                    scope.launch {
                        val models = AiParser.fetchModels()
                        modelStatus = if (models.isEmpty()) "未找到模型列表"
                        else "共 ${models.size} 个，已选前项填入"
                        if (models.isNotEmpty()) apiModel = models.first { it.contains("vl") || it.contains("vision") || it.contains("gpt-4o") } 
                    }
                }) { Text("获取\n列表", fontSize = 11.sp) }
            }
            if (modelStatus.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(modelStatus, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = {
                Store.settings["apiUrl"] = apiUrl.trim()
                Store.settings["apiKey"] = apiKey.trim()
                Store.settings["apiModel"] = apiModel.trim()
                Store.saveSettings()
                modelStatus = "已保存"
            }) { Text("保存配置", color = androidx.compose.ui.graphics.Color.White) }
        }
        Spacer(Modifier.height(12.dp))

        // 提取选项
        SettingsCard("提取选项") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("自动增强画质", fontSize = 13.5.sp)
                    Text("去阴影、提亮纸张、加深字迹", fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.outline)
                }
                Switch(checked = enhance, onCheckedChange = {
                    enhance = it
                    Store.settings["enhance"] = if (it) "1" else "0"
                    Store.saveSettings()
                })
            }
        }
        Spacer(Modifier.height(12.dp))

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
        Spacer(Modifier.height(12.dp))

        SettingsCard("关于") {
            Text("简错题 · AI 智能错题本 v5（原生版）", fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text("原生 Compose 界面 · CameraX 相机 · 八点透视裁剪 · 扫描增强 · 组卷打印 · 云同步",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.outline, lineHeight = 19.sp)
        }
        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}
