package com.jiancuoti.app.net

import android.graphics.Bitmap
import android.util.Base64
import com.jiancuoti.app.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class ParseResult(
    val subject: String, val knowledge: String,
    val question: String, val answer: String, val analysis: String,
    val by: String
)

/** 举一反三的变式题 */
data class VariantQuestion(
    val question: String,
    val answer: String,
    val hint: String,
    val difficulty: Int
)

object AiParser {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    val configured: Boolean
        get() = Store.settings["apiUrl"].isNullOrBlank().not() && Store.settings["apiKey"].isNullOrBlank().not()

    /** 规范化 chat/completions 地址：用户可能只填了 base（如 https://x.com 或 https://x.com/v1） */
    private fun chatUrls(raw: String): List<String> {
        val u = raw.trim().trimEnd('/')
        val base = u.removeSuffix("/chat/completions")
            .removeSuffix("/v1")
        return linkedSetOf(
            u,
            "$base/v1/chat/completions",
            "$base/chat/completions"
        ).filter { it.startsWith("http") }
    }

    /** 统一请求：逐个候选地址尝试，404 换下一个，给出可读错误 */
    private fun postChat(url: String, key: String, body: String): JSONObject {
        var lastErr: Exception? = null
        for (ep in chatUrls(url)) {
            try {
                val req = Request.Builder().url(ep)
                    .header("Authorization", "Bearer $key")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.code == 404) return@use  // 试下一个候选
                    if (!resp.isSuccessful) {
                        val msg = resp.body?.string()?.take(300) ?: ""
                        throw Exception("接口返回 ${resp.code}：${msg.ifBlank { ep }}")
                    }
                    return JSONObject(resp.body!!.string())
                }
            } catch (e: Exception) {
                if (e.message?.startsWith("接口返回") == true) throw e
                lastErr = e
            }
        }
        throw Exception("所有地址均 404，请检查接口地址（需为 OpenAI 兼容地址，可只填站点根地址）")
    }

    private fun JSONObject.extractContent(): String {
        var text = optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("message")?.optString("content") ?: ""
        return text.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    }

    suspend fun parse(bmp: Bitmap): ParseResult = withContext(Dispatchers.IO) {
        if (!configured) {
            // 未配置接口：保留图片，文本留空待手动补充
            return@withContext ParseResult(Store.settings["defaultSubject"] ?: "数学", "", "", "", "", "manual")
        }
        val url = Store.settings["apiUrl"]!!
        val key = Store.settings["apiKey"]!!
        val model = Store.settings["apiModel"].takeUnless { it.isNullOrBlank() } ?: "gpt-4o-mini"

        val bos = ByteArrayOutputStream()
        val small = if (bmp.width > 1280)
            Bitmap.createScaledBitmap(bmp, 1280, bmp.height * 1280 / bmp.width, true) else bmp
        small.compress(Bitmap.CompressFormat.JPEG, 85, bos)
        val b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)

        val body = JSONObject().apply {
            put("model", model)
            put("max_tokens", 1200)
            put("messages", JSONArray()
                .put(JSONObject().put("role", "system")
                    .put("content", "你是错题整理助手。观察题目图片，只输出 JSON（不要 markdown），字段：subject(限：数学/语文/英语/物理/化学/生物/历史/地理/政治/其他)、knowledge(知识点，简短)、question(完整题干，公式用LaTeX写在\$中)、answer(正确答案)、analysis(分步解析，简洁)。"))
                .put(JSONObject().put("role", "user").put("content", JSONArray()
                    .put(JSONObject().put("type", "text").put("text", "请解析这道题并按要求输出 JSON。"))
                    .put(JSONObject().put("type", "image_url")
                        .put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$b64"))))))
        }.toString()

        val data = postChat(url, key, body)
        val text = data.extractContent()
        val m = Regex("\\{[\\s\\S]*\\}").find(text) ?: throw Exception("返回无法解析")
        val j = JSONObject(m.value)
        val subj = j.optString("subject")
        return@withContext ParseResult(
            subject = if (com.jiancuoti.app.data.SUBJECTS.contains(subj)) subj else "其他",
            knowledge = j.optString("knowledge"),
            question = j.optString("question"),
            answer = j.optString("answer"),
            analysis = j.optString("analysis"),
            by = "api"
        )
    }

    /** 举一反三：基于原题生成 2-3 道同考点变式题（含答案与提示） */
    suspend fun variants(subject: String, knowledge: String, question: String, answer: String): List<VariantQuestion> =
        withContext(Dispatchers.IO) {
            if (!configured) return@withContext emptyList()
            val url = Store.settings["apiUrl"]!!
            val key = Store.settings["apiKey"]!!
            val model = Store.settings["apiModel"].takeUnless { it.isNullOrBlank() } ?: "gpt-4o-mini"

            val prompt = """
                你是错题复习助手。下面是一道学生的错题：
                科目：$subject
                知识点：$knowledge
                题干：${question.ifBlank { "（图片题，见描述）" }}
                答案：$answer

                请基于同一知识点出 3 道由易到难的变式练习题，用于举一反三。
                只输出 JSON 数组（不要 markdown 代码块），每个元素字段：
                question(新题干)、answer(参考答案)、hint(一句话思路提示)、difficulty(1到3的整数)。
            """.trimIndent()

            val body = JSONObject().apply {
                put("model", model)
                put("max_tokens", 1600)
                put("messages", JSONArray()
                    .put(JSONObject().put("role", "system")
                        .put("content", "你是出题专家，只输出 JSON 数组，不要任何解释。"))
                    .put(JSONObject().put("role", "user").put("content", prompt)))
            }.toString()

                val data = postChat(url, key, body)
                val text = data.extractContent()
                val m = Regex("\\[[\\s\\S]*\\]").find(text) ?: throw Exception("返回无法解析")
                val arr = JSONArray(m.value)
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    VariantQuestion(
                        question = o.optString("question"),
                        answer = o.optString("answer"),
                        hint = o.optString("hint"),
                        difficulty = o.optInt("difficulty", 1).coerceIn(1, 3)
                    )
                }.filter { it.question.isNotBlank() }
        }

    /** 从接口拉取模型列表 */
    suspend fun fetchModels(): List<String> = withContext(Dispatchers.IO) {
        val apiUrl = Store.settings["apiUrl"] ?: return@withContext emptyList()
        val key = Store.settings["apiKey"] ?: return@withContext emptyList()
        val base = apiUrl.replace(Regex("/v1/.*$"), "").removeSuffix("/")
        val endpoints = listOf("$base/v1/models", "$base/models")
        for (ep in endpoints) {
            try {
                val req = Request.Builder().url(ep).header("Authorization", "Bearer $key").get().build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val j = JSONObject(resp.body!!.string())
                        val arr = j.optJSONArray("data") ?: j.optJSONArray("models") ?: return@use
                        val out = mutableListOf<String>()
                        for (i in 0 until arr.length()) {
                            val o = arr.optJSONObject(i)
                            out.add(o?.optString("id") ?: arr.optString(i))
                        }
                        if (out.isNotEmpty()) return@withContext out.sorted()
                    }
                }
            } catch (_: Exception) {}
        }
        emptyList()
    }
}
