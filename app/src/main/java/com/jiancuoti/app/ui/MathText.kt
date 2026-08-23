package com.jiancuoti.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 公式渲染（印刷级排版目标）：
 * - \frac 竖式分数：占位高度充足（2.9em），分子/分数线/分母完整显示
 * - 上下标：真实上移/下移小字（BaselineShift），非 Unicode 替换
 * - 变量斜体、函数名正体，符合数学排版规范
 * - \mathbb{Z} → ℤ 等黑板粗体映射
 */

private val SYMBOLS = mapOf(
    "\\pi" to "π", "\\theta" to "θ", "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ",
    "\\delta" to "δ", "\\epsilon" to "ε", "\\varepsilon" to "ε", "\\lambda" to "λ", "\\mu" to "μ",
    "\\rho" to "ρ", "\\sigma" to "σ", "\\omega" to "ω", "\\phi" to "φ", "\\varphi" to "φ",
    "\\eta" to "η", "\\xi" to "ξ", "\\kappa" to "κ", "\\tau" to "τ", "\\upsilon" to "υ",
    "\\chi" to "χ", "\\psi" to "ψ", "\\zeta" to "ζ", "\\iota" to "ι", "\\nu" to "ν",
    "\\infty" to "∞", "\\times" to "×", "\\div" to "÷", "\\pm" to "±", "\\mp" to "∓",
    "\\cdot" to "·", "\\leq" to "≤", "\\le" to "≤", "\\geq" to "≥", "\\ge" to "≥",
    "\\neq" to "≠", "\\ne" to "≠", "\\approx" to "≈", "\\equiv" to "≡", "\\sim" to "∼",
    "\\propto" to "∝", "\\in" to "∈", "\\notin" to "∉",
    "\\subset" to "⊂", "\\supset" to "⊃", "\\subseteq" to "⊆", "\\cup" to "∪", "\\cap" to "∩",
    "\\emptyset" to "∅", "\\forall" to "∀", "\\exists" to "∃", "\\neg" to "¬",
    "\\rightarrow" to "→", "\\to" to "→", "\\leftarrow" to "←", "\\Rightarrow" to "⇒",
    "\\Leftrightarrow" to "⇔", "\\leftrightarrow" to "↔", "\\mapsto" to "↦",
    "\\degree" to "°", "\\circ" to "°", "\\prime" to "′",
    "\\sin" to "sin", "\\cos" to "cos", "\\tan" to "tan", "\\cot" to "cot",
    "\\sec" to "sec", "\\csc" to "csc", "\\log" to "log", "\\ln" to "ln",
    "\\lg" to "lg", "\\lim" to "lim", "\\max" to "max", "\\min" to "min",
    "\\sum" to "∑", "\\prod" to "∏", "\\int" to "∫", "\\partial" to "∂",
    "\\nabla" to "∇", "\\surd" to "√", "\\angle" to "∠", "\\perp" to "⊥",
    "\\parallel" to "∥", "\\triangle" to "△", "\\because" to "∵", "\\therefore" to "∴",
    "\\quad" to " ", "\\qquad" to "  ", "\\," to " ", "\\;" to " ", "\\!" to "", "\\ " to " ",
    "\\left" to "", "\\right" to "", "\\big" to "", "\\Big" to "", "\\bigg" to "", "\\Bigg" to "",
    "\\ldots" to "…", "\\dots" to "…", "\\cdots" to "⋯", "\\vdots" to "⋮", "\\ddots" to "⋱",
    "\\mathrm" to "", "\\mathbf" to "", "\\mathit" to "", "\\text" to "",
    "\\overset" to "", "\\underset" to "", "\\operatorname" to ""
)

/** 黑板粗体字母 → Unicode 双线字母 */
private val MATHBB = mapOf(
    "N" to "ℕ", "Z" to "ℤ", "Q" to "ℚ", "R" to "ℝ", "C" to "ℂ",
    "H" to "ℍ", "P" to "ℙ", "E" to "𝔼"
)

/** 上标字符（无法映射时退回真实上移排版） */
private val SUPERS = mapOf(
    '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
    '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
    '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾', 'n' to 'ⁿ'
)
private val SUBS = mapOf(
    '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
    '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
    '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎'
)

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

/* ---------------- 纯文本渲染（PDF 等） ---------------- */

fun renderLatex(src: String): String {
    val r = Reader(src.trim())
    val sb = StringBuilder()
    while (r.i < r.s.length) {
        val c = r.s[r.i]
        when {
            c == '\\' -> {
                val name = r.readCmd()
                when (name) {
                    "\\frac", "\\dfrac", "\\tfrac" -> {
                        val num = renderLatex(r.readArg())
                        val den = renderLatex(r.readArg())
                        if (num.length <= 2 && den.length <= 2) sb.append("$num⁄$den")
                        else sb.append("($num)/($den)")
                    }
                    "\\sqrt" -> {
                        sb.append("√(")
                        sb.append(renderLatex(r.readArg()))
                        sb.append(")")
                    }
                    "\\text", "\\mathrm", "\\mathbf", "\\mathit", "\\operatorname" ->
                        sb.append(renderLatex(r.readArg()))
                    "\\mathbb" -> {
                        val inner = renderLatex(r.readArg()).trim()
                        sb.append(inner.map { MATHBB[it.toString()] ?: it.toString() }.joinToString(""))
                    }
                    "\\mathcal" -> sb.append(renderLatex(r.readArg()))
                    "\\overline" -> {
                        sb.append(renderLatex(r.readArg()))
                        sb.append('̅')
                    }
                    "\\vec" -> {
                        sb.append(renderLatex(r.readArg()))
                        sb.append("⃗")
                    }
                    "\\hat" -> {
                        sb.append(renderLatex(r.readArg()))
                        sb.append('^')
                    }
                    else -> sb.append(SYMBOLS[name] ?: name.removePrefix("\\"))
                }
            }
            c == '^' -> {
                r.i++
                val inner = r.readArg()
                sb.append(inner.map { SUPERS[it] ?: it }.joinToString(""))
            }
            c == '_' -> {
                r.i++
                val inner = r.readArg()
                sb.append(inner.map { SUBS[it] ?: it }.joinToString(""))
            }
            c == '{' || c == '}' -> r.i++
            else -> { sb.append(c); r.i++ }
        }
    }
    return sb.toString()
}

fun renderMixedText(text: String): String {
    if (text.isBlank()) return text
    var out = text
    out = Regex("\\\\\\[(.+?)\\\\\\]", RegexOption.DOT_MATCHES_ALL).replace(out) { renderLatex(it.groupValues[1]) }
    out = Regex("\\\\\\((.+?)\\\\\\)", RegexOption.DOT_MATCHES_ALL).replace(out) { renderLatex(it.groupValues[1]) }
    out = Regex("\\$\\$(.+?)\\$\\$", RegexOption.DOT_MATCHES_ALL).replace(out) { renderLatex(it.groupValues[1]) }
    out = Regex("\\$([^$]+?)\\$").replace(out) { renderLatex(it.groupValues[1]) }
    out = out.replace(Regex("\\\\([a-zA-Z]+)")) { m ->
        SYMBOLS["\\" + m.groupValues[1]] ?: MATHBB[m.groupValues[1]] ?: m.groupValues[1]
    }
    return out
}

fun stripMarkdown(text: String): String {
    var out = text
    out = out.replace("```latex", "").replace("```math", "").replace("```", "")
    out = out.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
    out = out.replace(Regex("__(.+?)__"), "$1")
    out = out.replace(Regex("(^|\\s)\\*([^*]+?)\\*(\\s|$)"), "$1$2$3")
    out = out.replace(Regex("^#{1,4}\\s*", RegexOption.MULTILINE), "")
    return out
}

/* ---------------- 富文本渲染（竖式分数 + 真实上下标 + 斜体变量） ---------------- */

private const val INLINE_ID = "inline"

/** 公式内变量斜体输出 */
private fun AnnotatedString.Builder.appendMathChars(chars: String) {
    for (c in chars) {
        if (c.isLetter() && c.code < 128) {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(c) }
        } else {
            append(c)
        }
    }
}

private fun buildLatexAnnotated(
    latex: String,
    sb: AnnotatedString.Builder,
    inline: MutableMap<String, InlineTextContent>,
    fontSize: TextUnit,
    color: Color,
    idCounter: IntArray
) {
    val r = Reader(latex)
    while (r.i < r.s.length) {
        val c = r.s[r.i]
        when {
            c == '\\' -> {
                val name = r.readCmd()
                when (name) {
                    "\\frac", "\\dfrac", "\\tfrac" -> {
                        val numRaw = r.readArg()
                        val denRaw = r.readArg()
                        val num = renderLatex(numRaw)
                        val den = renderLatex(denRaw)
                        val id = "$INLINE_ID${idCounter[0]++}"
                        val widest = maxOf(num.length, den.length)
                        // 宽度：按字符估算 + 左右余量
                        val w = widest * fontSize.value * 0.62f + fontSize.value * 0.9f
                        // 高度：给足 2.9em，确保分母不被裁剪
                        val h = fontSize.value * 2.9f
                        sb.appendInlineContent(id, "□")
                        val subSize = (fontSize.value * 0.74f).sp
                        val lineH = (fontSize.value * 1.02f).sp
                        inline[id] = InlineTextContent(
                            Placeholder(
                                width = w.sp,
                                height = h.sp,
                                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                            )
                        ) {
                            Column(
                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 2.dp)
                            ) {
                                Text(num, fontSize = subSize, lineHeight = lineH, color = color,
                                    maxLines = 1, textAlign = TextAlign.Center)
                                Box(
                                    Modifier.fillMaxWidth()
                                        .height((fontSize.value * 0.09f).coerceAtLeast(1.1f).dp)
                                        .background(color.copy(alpha = 0.9f))
                                )
                                Text(den, fontSize = subSize, lineHeight = lineH, color = color,
                                    maxLines = 1, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    "\\sqrt" -> {
                        sb.append("√")
                        val inner = r.readArg()
                        // 根号内容顶部加横线（上划线近似）
                        buildLatexAnnotated(inner, sb, inline, fontSize, color, idCounter)
                    }
                    "\\text", "\\mathrm", "\\operatorname" -> {
                        sb.append(renderLatex(r.readArg()))
                    }
                    "\\mathbf" -> {
                        val inner = renderLatex(r.readArg())
                        sb.append(inner)
                    }
                    "\\mathbb" -> {
                        val inner = renderLatex(r.readArg()).trim()
                        sb.append(inner.map { MATHBB[it.toString()] ?: it.toString() }.joinToString(""))
                    }
                    "\\mathcal", "\\mathit" -> {
                        sb.append(renderLatex(r.readArg()))
                    }
                    "\\overline" -> {
                        sb.append(renderLatex(r.readArg()))
                        sb.append('̅')
                    }
                    "\\vec" -> {
                        sb.append(renderLatex(r.readArg()))
                        sb.append("⃗")
                    }
                    "\\hat" -> {
                        sb.append(renderLatex(r.readArg()))
                        sb.append('^')
                    }
                    else -> {
                        val rep = SYMBOLS[name] ?: name.removePrefix("\\")
                        sb.appendMathChars(rep)
                    }
                }
            }
            c == '^' -> {
                r.i++
                val inner = r.readArg()
                if (inner.length <= 3 && inner.all { SUPERS.containsKey(it) }) {
                    sb.append(inner.map { SUPERS[it] }.joinToString(""))
                } else {
                    // 真实上标：小字 + 上移
                    sb.withStyle(
                        SpanStyle(
                            fontSize = (fontSize.value * 0.68f).sp,
                            baselineShift = androidx.compose.ui.text.style.BaselineShift.Superscript
                        )
                    ) { append(renderLatex(inner)) }
                }
            }
            c == '_' -> {
                r.i++
                val inner = r.readArg()
                if (inner.length <= 3 && inner.all { SUBS.containsKey(it) }) {
                    sb.append(inner.map { SUBS[it] }.joinToString(""))
                } else {
                    sb.withStyle(
                        SpanStyle(
                            fontSize = (fontSize.value * 0.68f).sp,
                            baselineShift = androidx.compose.ui.text.style.BaselineShift.Subscript
                        )
                    ) { append(renderLatex(inner)) }
                }
            }
            c == '{' || c == '}' -> r.i++
            else -> {
                sb.appendMathChars(c.toString())
                r.i++
            }
        }
    }
}

private fun buildMixedAnnotated(
    text: String,
    fontSize: TextUnit,
    color: Color
): Pair<AnnotatedString, Map<String, InlineTextContent>> {
    val sb = AnnotatedString.Builder()
    val inline = mutableMapOf<String, InlineTextContent>()
    val idCounter = intArrayOf(0)

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

    var pos = 0
    for (sp in spans) {
        if (sp.start > pos) sb.append(text.substring(pos, sp.start))
        val raw = text.substring(sp.start, sp.end)
        val inner = Regex("\\$\\$|\\\\\\[|\\\\\\]|\\\\\\(|\\\\\\)|\\$").replace(raw, "")
        buildLatexAnnotated(inner, sb, inline, fontSize, color, idCounter)
        pos = sp.end
    }
    if (pos < text.length) {
        val tail = text.substring(pos)
        sb.append(tail.replace(Regex("\\\\([a-zA-Z]+)")) { m ->
            SYMBOLS["\\" + m.groupValues[1]] ?: MATHBB[m.groupValues[1]] ?: m.groupValues[1]
        })
    }
    return sb.toAnnotatedString() to inline
}

/** 带公式渲染的文本组件：竖式分数、真实上下标、变量斜体 */
@Composable
fun MathText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.sp,
    lineHeight: TextUnit = TextUnit.Unspecified,
    color: Color = MaterialTheme.colorScheme.onSurface,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val cleaned = remember(text) { stripMarkdown(text) }
    val (annotated, inline) = remember(cleaned, fontSize.value, color) {
        buildMixedAnnotated(cleaned, fontSize, color)
    }
    Text(
        annotated,
        modifier = modifier,
        inlineContent = inline,
        fontSize = fontSize,
        lineHeight = lineHeight,
        color = color,
        fontFamily = FontFamily.Default,
        maxLines = maxLines,
        overflow = overflow
    )
}
