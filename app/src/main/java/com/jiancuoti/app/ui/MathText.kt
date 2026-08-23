package com.jiancuoti.app.ui

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * 轻量 LaTeX 行内渲染：
 * - 把 $...$ 内的 \frac{a}{b} 渲染为 a/b 上标形式、\sqrt{x} → √(x)、\pi → π 等符号映射
 * - 上下标 ^{} _{} 转换成 Unicode 上下标（可用范围内），其余按符号映射输出
 * - 不追求完全排版级渲染，目标是「可读、不出现反斜杠源码」
 */

private val SYMBOLS = mapOf(
    "\\pi" to "π", "\\theta" to "θ", "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ",
    "\\delta" to "δ", "\\epsilon" to "ε", "\\lambda" to "λ", "\\mu" to "μ", "\\rho" to "ρ",
    "\\sigma" to "σ", "\\omega" to "ω", "\\phi" to "φ", "\\varphi" to "φ", "\\eta" to "η",
    "\\xi" to "ξ", "\\kappa" to "κ", "\\tau" to "τ", "\\upsilon" to "υ", "\\chi" to "χ",
    "\\psi" to "ψ", "\\zeta" to "ζ", "\\iota" to "ι", "\\nu" to "ν", "\\omicron" to "ο",
    "\\infty" to "∞", "\\times" to "×", "\\div" to "÷", "\\pm" to "±", "\\mp" to "∓",
    "\\cdot" to "·", "\\leq" to "≤", "\\geq" to "≥", "\\neq" to "≠", "\\approx" to "≈",
    "\\equiv" to "≡", "\\sim" to "∼", "\\propto" to "∝", "\\in" to "∈", "\\notin" to "∉",
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
    "\\quad" to " ", "\\qquad" to "  ", "\\," to " ", "\\;" to " ", "\\!" to "",
    "\\left" to "", "\\right" to "", "\\big" to "", "\\Big" to "", "\\bigg" to "", "\\Bigg" to "",
    "\\ldots" to "…", "\\dots" to "…", "\\cdots" to "⋯", "\\vdots" to "⋮", "\\ddots" to "⋱",
    "\\overline{a}" to "a̅"
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

/** 把一小段 LaTeX（无 $ 包裹）转成可读 Unicode 文本 */
fun renderLatex(src: String): String {
    val s = src.trim()
    val sb = StringBuilder()
    var i = 0
    fun readBrace(): String {
        // i 指向 '{'，返回内容并移动到 '}'
        if (i >= s.length || s[i] != '{') {
            // 无括号：读单个 token（字母串或单字符）
            if (i < s.length && s[i].isLetter()) {
                var j = i
                while (j < s.length && s[j].isLetter()) j++
                val t = s.substring(i, j); i = j; return t
            }
            if (i < s.length) { val c = s[i]; i++; return c.toString() }
            return ""
        }
        var depth = 0; val start = i + 1
        while (i < s.length) {
            if (s[i] == '{') depth++
            else if (s[i] == '}') { depth--; if (depth == 0) break }
            i++
        }
        val content = s.substring(start, i.coerceAtMost(s.length))
        i++ // skip }
        return content
    }
    while (i < s.length) {
        val c = s[i]
        when {
            c == '\\' -> {
                // 读取命令名
                var j = i + 1
                while (j < s.length && s[j].isLetter()) j++
                val cmd = if (j > i + 1) s.substring(i, j) else "\\" + s[i + 1.coerceAtMost(s.length - 1)].also { if (j <= i + 1) j = i + 2 }
                val name = if (cmd.startsWith("\\")) cmd else "\\$cmd"
                i = j
                when (name) {
                    "\\frac", "\\dfrac", "\\tfrac" -> {
                        val num = renderLatex(readBrace())
                        val den = renderLatex(readBrace())
                        // 分数：简单形式用 ⁄ 显示，长表达式用 (a)/(b)
                        if (num.length <= 2 && den.length <= 2) sb.append("$num⁄$den")
                        else sb.append("($num)/($den)")
                    }
                    "\\sqrt" -> {
                        val inner = readBrace()
                        sb.append("√($inner)")
                    }
                    "\\text", "\\mathrm", "\\mathbf", "\\mathit" -> {
                        sb.append(readBrace())
                    }
                    "\\overline" -> {
                        sb.append(renderLatex(readBrace())).append('̅')
                    }
                    "\\vec" -> sb.append(renderLatex(readBrace())).append("⃗")
                    "\\hat" -> sb.append(renderLatex(readBrace())).append('^')
                    else -> {
                        val rep = SYMBOLS[name] ?: SYMBOLS[cmd] ?: cmd.removePrefix("\\")
                        sb.append(rep)
                    }
                }
            }
            c == '^' -> {
                i++
                val hasBrace = i < s.length && s[i] == '{'
                val inner = if (hasBrace) readBrace() else if (i < s.length) s[i].toString().also { i++ } else ""
                sb.append(inner.map { SUPERS[it] ?: it }.joinToString(""))
            }
            c == '_' -> {
                i++
                val hasBrace = i < s.length && s[i] == '{'
                val inner = if (hasBrace) readBrace() else if (i < s.length) s[i].toString().also { i++ } else ""
                sb.append(inner.map { SUBS[it] ?: it }.joinToString(""))
            }
            else -> { sb.append(c); i++ }
        }
    }
    return sb.toString()
}

/** 混排文本：把整段文字中的 $...$ 和 \\(...\\) 渲染掉 */
fun renderMixedText(text: String): String {
    if (text.isBlank()) return text
    var out = text
    // \( ... \) 与 \[ ... \]
    out = Regex("\\\\\\((.+?)\\\\\\)").replace(out) { renderLatex(it.groupValues[1]) }
    out = Regex("\\\\\\[(.+?)\\\\\\]").replace(out) { renderLatex(it.groupValues[1]) }
    // $$ ... $$ 先处理
    out = Regex("\\$\\$(.+?)\\$\\$").replace(out) { renderLatex(it.groupValues[1]) }
    // $ ... $
    out = Regex("\\$(.+?)\\$").replace(out) { renderLatex(it.groupValues[1]) }
    // 残留的裸命令符号（如文本中直接出现 \pi 没有美元包裹）
    out = out.replace(Regex("\\\\([a-zA-Z]+)")) { m ->
        SYMBOLS["\\" + m.groupValues[1]] ?: m.groupValues[1]
    }
    return out
}

/** 带公式渲染的正文文本（公式部分用斜体区分） */
@Composable
fun MathText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.sp,
    lineHeight: TextUnit = TextUnit.Unspecified,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    maxLines: Int = Int.MAX_VALUE,
    overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip
) {
    val rendered = renderMixedText(text)
    Text(
        rendered,
        modifier = modifier,
        fontSize = fontSize,
        lineHeight = lineHeight,
        color = color,
        fontFamily = FontFamily.Default,
        maxLines = maxLines,
        overflow = overflow
    )
}
