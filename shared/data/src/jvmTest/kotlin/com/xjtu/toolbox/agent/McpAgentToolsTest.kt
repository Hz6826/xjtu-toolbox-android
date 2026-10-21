package com.xjtu.toolbox.agent

import com.sun.net.httpserver.HttpServer
import com.xjtu.toolbox.util.AppJson
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class McpAgentToolsTest {
    @Test
    fun httpInitializationPaginationSseAndPerCallApproval() = runBlocking {
        val methods = mutableListOf<String>()
        var calls = 0
        var approved = false
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/mcp") { exchange ->
            val input = AppJson.parseToJsonElement(exchange.requestBody.readBytes().decodeToString()).jsonObject
            val method = input["method"]!!.jsonPrimitive.content
            methods += method
            assertEquals("Bearer test-secret", exchange.requestHeaders.getFirst("Authorization"))
            assertTrue(exchange.requestHeaders.getFirst("Accept").contains("text/event-stream"))
            if (method != "initialize") {
                assertEquals("session-test", exchange.requestHeaders.getFirst("Mcp-Session-Id"))
                assertEquals("2025-06-18", exchange.requestHeaders.getFirst("MCP-Protocol-Version"))
            }
            if (method == "notifications/initialized") {
                exchange.sendResponseHeaders(202, -1); exchange.close()
            } else {
                val result = when (method) {
                    "initialize" -> """{"protocolVersion":"2025-06-18","capabilities":{"tools":{}}}"""
                    "tools/list" -> if (input["params"]?.jsonObject?.get("cursor") == null)
                        """{"tools":[{"name":"list_tasks","inputSchema":{"type":"object"}}],"nextCursor":"second"}"""
                        else """{"tools":[{"name":"create_task","inputSchema":{"type":"object"}}]}"""
                    else -> { calls++; """{"content":[{"type":"text","text":"created"}]}""" }
                }
                val response = """{"jsonrpc":"2.0","id":${input["id"]},"result":$result}"""
                val sse = method == "tools/call"
                val body = (if (sse) ": heartbeat\n\ndata: $response\n\n" else response).toByteArray()
                exchange.responseHeaders.add("Content-Type", if (sse) "text/event-stream" else "application/json")
                if (method == "initialize") exchange.responseHeaders.add("Mcp-Session-Id", "session-test")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
        }
        server.start()
        try {
            val config = AgentConfig(mcpEnabled = true, mcpEndpoint = "http://127.0.0.1:${server.address.port}/mcp", mcpToken = "test-secret")
            val tools = McpAgentTools(config, confirm = { endpoint, tool, arguments ->
                assertEquals(config.mcpEndpoint, endpoint)
                assertEquals("create_task", tool)
                assertTrue(arguments.contains("meeting"))
                approved
            })
            assertEquals(2, AppJson.parseToJsonElement(tools.execute("mcp_list_tools", "{}").substringBefore('\n')).jsonArray.size)
            assertEquals(listOf("initialize", "notifications/initialized", "tools/list", "tools/list"), methods)
            val args = """{"name":"create_task","arguments":{"title":"meeting"}}"""
            assertTrue(tools.execute("mcp_call_tool", args).contains("未执行"))
            assertEquals(0, calls)
            approved = true
            assertTrue(tools.execute("mcp_call_tool", args).contains("created"))
            assertEquals(1, calls)
            assertFailsWith<IllegalArgumentException> { tools.execute("mcp_call_tool", """{"name":"unknown","arguments":{}}""") }
            assertEquals(1, calls)
        } finally { server.stop(0) }
    }

    @Test
    fun plainHttpRemoteEndpointsAreRejectedBeforeSendingSecrets() = runBlocking {
        val tools = McpAgentTools(AgentConfig(mcpEndpoint = "http://example.com/mcp", mcpToken = "secret"), confirm = { _, _, _ -> true })
        assertFailsWith<IllegalArgumentException> { tools.testConnection() }
        Unit
    }
}
