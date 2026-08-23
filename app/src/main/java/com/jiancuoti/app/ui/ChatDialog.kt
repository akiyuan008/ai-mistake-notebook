package com.jiancuoti.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.jiancuoti.app.net.AiParser
import com.jiancuoti.app.net.BgTasks
import com.jiancuoti.app.net.ChatMsg
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File

/** AI 对话：针对某道错题问答（文本 + 图片），后台运行不随弹窗关闭中断 */
@Composable
fun ChatDialog(
    contextText: String,          // 题目上下文
    contextImage: File?,          // 题目原图
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    var pendingImage by remember { mutableStateOf<Uri?>(null) }
    // 历史：user/assistant 交替
    var history by remember { mutableStateOf(listOf<ChatMsg>()) }
    // 展示用的图片记录（与消息一一对应，仅 user 消息可能有图）
    var imagesShown by remember { mutableStateOf(listOf<Uri?>()) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val imgLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) pendingImage = uri }

    // 预置题目上下文
    LaunchedEffect(Unit) {
        if (history.isEmpty()) {
            history = listOf(
                ChatMsg("user", buildString {
                    append("我在复习这道错题：\n")
                    append(contextText.take(600).ifBlank { "（见图片）" })
                    append("\n\n后续我会针对它提问，请先简短确认你已理解题目（不用解题）。")
                })
            )
            imagesShown = listOf(if (contextImage != null) Uri.fromFile(contextImage) else null)
        }
    }

    fun send() {
        val text = input.trim()
        if ((text.isBlank() && pendingImage == null) || busy) return
        if (!AiParser.configured) { err = "请先在「我的」页配置 AI 接口"; return }
        val imgUri = pendingImage
        val userMsg = ChatMsg("user", text.ifBlank { "请看这张图片" })
        history = history + userMsg
        imagesShown = imagesShown + imgUri
        input = ""
        pendingImage = null
        busy = true
        err = ""
        scope.launch {
            try {
                // 构造请求消息（含题目上下文与图片）
                val msgs = mutableListOf<ChatMsg>()
                val ctxImgB64 = contextImage?.let { fileToB64(it, 1280) }
                msgs.add(ChatMsg("user", "题目背景：${contextText.take(800).ifBlank { "见图片" }}", ctxImgB64))
                msgs.add(ChatMsg("assistant", "好的，我已了解这道题，请提问。"))
                history.forEach { m ->
                    val b64 = if (m === userMsg && imgUri != null) uriToB64(context, imgUri, 1280) else null
                    msgs.add(ChatMsg(m.role, m.text, b64))
                }
                val reply = AiParser.chat(msgs)
                history = history + ChatMsg("assistant", reply)
                imagesShown = imagesShown + null
            } catch (e: Exception) {
                err = "发送失败：${e.message?.take(90)}"
            } finally {
                busy = false
            }
        }
    }

    Dialog(onDismissRequest = onClose) {
        Surface(
            Modifier.fillMaxWidth().fillMaxHeight(0.9f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.fillMaxSize()) {
                // 顶栏
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("AI 对话", fontSize = 16.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text("关闭") }
                }
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                // 消息列表
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(history.size) { i ->
                        val m = history[i]
                        val isUser = m.role == "user"
                        Column(
                            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(
                                    topStart = if (isUser) 16.dp else 4.dp,
                                    topEnd = if (isUser) 4.dp else 16.dp,
                                    bottomStart = 16.dp, bottomEnd = 16.dp
                                ),
                                color = if (isUser) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    val img = imagesShown.getOrNull(i)
                                    if (isUser && img != null) {
                                        AsyncImage(
                                            model = img, contentDescription = null,
                                            modifier = Modifier.fillMaxWidth(0.6f)
                                                .heightIn(max = 160.dp)
                                                .clip(RoundedCornerShape(12.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        if (m.text.isNotBlank()) Spacer(Modifier.height(6.dp))
                                    }
                                    if (m.text.isNotBlank()) {
                                        if (isUser) Text(m.text, fontSize = 14.sp,
                                            color = Color.White, lineHeight = 21.sp)
                                        else MathText(m.text, fontSize = 14.sp, lineHeight = 21.sp,
                                            color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                    if (busy) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(4.dp)) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("思考中…", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                if (err.isNotBlank()) {
                    Text(err, fontSize = 12.sp, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 14.dp))
                }

                // 待发送图片预览
                pendingImage?.let { uri ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(model = uri, contentDescription = null,
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(8.dp))
                        Text("已选图片", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f))
                        IconButton(onClick = { pendingImage = null }) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // 输入栏
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    IconButton(onClick = { imgLauncher.launch("image/*") }) {
                        Icon(Icons.Default.AddPhotoAlternate, "发图",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(4.dp))
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f)
                    ) {
                        TextField(
                            value = input,
                            onValueChange = { input = it },
                            placeholder = { Text("问这道题…", fontSize = 14.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 4,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    FilledIconButton(
                        onClick = { send() },
                        enabled = !busy && (input.isNotBlank() || pendingImage != null),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, "发送")
                    }
                }
            }
        }
    }
}

private fun fileToB64(f: File, maxSide: Int): String? = try {
    val bmp = BitmapFactory.decodeFile(f.absolutePath) ?: return null
    val small = if (bmp.width > maxSide)
        Bitmap.createScaledBitmap(bmp, maxSide, bmp.height * maxSide / bmp.width, true) else bmp
    val bos = ByteArrayOutputStream()
    small.compress(Bitmap.CompressFormat.JPEG, 85, bos)
    android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP)
} catch (_: Exception) { null }

private fun uriToB64(context: android.content.Context, uri: Uri, maxSide: Int): String? = try {
    val bmp = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it)
    } ?: return null
    val small = if (bmp.width > maxSide)
        Bitmap.createScaledBitmap(bmp, maxSide, bmp.height * maxSide / bmp.width, true) else bmp
    val bos = ByteArrayOutputStream()
    small.compress(Bitmap.CompressFormat.JPEG, 85, bos)
    android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP)
} catch (_: Exception) { null }
