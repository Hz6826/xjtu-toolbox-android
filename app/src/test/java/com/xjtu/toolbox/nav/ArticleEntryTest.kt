package com.xjtu.toolbox.nav

import android.content.Intent
import org.junit.Assert.*
import org.junit.Test

class ArticleEntryTest {
    @Test fun browserEntryStartsANewReadingRequest() {
        val target = DeepLinkRouter.articleTarget(DeepLinkRouter.PIDAI_LINK_ALIAS, Intent.ACTION_VIEW,
            "https://mp.weixin.qq.com/s?__biz=abc&sn=xyz")!!
        assertEquals(AppRoute.Agent, target.route)
        assertTrue(target.autoSend)
        assertEquals("请阅读这篇文章，概括主要内容和要点：\nhttps://mp.weixin.qq.com/s?__biz=abc&sn=xyz", target.prompt)
    }

    @Test fun sharedTextAndClipLinksUseTheSameReadingEntry() {
        val target = DeepLinkRouter.articleTarget(DeepLinkRouter.PIDAI_LINK_ALIAS, Intent.ACTION_SEND, null,
            listOf("分享一篇好文章", "https://example.com/article"))!!
        assertTrue(target.autoSend)
        assertTrue(target.prompt!!.endsWith("https://example.com/article"))
    }

    @Test fun ordinaryDeepLinksAndOtherActionsDoNotAutoSend() {
        assertFalse(DeepLinkRouter.DeepLinkTarget(AppRoute.Agent, "hello").autoSend)
        assertNull(DeepLinkRouter.articleTarget("com.xjtu.toolbox.MainActivity", Intent.ACTION_VIEW, "https://example.com/"))
        assertNull(DeepLinkRouter.articleTarget(DeepLinkRouter.PIDAI_LINK_ALIAS, Intent.ACTION_EDIT, "https://example.com/"))
        assertNull(DeepLinkRouter.articleTarget(DeepLinkRouter.PIDAI_LINK_ALIAS, Intent.ACTION_VIEW, "file:///a.html"))
        assertNull(DeepLinkRouter.articleTarget(DeepLinkRouter.PIDAI_LINK_ALIAS, Intent.ACTION_SEND, null, listOf("只有标题")))
    }
}
