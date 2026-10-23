package com.xjtu.toolbox.agent

import com.sun.net.httpserver.HttpServer
import com.xjtu.toolbox.FakeCampusProxy
import com.xjtu.toolbox.FakeUpstreamProxySelector
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** 联网搜索 / 读网页（[DeepSeekWebTools]）。 */
class DeepSeekWebToolsJvmTest {

    private lateinit var server: HttpServer
    private val paths = mutableListOf<String>()
    private val authHeader = AtomicReference<String?>(null)
    private val postedBody = AtomicReference<String?>(null)
    private val hits = AtomicInteger(0)

    /** (HTTP 码, 响应体)。每个用例自己换。 */
    @Volatile
    private var response: Pair<Int, String> = 200 to "{}"

    @BeforeTest
    fun setUp() {
        FakeCampusProxy.installFakeUpstreams()
        FakeUpstreamProxySelector.port = 0
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newCachedThreadPool()
        server.createContext("/v1/tools/") { exchange ->
            hits.incrementAndGet()
            synchronized(paths) { paths += exchange.requestURI.path }
            authHeader.set(exchange.requestHeaders.getFirst("Authorization"))
            postedBody.set(String(exchange.requestBody.readBytes(), StandardCharsets.UTF_8))
            val (code, body) = response
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(code, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.stop(0)
    }

    private fun tools(localFetch: (suspend (String) -> String?)? = null) =
        DeepSeekWebTools(FAKE_KEY, localFetch, baseUrl = "http://127.0.0.1:${server.address.port}")

    @Test
    fun 搜索结果一条一行_带来源日期摘要() = runBlocking {
        response = 200 to """
            {"results":[
              {"rerank_score":2.1,"title":"创新港班车","url":"https://gs.xjtu.edu.cn/1.htm","snippet":"短摘要",
               "summary":"班车  时刻\n调整","content":"","site_name":"研究生院","site_icon":null,
               "date":"2026-09-01T08:00:00Z","authority":3},
              {"title":"无摘要","url":"https://example.com/","snippet":"只有短摘要","summary":"","site_name":null,"date":null}
            ]}
        """.trimIndent()

        val out = tools().execute("web_search", """{"query":"创新港 班车 site:xjtu.edu.cn","limit":"50"}""")

        assertEquals(listOf("/v1/tools/search"), paths)
        assertEquals("Bearer $FAKE_KEY", authHeader.get())
        assertTrue(postedBody.get()!!.contains("\"count\":20"), "limit 要收进 1..20：${postedBody.get()}")
        assertTrue(out.contains("[1] 创新港班车｜https://gs.xjtu.edu.cn/1.htm｜研究生院｜2026-09-01｜班车 时刻 调整"), out)
        assertTrue(out.contains("[2] 无摘要｜https://example.com/｜只有短摘要"), out)
        assertTrue(out.endsWith(ToolReply.EXTERNAL_DATA))
    }

    @Test
    fun 空结果与缺参() = runBlocking {
        response = 200 to """{"results":[]}"""
        assertEquals("result: empty; scope: query: 无", tools().execute("web_search", """{"query":"无"}"""))
        assertEquals(ToolReply.missing("query"), tools().execute("web_search", "{}"))
        assertEquals(1, hits.get(), "缺参不该发请求")
    }

    @Test
    fun 读网页_本地优先_其余走_DeepSeek() = runBlocking {
        response = 200 to """{"title":"校历","content":"# 2026-2027 校历"}"""
        val t = tools(localFetch = { url -> if ("weixin" in url) "本地读的" else null })

        assertEquals("本地读的", t.execute("web_fetch", """{"url":"https://mp.weixin.qq.com/s/a"}"""))
        assertEquals(0, hits.get())

        val out = t.execute("web_fetch", """{"url":"https://dean.xjtu.edu.cn/xl.htm"}""")
        assertEquals(listOf("/v1/tools/fetch"), paths)
        assertTrue(postedBody.get()!!.contains("https://dean.xjtu.edu.cn/xl.htm"))
        assertTrue(out.startsWith("标题：校历\n"), out)
        assertTrue(out.contains("# 2026-2027 校历"))
        assertEquals(ToolReply.badFormat("url", "http(s)://…"), t.execute("web_fetch", """{"url":"file:///etc/passwd"}"""))
    }

    @Test
    fun 错误码译成原因_429_重试一次() = runBlocking {
        response = 402 to """{"error":{"message":"Insufficient Balance","type":"invalid_request_error","param":null,"code":"invalid_request_error"}}"""
        assertEquals(ToolReply.failed("web_search", "insufficient_balance"), tools().execute("web_search", """{"query":"x"}"""))
        assertEquals(1, hits.get(), "402 不该重试")

        response = 429 to "{}"
        assertEquals(ToolReply.failed("web_fetch", "rate_limited"), tools().execute("web_fetch", """{"url":"https://a.com"}"""))
        assertEquals(3, hits.get(), "429 等一下重试一次")

        response = 400 to """{"error":{"message":"query too long"}}"""
        assertEquals(ToolReply.failed("web_search", "query too long"), tools().execute("web_search", """{"query":"y"}"""))
    }

    private companion object {
        const val FAKE_KEY = "sk-fake-deepseek-web"
    }
}
