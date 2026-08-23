package com.jiancuoti.app.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** JSON 文件持久化（轻量，免 KSP/Room） */
object Store {
    private lateinit var appDir: File
    lateinit var imgDir: File
    lateinit var draftDir: File
    val settings = mutableMapOf<String, String>()

    var mistakes: MutableList<Mistake> = mutableListOf()
    var papers: MutableList<Paper> = mutableListOf()

    /** 数据版本号：任何数据变更后自增，Compose 界面以此自动刷新 */
    val revision = mutableIntStateOf(0)
    private fun bump() { revision.intValue++ }

    fun init(ctx: Context) {
        appDir = ctx.filesDir
        imgDir = File(appDir, "imgs").apply { mkdirs() }
        draftDir = File(appDir, "drafts").apply { mkdirs() }
        loadSettings()
        loadMistakes()
        loadPapers()
    }

    /** 草稿：拍摄/导入后未完成提取的图片（仅本地，不云同步） */
    fun drafts(): List<File> =
        draftDir.listFiles()?.filter { it.isFile && it.extension == "jpg" }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun saveDraft(file: File) {
        try { file.copyTo(File(draftDir, file.name), overwrite = true) } catch (_: Exception) {}
    }

    fun removeDraft(name: String) {
        try { File(draftDir, name).delete() } catch (_: Exception) {}
    }

    private fun file(name: String) = File(appDir, name)

    private fun loadSettings() {
        try {
            val j = JSONObject(file("settings.json").readText())
            j.keys().forEach { k -> settings[k] = j.optString(k) }
        } catch (_: Exception) {}
    }

    fun saveSettings() {
        val j = JSONObject()
        settings.forEach { (k, v) -> j.put(k, v) }
        file("settings.json").writeText(j.toString())
    }

    private fun loadMistakes() {
        try {
            val arr = JSONArray(file("mistakes.json").readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                mistakes.add(Mistake(
                    id = o.getString("id"),
                    subject = o.optString("subject", "其他"),
                    knowledge = o.optString("knowledge"),
                    question = o.optString("question"),
                    answer = o.optString("answer"),
                    analysis = o.optString("analysis"),
                    imageFile = o.optString("imageFile"),
                    errorCount = o.optInt("errorCount", 1),
                    mastered = o.optBoolean("mastered"),
                    parsedBy = o.optString("parsedBy", "manual"),
                    parsing = o.optBoolean("parsing", false),
                    variantOf = o.optString("variantOf"),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                ))
            }
            mistakes.sortByDescending { it.createdAt }
        } catch (_: Exception) {}
    }

    fun saveMistakes() {
        val arr = JSONArray()
        for (m in mistakes) {
            arr.put(JSONObject()
                .put("id", m.id)
                .put("subject", m.subject)
                .put("knowledge", m.knowledge)
                .put("question", m.question)
                .put("answer", m.answer)
                .put("analysis", m.analysis)
                .put("imageFile", m.imageFile)
                .put("errorCount", m.errorCount)
                .put("mastered", m.mastered)
                .put("parsedBy", m.parsedBy)
                .put("parsing", m.parsing)
                .put("variantOf", m.variantOf)
                .put("createdAt", m.createdAt)
                .put("updatedAt", m.updatedAt))
        }
        file("mistakes.json").writeText(arr.toString())
        bump()
    }

    private fun loadPapers() {
        try {
            val arr = JSONArray(file("papers.json").readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val qs = mutableListOf<String>()
                val qa = o.optJSONArray("questions") ?: JSONArray()
                for (k in 0 until qa.length()) qs.add(qa.getString(k))
                papers.add(Paper(
                    id = o.getString("id"),
                    name = o.optString("name"),
                    subjects = o.optString("subjects"),
                    count = o.optInt("count"),
                    questions = qs,
                    createdAt = o.optLong("createdAt", System.currentTimeMillis())
                ))
            }
            papers.sortByDescending { it.createdAt }
        } catch (_: Exception) {}
    }

    fun savePapers() {
        val arr = JSONArray()
        for (p in papers) {
            arr.put(JSONObject()
                .put("id", p.id)
                .put("name", p.name)
                .put("subjects", p.subjects)
                .put("count", p.count)
                .put("questions", JSONArray(p.questions))
                .put("createdAt", p.createdAt))
        }
        file("papers.json").writeText(arr.toString())
        bump()
    }

    fun imgFile(name: String): File? {
        if (name.isBlank()) return null
        val f = File(imgDir, name)
        return if (f.exists()) f else null
    }

    fun uid(): String =
        System.currentTimeMillis().toString(36) + (0..5).map { ('a'..'z').random() }.joinToString("")

    /** 导出全部数据为 JSON 字符串 */
    fun exportAll(): String {
        val root = JSONObject()
        root.put("exportedAt", System.currentTimeMillis())
        val ms = JSONArray()
        for (m in mistakes) {
            val o = JSONObject()
                .put("id", m.id).put("subject", m.subject).put("knowledge", m.knowledge)
                .put("question", m.question).put("answer", m.answer).put("analysis", m.analysis)
                .put("errorCount", m.errorCount).put("mastered", m.mastered)
                .put("parsedBy", m.parsedBy)
                .put("parsing", m.parsing).put("createdAt", m.createdAt).put("updatedAt", m.updatedAt)
            // 图片以 base64 带出
            imgFile(m.imageFile)?.let { f ->
                o.put("imageBase64", android.util.Base64.encodeToString(f.readBytes(), android.util.Base64.NO_WRAP))
            }
            ms.put(o)
        }
        root.put("mistakes", ms)
        return root.toString()
    }
}
