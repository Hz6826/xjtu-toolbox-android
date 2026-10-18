package com.xjtu.toolbox.agent

import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InsightJsonTest {

    @Test
    fun `容忍代码围栏和前后多余的话`() {
        val reply = "好的，结果如下：\n```json\n{\"summary\":\"今天挺空\",\"picks\":[]}\n```\n有问题再问我"
        assertEquals("今天挺空", parseInsightJson(reply)!!["summary"]!!.jsonPrimitive.content)
    }

    @Test
    fun `没有 JSON 或 JSON 坏了返回 null`() {
        assertNull(parseInsightJson("抱歉，这次没查到"))
        assertNull(parseInsightJson("{\"summary\": "))
    }
}
