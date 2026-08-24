package com.jiancuoti.app.net

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用日志系统：滚动写入 filesDir/app_log.txt（上限 512KB，自动截断保留后半）
 * 记录：AI 请求（地址/状态码/错误）、解析队列、云同步、关键操作
 * 在「我的」页可查看 / 分享 / 清除，便于远程排查问题
 */
object AppLog {
    private var file: File? = null
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private const val MAX_SIZE = 512 * 1024L

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "app_log.txt")
        log("APP", "启动 | ${android.os.Build.MODEL} API${android.os.Build.VERSION.SDK_INT}")
    }

    @Synchronized
    fun log(tag: String, msg: String) {
        try {
            val f = file ?: return
            if (f.length() > MAX_SIZE) {
                // 滚动：只保留后 256KB
                val tail = f.readText().takeLast(256 * 1024)
                f.writeText(tail)
            }
            f.appendText("[${fmt.format(Date())}][$tag] $msg\n")
        } catch (_: Exception) {}
    }

    fun read(): String = try {
        file?.readText()?.takeLast(60_000) ?: "（无日志）"
    } catch (e: Exception) { "读取失败：$e" }

    fun fileRef(): File? = file

    fun clear() {
        try { file?.writeText("") } catch (_: Exception) {}
    }
}
