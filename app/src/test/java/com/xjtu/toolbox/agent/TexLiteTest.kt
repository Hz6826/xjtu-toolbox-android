package com.xjtu.toolbox.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class TexLiteTest {

    @Test
    fun `GPA 算式转成可读文字`() {
        assertEquals(
            // 分子分母是复合式就各加一层括号：多一层括号不影响读，少了会读错
            "GPA = (Σ(绩点×学分))/(Σ学分)",
            TexLite.toUnicode("""\text{GPA} = \frac{\sum(\text{绩点}\times\text{学分})}{\sum\text{学分}}"""),
        )
    }

    @Test
    fun `上下标与常见符号`() {
        assertEquals("x² + y₁ ≤ 3", TexLite.toUnicode("""x^2 + y_1 \le 3"""))
        assertEquals("a⁽ⁿ⁺¹⁾", TexLite.toUnicode("""a^{(n+1)}"""))
        assertEquals("√(2x)", TexLite.toUnicode("""\sqrt{2x}"""))
        assertEquals("90°", TexLite.toUnicode("""90^\circ"""))
    }

    @Test
    fun `换不了上标的字符老实写成括号`() {
        assertEquals("e^(-πt)", TexLite.toUnicode("""e^{-\pi t}""").replace(" ", ""))
    }

    @Test
    fun `认不出的命令原样保留不吞内容`() {
        assertEquals("\\foo x", TexLite.toUnicode("""\foo x"""))
    }

    @Test
    fun `前缀相同的命令不串`() {
        // \in 不能把 \infty 吃成「∈fty」
        assertEquals("x ∈ ∞", TexLite.toUnicode("""x \in \infty"""))
    }

    @Test
    fun `字母上的记号带不带花括号都认`() {
        assertEquals("a⃗ · b⃗", TexLite.toUnicode("""\vec{a} \cdot \vec b"""))
        assertEquals("AB⃗", TexLite.toUnicode("""\overrightarrow{AB}"""))
        assertEquals("x̂", TexLite.toUnicode("""\hat{x}"""))
        assertEquals("x̅", TexLite.toUnicode("""\bar x"""))
        assertEquals("v⃗", TexLite.toUnicode("""\vect{v}"""))
    }

    @Test
    fun `函数名、数集、根次、组合数`() {
        assertEquals("sin x + ln y", TexLite.toUnicode("""\sin x + \ln y"""))
        assertEquals("x ∈ ℝ", TexLite.toUnicode("""x \in \mathbb{R}"""))
        assertEquals("³√8", TexLite.toUnicode("""\sqrt[3]{8}"""))
        assertEquals("C(n,k)", TexLite.toUnicode("""\binom{n}{k}"""))
        assertEquals("AB ∥ CD", TexLite.toUnicode("""AB \parallel CD"""))
    }
}
