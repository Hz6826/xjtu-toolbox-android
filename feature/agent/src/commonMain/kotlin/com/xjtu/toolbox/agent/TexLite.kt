package com.xjtu.toolbox.agent

/**
 * 把模型写出来的简单 TeX 公式转成能直接显示的 Unicode 文字。
 *
 * 不做真正的排版（没有分数线、根号盖线），只求「读得懂」：`\frac{a}{b}` → a/b、
 * `x^2` → x²、`\sum` → Σ、`\le` → ≤。屁岱的公式基本是 GPA、均分这类算式，这个程度够用；
 * 引一个完整的 LaTeX 渲染库（JLaTeXMath / KaTeX WebView）换来的是几 MB 体积和一套 WebView，不值。
 * 认不出来的命令原样留着，至少不吞内容。
 */
object TexLite {

    fun toUnicode(tex: String): String {
        var s = tex.trim()
        // 从里往外一层层消掉花括号结构，最多十轮，防止怪输入死循环
        repeat(10) {
            val before = s
            s = textCmdRe.replace(s) { it.groupValues[2] }
            s = mathbbRe.replace(s) { m -> m.groupValues[1].map { BLACKBOARD[it] ?: it }.joinToString("") }
            s = accentRe.replace(s) { m -> accent(m.groupValues[1], m.groupValues[2].ifEmpty { m.groupValues[3] }) }
            s = rootRe.replace(s) { m -> script(m.groupValues[1], SUP, "") + "√" + wrap(m.groupValues[2]) }
            s = binomRe.replace(s) { m -> "C(${m.groupValues[1].trim()},${m.groupValues[2].trim()})" }
            // 根号下多于一个字符一律加括号：√2x 会被读成「√2 乘 x」
            s = sqrtRe.replace(s) { m -> m.groupValues[1].trim().let { if (it.length <= 1) "√$it" else "√($it)" } }
            s = fracRe.replace(s) { m -> wrap(m.groupValues[1]) + "/" + wrap(m.groupValues[2]) }
            s = supGroupRe.replace(s) { m -> script(m.groupValues[1], SUP, "^") }
            s = subGroupRe.replace(s) { m -> script(m.groupValues[1], SUB, "_") }
            if (s == before) return@repeat
        }
        s = s.replace("^\\circ", "°").replace("^{\\circ}", "°")
        s = funcRe.replace(s) { m -> m.groupValues[1] }
        SYMBOLS.forEach { (k, v) -> s = s.replace(Regex(Regex.escape(k) + "(?![A-Za-z])"), Regex.escapeReplacement(v)) }
        s = supCharRe.replace(s) { m -> script(m.groupValues[1], SUP, "^") }
        s = subCharRe.replace(s) { m -> script(m.groupValues[1], SUB, "_") }
        s = s.replace("\\{", "{").replace("\\}", "}").replace("{", "").replace("}", "")
        return s.replace(Regex(" {2,}"), " ").trim()
    }

    /** 单个记号不加括号，复合式加括号：a/b，(a+b)/(c+d)。 */
    private fun wrap(x: String): String {
        val t = x.trim()
        return if (t.length <= 1 || t.all { it.isLetterOrDigit() || it == '.' }) t else "($t)"
    }

    /** 能全部换成上 / 下标字符就换，换不了就老实写成 ^(…) / _(…)。 */
    private fun script(x: String, table: Map<Char, Char>, mark: String): String {
        val t = x.trim()
        return if (t.isNotEmpty() && t.all { it in table }) t.map { table.getValue(it) }.joinToString("")
        else "$mark(${t})"
    }

    /** 字母上的记号用 Unicode 组合字符：每个字母后面跟一个（箭头只跟在最后一个字母后）。 */
    private fun accent(cmd: String, body: String): String {
        val t = body.trim()
        if (t.isEmpty()) return ""
        val mark = when (cmd) {
            "vec", "vect", "overrightarrow" -> return t + "\u20D7"
            "hat", "widehat" -> '\u0302'
            "bar", "overline" -> '\u0305'
            "dot" -> '\u0307'
            "ddot" -> '\u0308'
            "tilde", "widetilde" -> '\u0303'
            "underline" -> '\u0332'
            else -> return t
        }
        return t.map { "$it$mark" }.joinToString("")
    }

    // 命令名后面不许紧跟字母：不然 \vect{v} 会被认成 \vec 加单字母 t
    private val accentRe = Regex("""\\(vec|vect|overrightarrow|hat|widehat|bar|overline|dot|ddot|tilde|widetilde|underline)(?![A-Za-z])(?:\{([^{}]*)\}|\s*([A-Za-z0-9]))""")
    private val mathbbRe = Regex("""\\mathbb\{([^{}]*)\}""")
    private val rootRe = Regex("""\\sqrt\[([^\]]*)\]\{([^{}]*)\}""")
    private val binomRe = Regex("""\\binom\{([^{}]*)\}\{([^{}]*)\}""")
    /** 函数名去掉反斜杠照写：\sin x → sin x。 */
    private val funcRe = Regex("""\\(sin|cos|tan|cot|sec|csc|arcsin|arccos|arctan|sinh|cosh|tanh|log|ln|lg|exp|lim|max|min|sup|inf|det|dim|ker|deg|gcd|arg|Pr)(?![A-Za-z])""")
    private val BLACKBOARD = mapOf('N' to 'ℕ', 'Z' to 'ℤ', 'Q' to 'ℚ', 'R' to 'ℝ', 'C' to 'ℂ', 'P' to 'ℙ')

    private val textCmdRe = Regex("""\\(text|mathrm|mathbf|mathit|operatorname|textbf|boldsymbol)\{([^{}]*)\}""")
    private val sqrtRe = Regex("""\\sqrt\{([^{}]*)\}""")
    private val fracRe = Regex("""\\[dt]?frac\{([^{}]*)\}\{([^{}]*)\}""")
    private val supGroupRe = Regex("""\^\{([^{}]*)\}""")
    private val subGroupRe = Regex("""_\{([^{}]*)\}""")
    // 单字符上下标不认括号：换不了上标时退回的 ^(…) 已经带括号，不能再被转一次
    private val supCharRe = Regex("""\^([A-Za-z0-9+\-=])""")
    private val subCharRe = Regex("""_([A-Za-z0-9+\-=])""")

    private val SUP = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴', '5' to '⁵', '6' to '⁶', '7' to '⁷',
        '8' to '⁸', '9' to '⁹', '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'n' to 'ⁿ', 'i' to 'ⁱ', 'x' to 'ˣ', 'y' to 'ʸ', 'k' to 'ᵏ', 'T' to 'ᵀ',
    )
    private val SUB = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄', '5' to '₅', '6' to '₆', '7' to '₇',
        '8' to '₈', '9' to '₉', '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
        'a' to 'ₐ', 'e' to 'ₑ', 'h' to 'ₕ', 'i' to 'ᵢ', 'j' to 'ⱼ', 'k' to 'ₖ', 'l' to 'ₗ', 'm' to 'ₘ',
        'n' to 'ₙ', 'o' to 'ₒ', 'p' to 'ₚ', 'r' to 'ᵣ', 's' to 'ₛ', 't' to 'ₜ', 'u' to 'ᵤ', 'v' to 'ᵥ', 'x' to 'ₓ',
    )

    /** 长的放前面：\leq 要先于 \le 匹配（其实有后行断言兜着，这里再保险一层）。 */
    private val SYMBOLS = listOf(
        "\\left" to "", "\\right" to "", "\\displaystyle" to "",
        "\\quad" to "  ", "\\qquad" to "  ", "\\," to " ", "\\;" to " ", "\\:" to " ", "\\!" to "",
        "\\times" to "×", "\\cdot" to "·", "\\div" to "÷", "\\pm" to "±", "\\mp" to "∓",
        "\\leq" to "≤", "\\le" to "≤", "\\geq" to "≥", "\\ge" to "≥", "\\neq" to "≠", "\\ne" to "≠",
        "\\approx" to "≈", "\\equiv" to "≡", "\\sim" to "∼", "\\propto" to "∝",
        "\\Rightarrow" to "⇒", "\\Leftarrow" to "⇐", "\\Leftrightarrow" to "⇔",
        "\\rightarrow" to "→", "\\leftarrow" to "←", "\\to" to "→", "\\mapsto" to "↦",
        "\\sum" to "Σ", "\\prod" to "Π", "\\int" to "∫", "\\oint" to "∮", "\\infty" to "∞",
        "\\partial" to "∂", "\\nabla" to "∇", "\\forall" to "∀", "\\exists" to "∃",
        "\\in" to "∈", "\\notin" to "∉", "\\subseteq" to "⊆", "\\subset" to "⊂", "\\cup" to "∪", "\\cap" to "∩",
        "\\emptyset" to "∅", "\\land" to "∧", "\\lor" to "∨", "\\neg" to "¬",
        "\\ldots" to "…", "\\cdots" to "⋯", "\\dots" to "…", "\\degree" to "°", "\\circ" to "∘", "\\%" to "%",
        "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ", "\\epsilon" to "ε",
        "\\varepsilon" to "ε", "\\zeta" to "ζ", "\\eta" to "η", "\\theta" to "θ", "\\lambda" to "λ",
        "\\mu" to "μ", "\\nu" to "ν", "\\xi" to "ξ", "\\pi" to "π", "\\rho" to "ρ", "\\sigma" to "σ",
        "\\tau" to "τ", "\\phi" to "φ", "\\varphi" to "φ", "\\chi" to "χ", "\\psi" to "ψ", "\\omega" to "ω",
        "\\Gamma" to "Γ", "\\Delta" to "Δ", "\\Theta" to "Θ", "\\Lambda" to "Λ", "\\Pi" to "Π",
        "\\Sigma" to "Σ", "\\Phi" to "Φ", "\\Psi" to "Ψ", "\\Omega" to "Ω",
        "\\parallel" to "∥", "\\perp" to "⊥", "\\angle" to "∠", "\\triangle" to "△", "\\cong" to "≅", "\\simeq" to "≃",
        "\\ll" to "≪", "\\gg" to "≫", "\\leqslant" to "≤", "\\geqslant" to "≥", "\\langle" to "⟨", "\\rangle" to "⟩",
        "\\|" to "‖", "\\lVert" to "‖", "\\rVert" to "‖", "\\mid" to "|", "\\vert" to "|",
        "\\because" to "∵", "\\therefore" to "∴", "\\oplus" to "⊕", "\\otimes" to "⊗", "\\star" to "⋆",
        "\\uparrow" to "↑", "\\downarrow" to "↓", "\\leftrightarrow" to "↔", "\\implies" to "⇒", "\\iff" to "⇔",
        "\\vdots" to "⋮", "\\ddots" to "⋱", "\\prime" to "′", "\\hbar" to "ℏ", "\\ell" to "ℓ", "\\top" to "⊤", "\\bot" to "⊥",
        "\\supseteq" to "⊇", "\\supset" to "⊃", "\\setminus" to "∖", "\\iint" to "∬", "\\iiint" to "∭",
    ).sortedByDescending { it.first.length }
}
