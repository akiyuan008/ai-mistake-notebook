package com.jiancuoti.app.net

import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 全局后台 AI 任务中心：
 * - 解析、举一反三都在这里跑，与任何界面生命周期无关
 * - 关闭弹窗、切换页面都不中断，结果写入状态表，界面随时读取
 */
object BgTasks {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** 举一反三结果：错题 id -> 变式题 */
    val variants = mutableStateMapOf<String, List<VariantQuestion>>()

    /** 举一反三进行中 / 失败信息 */
    val variantsBusy = mutableStateMapOf<String, Boolean>()
    val variantsError = mutableStateMapOf<String, String>()

    /** 全局提示（解析完成等） */
    val notice = mutableStateMapOf<String, String>()

    fun startVariants(m: MistakeLike, preferReal: Boolean) {
        val key = m.id
        if (variantsBusy[key] == true) return
        variantsBusy[key] = true
        variantsError.remove(key)
        scope.launch {
            try {
                val vs = AiParser.variants(
                    m.subject, m.knowledge, m.question, m.answer, preferReal
                )
                variantsBusy[key] = false
                if (vs.isEmpty()) {
                    variantsError[key] = "生成失败：未返回有效题目"
                } else {
                    variants[key] = vs
                }
            } catch (e: Exception) {
                variantsBusy[key] = false
                variantsError[key] = "生成失败：${e.message?.take(80) ?: "未知错误"}"
            }
        }
    }
}

/** BgTasks 需要的最小字段（var 属性同样可满足 val 接口） */
interface MistakeLike {
    val id: String
    val subject: String
    val knowledge: String
    val question: String
    val answer: String
}
