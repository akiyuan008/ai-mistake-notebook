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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 公式渲染：
 * - MathText 组合函数：$...$ 内的 \frac 渲染为真正的竖式分数（上下排版 + 分数线），
 *   与印刷卷面一致；\sqrt、上下标、希腊字母等按符号映射输出
 * - renderMixedText：纯文本版本（供 PDF 绘制等场景）
 * - stripMarkdown：清理 AI 回复中的 markdown 记号
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

private val SUPERS = mapOf(
    '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
    '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
    '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
    'n' to 'ⁿ', 'i' to 'ⁱ', 'a' to 'ᵃ', 'b' to 'ᵇ', 'c' to 'ᶜ', 'd' to 'ᵈ',
    'e' to 'ᵉ', 'f' to 'ᶠ', 'g' to 'ᵍ', 'h' to 'ʰ', 'j' to 'ʲ', 'k' to 'ᵏ',
    'l' to 'ˡ', 'm' to 'ᵐ', 'o' to 'ᵒ', 'p' to 'ᵖ', 'r' to 'ʳ', 's' to 'ˢ',
    't' to 'ᵗ', 'u' to 'ᵘ', 'v' to 'ᵛ', 'w' to 'ʷ', 'x' to 'ˣ', 'y' to 'ʸ', 'z' to 'ᶻ'
)
private val SUBS = mapOf(
    '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
    '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
    '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
    'a' to 'ₐ', 'e' to 'ₑ', 'h' to 'ₕ', 'i' to 'ᵢ', 'j' to 'ⱼ', 'k' to 'ₖ',
    'l' to 'ₗ', 'm' to 'ₘ', 'n' to 'ₙ', 'o' to 'ₒ', 'p' to 'ₚ', 'r' to 'ᵣ',
    's' to 'ₛ', 't' to 'ₜ', 'u' to 'ᵤ', 'v' to 'ᵥ', 'x' to 'ₓ'
)

/* ---------------- LaTeX 分词读取工具 ---------------- */

private class Reader(val s: String) {
    var i = 0
    /** 读取一个参数：{...} 整体或单个 token */
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
            i++ // 跳过 }
            return content
        }
        if (s[i] == '\\') {
            // 反斜杠命令作为整体
            var j = i + 1
            while (j < s.length && s[j].isLetter()) j++
            if (j == i + 1 && j < s.length) j++ // \<符号>
            val t = s.substring(i, j.coerceAtMost(s.length))
            i = j
            return t
        }
        return s[i++].toString()
    }
    /** 读取命令名（调用时 s[i] == '\\'） */
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

/** 把一小段 LaTeX（无 $ 包裹）转成可读 Unicode 文本 */
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
                    "\\sqrt" -> sb.append("√(").append(renderLatex(r.readArg())).append(")")
                    "\\text", "\\mathrm", "\\mathbf", "\\mathit", "\\operatorname" ->
                        sb.append(renderLatex(r.readArg()))
                    "\\overline" -> sb.append(renderLatex(r.readArg())).append('̅')
                    "\\vec" -> sb.append(renderLatex(r.readArg())).append("⃗")
                    "\\hat" -> sb.append(renderLatex(r.readArg())).append('^')
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

/** 混排文本：把整段中的 $$...$$ / $...$ / \(...\) / \[...\] 渲染掉（纯文本版） */
fun renderMixedText(text: String): String {
    if (text.isBlank()) return text
    var out = text
    out = Regex("\\\\\\[(.+?)\\\\\\]", RegexOption.DOT_MATCHES_ALL).replace(out) { renderLatex(it.groupValues[1]) }
    out = Regex("\\\\\\((.+?)\\\\\\)", RegexOption.DOT_MATCHES_ALL).replace(out) { renderLatex(it.groupValues[1]) }
    out = Regex("\\$\\$(.+?)\\$\\$", RegexOption.DOT_MATCHES_ALL).replace(out) { renderLatex(it.groupValues[1]) }
    out = Regex("\\$([^$]+?)\\$").replace(out) { renderLatex(it.groupValues[1]) }
    out = out.replace(Regex("\\\\([a-zA-Z]+)")) { m ->
        SYMBOLS["\\" + m.groupValues[1]] ?: m.groupValues[1]
    }
    return out
}

/** 清理 AI 回复的 markdown 记号，保留公式 */
fun stripMarkdown(text: String): String {
    var out = text
    out = out.replace("```latex", "").replace("```math", "").replace("```", "")
    out = out.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
    out = out.replace(Regex("__(.+?)__"), "$1")
    out = out.replace(Regex("(^|\\s)\\*([^*]+?)\\*(\\s|$)"), "$1$2$3")
    out = out.replace(Regex("^#{1,4}\\s*", RegexOption.MULTILINE), "")
    return out
}

/* ---------------- 富文本渲染（竖式分数） ---------------- */

private const val FRAC_ID = "frac"

/** 构建带竖式分数的 AnnotatedString。latex 为无 $ 包裹的公式段 */
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
                        val id = "$FRAC_ID${idCounter[0]++}"
                        val widest = maxOf(num.length, den.length)
                        val charW = fontSize.value * 0.58f
                        val w = (widest * charW + 4f).coerceAtLeast(fontSize.value * 1.1f)
                        sb.appendInlineContent(id, "​")
                        inline[id] = InlineTextContent(
                            Placeholder(
                                width = w.sp,
                                height = (fontSize.value * 2.15f).sp,
                                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                            )
                        ) {
                            Column(
                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 1.dp)
                            ) {
                                Text(num, fontSize = (fontSize.value * 0.78f).sp, color = color,
                                    maxLines = 1, textAlign = TextAlign.Center,
                                    fontFamily = FontFamily.Default)
                                Box(
                                    Modifier.fillMaxWidth()
                                        .height((fontSize.value * 0.09f).coerceAtLeast(1f).dp)
                                        .background(color.copy(alpha = 0.85f))
                                )
                                Text(den, fontSize = (fontSize.value * 0.78f).sp, color = color,
                                    maxLines = 1, textAlign = TextAlign.Center,
                                    fontFamily = FontFamily.Default)
                            }
                        }
                    }
                    "\\sqrt" -> {
                        sb.append("√(")
                        sb.append(renderLatex(r.readArg()))
                        sb.append(")")
                    }
                    "\\text", "\\mathrm", "\\mathbf", "\\mathit", "\\operatorname" ->
                        sb.append(renderLatex(r.readArg()))
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
}

/** 混排富文本：普通文字原样，公式段渲染（含竖式分数） */
private fun buildMixedAnnotated(
    text: String,
    fontSize: TextUnit,
    color: Color
): Pair<AnnotatedString, Map<String, InlineTextContent>> {
    val sb = AnnotatedString.Builder()
    val inline = mutableMapOf<String, InlineTextContent>()
    val idCounter = intArrayOf(0)

    // 找出所有公式区间，按优先级：$$ $$、\[\]、\(\)、$ $
    data class Span(val start: Int, val end: Int, val content: Int) // content: 公式内容起止
    val spans = mutableListOf<Span>()
    fun collect(pattern: Regex) {
        pattern.findAll(text).forEach { m ->
            val s = m.range.first; val e = m.range.last + 1
            if (spans.none { s < it.end && e > it.start }) {
                spans.add(Span(s, e, m.groups[1]!!.range.first).let {
                    Span(s, e, 0)
                })
            }
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
        // 尾巴里残留的裸命令做符号映射
        sb.append(tail.replace(Regex("\\\\([a-zA-Z]+)")) { m ->
            SYMBOLS["\\" + m.groupValues[1]] ?: m.groupValues[1]
        })
    }
    return sb.toAnnotatedString() to inline
}

/** 带公式渲染的文本组件：分数竖式排版，其余符号映射 */
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
