package com.xjtu.toolbox.community

import kotlin.test.*

class CommunityImagesTest {
    @Test
    fun imageLinksAreHttpsAndCannotCloseTheMarkdownDestination() {
        assertEquals("\n![图片](<https://example.org/a(b).png>)\n", communityImageMarkdown(" https://example.org/a(b).png "))
        listOf("https://", "http://example.org/a.png", "javascript:alert(1)", "https://example.org/a>\n[evil](x)", "https://example.org/a b").forEach {
            assertFailsWith<IllegalArgumentException> { communityImageMarkdown(it) }
        }
    }
}
