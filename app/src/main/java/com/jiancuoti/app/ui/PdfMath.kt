package com.jiancuoti.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.max
import kotlin.math.min

/**
 * PDF 公式渲染引擎：把（已修复的）LaTeX 渲染成印刷级位图
 * - 竖式分数（分子/分数线/分母）
 * - 真实上下标（小字上移/下移）
 * - 变量斜体、函数正体、希腊字母/数学符号
 */
object PdfMath {

    sealed class El
    data class T(val s: String, val italic: Boolean, val sizeMul: Float = 1f) : El()
    data class Frac(val num: List<El>, val den: List<El>) : El()
    data class Sup(val inner: List<El>) : El()
    data class Sub(val inner: List<El>) : El()

    private class Reader(val s: String) {
        var i = 0
        fun readArg(): String {
            if (i >= s.length) return ""
            if (s[i] == '{') {
                var depth = 0
                val start = i + 1
                while (i < s.length) {
                    if (s[i] == '{') depth++
                    else if (s[i] == '}') { depth--; if (depth == 0) break }
                    i++
                }
                val content = s.substring(start, i.coerceAtMost(s.length))
                i++
                return content
            }
            if (s[i] == '\\') {
                var j = i + 1
                while (j < s.length && s[j].isLetter()) j++
                if (j == i + 1 && j < s.length) j++
                val t = s.substring(i, j.coerceAtMost(s.length))
                i = j
                return t
            }
            return s[i++].toString()
        }
        fun readCmd(): String {
            var j = i + 1
            while (j < s.length && s[j].isLetter()) j++
            if (j == i + 1 && j < s.length) j++
            val name = s.substring(i, j.coerceAtMost(s.length))
            i = j
            return name
        }
    }

    /** 解析（已 normalize 的）LaTeX 为元素序列 */
    fun parse(latex: String): List<El> {
        val r = Reader(latex.trim())
        val out = mutableListOf<El>()
        while (r.i < r.s.length) {
            val c = r.s[r.i]
            when {
                c == '\\' -> {
                    val name = r.readCmd()
                    when (name) {
                        "\\frac", "\\dfrac", "\\tfrac" ->
                            out.add(Frac(parse(r.readArg()), parse(r.readArg())))
                        "\\sqrt" -> {
                            out.add(T("√", false))
                            out.addAll(parse(r.readArg()))
                        }
                        "\\text", "\\mathrm", "\\operatorname", "\\mathbf", "\\mathit" ->
                            out.add(T(r.readArg(), false))
                        "\\mathbb" -> {
                            val inner = r.readArg()
                            out.add(T(inner.map { MATHBB[it.toString()] ?: it.toString() }
                                .joinToString(""), false))
                        }
                        "\\overline" -> {
                            out.addAll(parse(r.readArg()))
                            out.add(T("‾", false, 0.8f))
                        }
                        "\\vec" -> {
                            out.addAll(parse(r.readArg()))
                            out.add(T("→", false, 0.7f))
                        }
                        else -> out.add(T(SYMBOLS[name] ?: name.removePrefix("\\"), false))
                    }
                }
                c == '^' -> { r.i++; out.add(Sup(parse(r.readArg()))) }
                c == '_' -> { r.i++; out.add(Sub(parse(r.readArg()))) }
                c == '{' || c == '}' -> r.i++
                c.isLetter() && c.code < 128 -> { out.add(T(c.toString(), true)); r.i++ }
                else -> { out.add(T(c.toString(), false)); r.i++ }
            }
        }
        return out
    }

    private data class M(val w: Float, val asc: Float, val desc: Float)

    private const val FRAC_SUB = 0.78f
    private const val SUP_SUB = 0.66f
    private const val AXIS = 0.26f      // 数学轴高度（em）
    private const val GAP = 0.14f       // 分数线与分子分母间距（em）

    private fun measure(els: List<El>, paint: Paint, baseSize: Float): M {
        var w = 0f; var asc = 0f; var desc = 0f
        val saved = paint.textSize
        for (e in els) {
            when (e) {
                is T -> {
                    paint.textSize = baseSize * e.sizeMul
                    val fw = paint.measureText(e.s)
                    w += fw
                    val fm = paint.fontMetrics
                    asc = max(asc, -fm.ascent)
                    desc = max(desc, fm.descent)
                }
                is Frac -> {
                    paint.textSize = baseSize * FRAC_SUB
                    val numM = measure(e.num, paint, baseSize * FRAC_SUB)
                    val denM = measure(e.den, paint, baseSize * FRAC_SUB)
                    val fw = max(numM.w, denM.w) + baseSize * 0.28f
                    w += fw
                    val numH = numM.asc + numM.desc
                    val denH = denM.asc + denM.desc
                    val lineW = max(1.5f, baseSize * 0.06f)
                    asc = max(asc, AXIS * baseSize + GAP * baseSize + numH + lineW)
                    desc = max(desc, denH + GAP * baseSize + lineW - AXIS * baseSize)
                }
                is Sup -> {
                    val m = measure(e.inner, paint, baseSize * SUP_SUB)
                    w += m.w
                    asc = max(asc, m.asc + 0.40f * baseSize)
                    desc = max(desc, m.desc - 0.40f * baseSize)
                }
                is Sub -> {
                    val m = measure(e.inner, paint, baseSize * SUP_SUB)
                    w += m.w
                    asc = max(asc, m.asc - 0.18f * baseSize)
                    desc = max(desc, m.desc + 0.18f * baseSize)
                }
            }
        }
        paint.textSize = saved
        return M(w, asc, desc)
    }

    private fun drawEls(
        canvas: Canvas, els: List<El>, paint: Paint,
        baseSize: Float, x0: Float, baselineY: Float
    ): Float {
        var x = x0
        val savedSize = paint.textSize
        val savedSkew = paint.textSkewX
        for (e in els) {
            when (e) {
                is T -> {
                    paint.textSize = baseSize * e.sizeMul
                    paint.textSkewX = if (e.italic) -0.22f else 0f
                    canvas.drawText(e.s, x, baselineY, paint)
                    x += paint.measureText(e.s)
                }
                is Frac -> {
                    val sub = baseSize * FRAC_SUB
                    paint.textSize = sub
                    val numM = measure(e.num, paint, sub)
                    val denM = measure(e.den, paint, sub)
                    val fw = max(numM.w, denM.w) + baseSize * 0.28f
                    val lineW = max(1.5f, baseSize * 0.06f)
                    val axisY = baselineY - AXIS * baseSize
                    val gap = GAP * baseSize
                    // 分子
                    paint.textSize = sub
                    paint.textSkewX = 0f
                    val fm = paint.fontMetrics
                    val numY = axisY - gap - fm.descent
                    canvas.drawText(flatText(e.num), x + (fw - numM.w) / 2, numY, paint)
                    // 分数线
                    val linePaint = Paint(paint).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = lineW
                        strokeCap = Paint.Cap.ROUND
                    }
                    canvas.drawLine(x + baseSize * 0.04f, axisY, x + fw - baseSize * 0.04f, axisY, linePaint)
                    // 分母
                    val denY = axisY + gap - fm.ascent
                    canvas.drawText(flatText(e.den), x + (fw - denM.w) / 2, denY, paint)
                    x += fw
                }
                is Sup -> {
                    x = drawEls(canvas, e.inner, paint, baseSize * SUP_SUB, x, baselineY - 0.40f * baseSize)
                }
                is Sub -> {
                    x = drawEls(canvas, e.inner, paint, baseSize * SUP_SUB, x, baselineY + 0.18f * baseSize)
                }
            }
        }
        paint.textSize = savedSize
        paint.textSkewX = savedSkew
        return x
    }

    /** 分数分子/分母的文本（子元素若还有嵌套按扁平文本近似） */
    private fun flatText(els: List<El>): String {
        val sb = StringBuilder()
        for (e in els) {
            when (e) {
                is T -> sb.append(e.s)
                is Frac -> sb.append(flatText(e.num)).append("⁄").append(flatText(e.den))
                is Sup -> sb.append(flatText(e.inner))
                is Sub -> sb.append(flatText(e.inner))
            }
        }
        return sb.toString()
    }

    /** 渲染公式为位图，返回 (位图, 基线距顶部距离) */
    private val cache = HashMap<String, Pair<Bitmap, Float>>()

    fun render(latex: String, paint: Paint, baseSize: Float, color: Int): Pair<Bitmap, Float>? {
        val key = "$latex|$baseSize|$color"
        cache[key]?.let { return it }
        val els = parse(latex)
        if (els.isEmpty()) return null
        val saved = paint.textSize
        val m = measure(els, paint, baseSize)
        paint.textSize = saved
        val w = (m.w + 6f).toInt().coerceAtLeast(2)
        val h = (m.asc + m.desc + 4f).toInt().coerceAtLeast(2)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(paint).apply {
            this.color = color
            textSize = baseSize
            isAntiAlias = true
            textSkewX = 0f
        }
        drawEls(c, els, p, baseSize, 3f, m.asc + 2f)
        if (cache.size > 300) cache.clear()
        val result = bmp to (m.asc + 2f)
        cache[key] = result
        return result
    }
}

/**
 * PDF 富文本排版：文字 + 公式位图混排，自动换行/跨页
 */
class PdfRichText(
    var canvas: Canvas,
    private val paint: Paint,
    private val x0: Float,
    private val maxWidth: Float,
    var y: Float,
    private val maxY: Float,
    private val onPageBreak: () -> Unit
) {
    private sealed class Seg {
        data class Text(val s: String) : Seg()
        data class Formula(val latex: String, val bmp: Bitmap, val baseline: Float) : Seg()
    }

    private data class Placed(val seg: Seg, val w: Float, val asc: Float, val desc: Float)

    /** 绘制混排文本，返回结束 y */
    fun draw(mixed: String): Float {
        val text = normalizeMathText(stripMarkdown(mixed))
        val segs = segment(text)
        if (segs.isEmpty()) return y

        val lineItems = mutableListOf<Placed>()
        var curW = 0f
        var lineAsc = -paint.fontMetrics.ascent
        var lineDesc = paint.fontMetrics.descent
        val baseLineH = paint.textSize * 1.5f

        fun flush() {
            if (lineItems.isEmpty()) return
            if (y + lineAsc + lineDesc + 4f > maxY) {
                onPageBreak()
                lineAsc = -paint.fontMetrics.ascent
                lineDesc = paint.fontMetrics.descent
            }
            val baseline = y + lineAsc
            var x = x0
            for (it in lineItems) {
                when (val s = it.seg) {
                    is Seg.Text -> {
                        paint.textSize = baseTextSize
                        canvas.drawText(s.s, x, baseline, paint)
                    }
                    is Seg.Formula -> {
                        canvas.drawBitmap(s.bmp, x, baseline - s.baseline, null)
                    }
                }
                x += it.w
            }
            y += max(baseLineH, lineAsc + lineDesc + paint.textSize * 0.28f)
            lineItems.clear()
            curW = 0f
            lineAsc = -paint.fontMetrics.ascent
            lineDesc = paint.fontMetrics.descent
        }

        for (seg in segs) {
            when (seg) {
                is Seg.Formula -> {
                    val m = seg.bmp.width.toFloat()
                    if (curW + m > maxWidth && lineItems.isNotEmpty()) flush()
                    lineItems.add(Placed(seg, m, seg.baseline, seg.bmp.height - seg.baseline))
                    curW += m
                    lineAsc = max(lineAsc, seg.baseline)
                    lineDesc = max(lineDesc, seg.bmp.height - seg.baseline)
                }
                is Seg.Text -> {
                    // 拆成可换行单元：CJK 单字、拉丁单词、标点、\n 强制换行
                    val units = breakUnits(seg.s)
                    for (u in units) {
                        if (u == "\n") { flush(); continue }
                        paint.textSize = baseTextSize
                        val uw = paint.measureText(u)
                        if (curW + uw > maxWidth && lineItems.isNotEmpty()) flush()
                        val fm = paint.fontMetrics
                        lineItems.add(Placed(Seg.Text(u), uw, -fm.ascent, fm.descent))
                        curW += uw
                        lineAsc = max(lineAsc, -fm.ascent)
                        lineDesc = max(lineDesc, fm.descent)
                    }
                }
            }
        }
        flush()
        return y
    }

    private val baseTextSize = paint.textSize

    /** 分段：$...$ 为公式；普通段按 CJK/非CJK 切分，含 \ 命令或 ^_ 的非CJK段按公式 */
    private fun segment(text: String): List<Seg> {
        val result = mutableListOf<Seg>()
        data class Span(val start: Int, val end: Int)
        val spans = mutableListOf<Span>()
        fun collect(pattern: Regex) {
            pattern.findAll(text).forEach { m ->
                val s = m.range.first; val e = m.range.last + 1
                if (spans.none { s < it.end && e > it.start }) spans.add(Span(s, e))
            }
        }
        collect(Regex("\\$\\$(.+?)\\$\\$", RegexOption.DOT_MATCHES_ALL))
        collect(Regex("\\\\\\[(.+?)\\\\\\]", RegexOption.DOT_MATCHES_ALL))
        collect(Regex("\\\\\\((.+?)\\\\\\)", RegexOption.DOT_MATCHES_ALL))
        collect(Regex("\\$([^$\\n]+?)\\$"))
        spans.sortBy { it.start }

        fun addPlain(seg: String) {
            if (seg.isEmpty()) return
            // 按 CJK 边界切分
            val sb = StringBuilder()
            var prevCjk: Boolean? = null
            fun flushBuf() {
                if (sb.isEmpty()) return
                val s = sb.toString()
                val hasMath = Regex("\\\\[a-zA-Z]|\\^|_").containsMatchIn(s) && !s.all { isCjk(it) }
                if (hasMath) {
                    val inner = Regex("\\\\left|\\\\right").replace(s, "")
                    PdfMath.render(inner, paint, paint.textSize, paint.color)?.let { (bmp, bl) ->
                        result.add(Seg.Formula(s, bmp, bl))
                    } ?: result.add(Seg.Text(s))
                } else {
                    result.add(Seg.Text(s))
                }
                sb.clear()
            }
            for (c in seg) {
                val cjk = isCjk(c)
                if (prevCjk != null && cjk != prevCjk) flushBuf()
                sb.append(c)
                prevCjk = cjk
            }
            flushBuf()
        }

        var pos = 0
        for (sp in spans) {
            if (sp.start > pos) addPlain(text.substring(pos, sp.start))
            val raw = text.substring(sp.start, sp.end)
            val inner = Regex("\\$\\$|\\\\\\[|\\\\\\]|\\\\\\(|\\\\\\)|\\$").replace(raw, "")
            PdfMath.render(inner, paint, paint.textSize, paint.color)?.let { (bmp, bl) ->
                result.add(Seg.Formula(inner, bmp, bl))
            } ?: result.add(Seg.Text(inner))
            pos = sp.end
        }
        if (pos < text.length) addPlain(text.substring(pos))
        return result
    }

    private fun isCjk(c: Char): Boolean =
        c.code in 0x4E00..0x9FFF || c.code in 0x3400..0x4DBF ||
        c.code in 0xFF00..0xFFEF || c.code in 0x3000..0x303F

    /** 可换行单元：CJK 逐字，拉丁按单词/符号块 */
    private fun breakUnits(s: String): List<String> {
        val units = mutableListOf<String>()
        val sb = StringBuilder()
        var mode = 0 // 0=初始 1=CJK 2=拉丁
        fun flush() { if (sb.isNotEmpty()) { units.add(sb.toString()); sb.clear() } }
        for (c in s) {
            if (c == '\n') { flush(); units.add("\n"); mode = 0; continue }
            val cjk = isCjk(c)
            val m = if (cjk) 1 else 2
            if (m != mode) flush()
            mode = m
            if (cjk) { units.add(c.toString()) } else {
                if (c == ' ' && sb.isNotEmpty()) { sb.append(c); flush() }
                else sb.append(c)
            }
        }
        flush()
        return units
    }
}
