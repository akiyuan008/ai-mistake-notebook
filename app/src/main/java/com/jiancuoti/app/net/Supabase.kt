package com.jiancuoti.app.net

import com.jiancuoti.app.data.Mistake
import com.jiancuoti.app.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** Supabase REST 同步 */
object Supabase {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    val configured: Boolean
        get() = !Store.settings["supaUrl"].isNullOrBlank() && !Store.settings["supaKey"].isNullOrBlank()

    private fun req(path: String, method: String, body: String? = null): Request.Builder {
        val url = Store.settings["supaUrl"]!!.removeSuffix("/") + "/rest/v1/" + path
        val b = Request.Builder().url(url)
            .header("apikey", Store.settings["supaKey"]!!)
            .header("Authorization", "Bearer " + Store.settings["supaKey"]!!)
            .header("Prefer", "resolution=merge-duplicates,return=minimal")
        when (method) {
            "POST" -> b.post((body ?: "{}").toRequestBody("application/json".toMediaType()))
            "DELETE" -> b.delete()
            else -> b.get()
        }
        return b
    }

    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            client.newCall(req("mistakes?select=id&limit=1", "GET").build())
                .execute().use { it.isSuccessful }
        } catch (e: Exception) { false }
    }

    suspend fun pushMistake(m: Mistake) = withContext(Dispatchers.IO) {
        if (!configured) return@withContext
        try {
            // 注意：图片不上传云端（体积控制），只传文本字段
            val j = JSONObject()
                .put("id", m.id).put("subject", m.subject).put("knowledge", m.knowledge)
                .put("topic", "").put("question", m.question).put("answer", m.answer)
                .put("analysis", m.analysis).put("image", "")
                .put("error_count", m.errorCount).put("mastered", m.mastered)
                .put("parsed_by", m.parsedBy)
                .put("variant_of", m.variantOf)
                .put("created_at", iso.format(Date(m.createdAt)))
                .put("updated_at", iso.format(Date(m.updatedAt)))
            client.newCall(req("mistakes", "POST", j.toString()).build()).execute().use {}
        } catch (_: Exception) {}
    }

    suspend fun deleteMistake(id: String) = withContext(Dispatchers.IO) {
        if (!configured) return@withContext
        try {
            client.newCall(req("mistakes?id=eq.$id", "DELETE").build()).execute().use {}
        } catch (_: Exception) {}
    }

    /** 拉取云端错题（合并：以 updatedAt 新者为准），返回合并条数 */
    suspend fun pull(): Int = withContext(Dispatchers.IO) {
        if (!configured) return@withContext 0
        try {
            val resp = client.newCall(
                req("mistakes?select=*", "GET").header("Prefer", "return=representation").build()
            ).execute()
            if (!resp.isSuccessful) return@withContext 0
            val arr = org.json.JSONArray(resp.body!!.string())
            var merged = 0
            val local = Store.mistakes.associateBy { it.id }
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.optString("id")
                val rt = parseIso(o.optString("updated_at"))
                val lm = local[id]
                if (lm == null || (lm.updatedAt) < rt) {
                    if (lm == null) {
                        Store.mistakes.add(Mistake(
                            id = id, subject = o.optString("subject", "其他"),
                            knowledge = o.optString("knowledge"),
                            question = o.optString("question"),
                            answer = o.optString("answer"),
                            analysis = o.optString("analysis"),
                            errorCount = o.optInt("error_count", 1),
                            mastered = o.optBoolean("mastered"),
                            parsedBy = o.optString("parsed_by", "cloud"),
                            variantOf = o.optString("variant_of"),
                            createdAt = parseIso(o.optString("created_at")),
                            updatedAt = rt
                        ))
                        merged++
                    }
                }
            }
            if (merged > 0) {
                Store.mistakes.sortByDescending { it.createdAt }
                Store.saveMistakes()
            }
            merged
        } catch (e: Exception) { 0 }
    }

    private fun parseIso(s: String): Long =
        try { iso.parse(s)?.time ?: System.currentTimeMillis() } catch (e: Exception) { System.currentTimeMillis() }
}
