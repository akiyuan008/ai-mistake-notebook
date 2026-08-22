package com.jiancuoti.app.data

/** 错题 */
data class Mistake(
    val id: String,
    var subject: String = "其他",
    var knowledge: String = "",
    var question: String = "",
    var answer: String = "",
    var analysis: String = "",
    var imageFile: String = "",      // 本地文件名（存于 filesDir/imgs）
    var errorCount: Int = 1,
    var mastered: Boolean = false,
    var parsedBy: String = "manual",
    var parsing: Boolean = false,      // 后台 AI 解析进行中
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) : com.jiancuoti.app.net.MistakeLike

/** 试卷（组卷记录） */
data class Paper(
    val id: String,
    var name: String,
    var subjects: String = "",
    var count: Int = 0,
    var questions: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

val SUBJECTS = listOf("数学","语文","英语","物理","化学","生物","历史","地理","政治","其他")
