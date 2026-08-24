package com.jiancuoti.app.net

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import com.jiancuoti.app.data.Store
import com.jiancuoti.app.ui.loadBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 全局后台 AI 任务中心：
 * - 解析队列串行排队，与任何界面生命周期无关
 * - 关闭页面/返回都不中断，完成后弹提示
 */
object BgTasks {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** 举一反三结果：错题 id -> 变式题 */
    val variants = mutableStateMapOf<String, List<VariantQuestion>>()
    val variantsBusy = mutableStateMapOf<String, Boolean>()
    val variantsError = mutableStateMapOf<String, String>()

    /** 全局提示（解析完成等） */
    val notice = mutableStateMapOf<String, String>()

    /** 全局 Toast 通道 */
    val globalToast = mutableStateOf(0L to "")
    fun toast(msg: String) {
        globalToast.value = System.currentTimeMillis() to msg
    }

    /* ---------------- 解析队列 ---------------- */

    data class ParseTask(
        val mistakeId: String,
        val mode: String,      // full=完整解析 knowledge=仅知识点 variants=举一反三
        var status: String,    // wait / doing / done / fail
        val extra: String = ""
    )

    val parseQueue = mutableStateListOf<ParseTask>()
    private var workerRunning = false

    /** 入队（同一题不重复入队） */
    fun enqueueParse(mistakeId: String, mode: String = "full") {
        if (parseQueue.any { it.mistakeId == mistakeId && (it.status == "wait" || it.status == "doing") }) return
        parseQueue.add(ParseTask(mistakeId, mode, "wait"))
        AppLog.log("解析", "入队 mistakeId=$mistakeId mode=$mode 队列长度=${parseQueue.size}")
        pump()
    }

    val activeCount: Int
        get() = parseQueue.count { it.status == "wait" || it.status == "doing" }

    private fun pump() {
        if (workerRunning) return
        val next = parseQueue.firstOrNull { it.status == "wait" } ?: return
        workerRunning = true
        next.status = "doing"
        scope.launch {
            try {
                processTask(next)
                next.status = "done"
                AppLog.log("解析", "完成 mistakeId=${next.mistakeId}")
            } catch (e: Exception) {
                next.status = "fail"
                AppLog.log("解析", "失败 mistakeId=${next.mistakeId}: ${e.message?.take(120)}")
                val m = Store.mistakes.firstOrNull { it.id == next.mistakeId }
                if (m != null) {
                    m.parsing = false
                    Store.saveMistakes()
                }
                toast("解析失败：${e.message?.take(60)}")
            }
            // 完成/失败的任务 8 秒后移出队列
            scope.launch {
                delay(8000)
                parseQueue.remove(next)
            }
            workerRunning = false
            pump()
        }
    }

    private suspend fun processTask(task: ParseTask) {
        val m = Store.mistakes.firstOrNull { it.id == task.mistakeId } ?: return
        if (task.mode == "variants") {
            try {
                val vs = AiParser.variants(
                    m.subject, m.knowledge, m.question, m.answer, task.extra == "1"
                )
                variantsBusy[m.id] = false
                if (vs.isEmpty()) {
                    variantsError[m.id] = "生成失败：未返回有效题目"
                } else {
                    variants[m.id] = vs
                    toast("举一反三已生成 ${vs.size} 道变式题")
                }
            } catch (e: Exception) {
                variantsBusy[m.id] = false
                variantsError[m.id] = "生成失败：${e.message?.take(80) ?: "未知错误"}"
            }
            return
        }
        val f = Store.imgFile(m.imageFile) ?: throw Exception("该题没有图片")
        val bmp = withContext(Dispatchers.IO) { loadBitmap(f, 1600) }
        if (task.mode == "full") {
            val r = AiParser.parse(bmp)
            m.subject = r.subject
            m.knowledge = r.knowledge
            m.question = r.question
            m.answer = r.answer
            m.analysis = r.analysis
            m.parsedBy = r.by
        } else {
            val r = AiParser.knowledgeOnly(bmp)
            if (r.second.isNotBlank()) m.knowledge = r.second
            if (r.first.isNotBlank() && m.subject == "其他") m.subject = r.first
        }
        m.parsing = false
        m.updatedAt = System.currentTimeMillis()
        Store.saveMistakes()
        scope.launch { Supabase.pushMistake(m) }
        val label = m.knowledge.ifBlank { m.subject }.take(14)
        toast(if (task.mode == "full") "解析完成：$label" else "知识点识别完成：$label")
    }

    /** APP 启动时：把上次未完成的解析重新入队 */
    fun resumePending() {
        if (!AiParser.configured) {
            var changed = false
            Store.mistakes.forEach { m ->
                if (m.parsing) { m.parsing = false; changed = true }
            }
            if (changed) Store.saveMistakes()
            return
        }
        val full = Store.settings["autoParse"] != "0"
        Store.mistakes.filter { it.parsing }.forEach { m ->
            enqueueParse(m.id, if (full) "full" else "knowledge")
        }
    }

    /** 云同步轮询：每 3 分钟拉取一次（文字数据） */
    fun startSyncLoop() {
        scope.launch {
            while (true) {
                delay(3 * 60 * 1000)
                if (Supabase.configured) {
                    try {
                        Supabase.pull()
                        Supabase.pullPapers()
                    } catch (_: Exception) {}
                }
            }
        }
    }

    /* ---------------- 举一反三 ---------------- */

    /** 举一反三：进全局队列（排队执行，可在「我的」页看进度） */
    fun startVariants(m: MistakeLike, preferReal: Boolean) {
        val key = m.id
        if (variantsBusy[key] == true) return
        if (parseQueue.any { it.mistakeId == key && it.mode == "variants" && (it.status == "wait" || it.status == "doing") }) return
        variantsBusy[key] = true
        variantsError.remove(key)
        parseQueue.add(ParseTask(key, "variants", "wait", if (preferReal) "1" else "0"))
        AppLog.log("举一反三", "入队 ${m.id} 队列长度=${parseQueue.size}")
        pump()
    }
}

/** BgTasks 需要的最小字段 */
interface MistakeLike {
    val id: String
    val subject: String
    val knowledge: String
    val question: String
    val answer: String
}
