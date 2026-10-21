package com.xjtu.toolbox.community

import com.sun.net.httpserver.HttpServer
import com.xjtu.toolbox.platform.dataRootOverride
import com.xjtu.toolbox.util.AppJson
import java.net.InetSocketAddress
import java.nio.file.Files
import kotlin.io.encoding.Base64
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class GithubImageStorageTest {
    @Test
    fun uploadUsesOwnedPublicRepoAndImmutableCommitUrl() = runBlocking {
        dataRootOverride = Files.createTempDirectory("community-image-test").toFile()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var privateRepo = false
        var viewer = "alice"
        var writes = 0
        var owner = "alice"
        var imageContent = ""
        var path = ""
        server.createContext("/") { e ->
            assertEquals("Bearer image-test-token", e.requestHeaders.getFirst("Authorization"))
            val response = when {
                e.requestURI.path == "/user" -> """{"login":"$viewer"}"""
                e.requestMethod == "GET" -> """{"private":$privateRepo,"owner":{"login":"$owner"},"permissions":{"push":true}}"""
                else -> {
                    assertTrue(e.requestURI.path.startsWith("/repos/alice/images/contents/community/"))
                    writes++
                    path = e.requestURI.path.substringAfter("contents/")
                    imageContent = AppJson.parseToJsonElement(e.requestBody.readBytes().decodeToString()).jsonObject["content"]!!.jsonPrimitive.content
                    """{"commit":{"sha":"${"a".repeat(40)}"}}"""
                }
            }.toByteArray()
            e.sendResponseHeaders(200, response.size.toLong()); e.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            val storage = GithubImageStorage(login = { "alice" }, apiBase = "http://127.0.0.1:${server.address.port}/")
            storage.saveConfig(CommunityImageConfig("images", "image-test-token"))
            val image = CommunityImage(byteArrayOf(1, 2, 3), "image/png")
            val url = storage.upload(image)
            assertEquals("https://raw.githubusercontent.com/alice/images/${"a".repeat(40)}/$path", url)
            assertContentEquals(image.bytes, Base64.decode(imageContent))
            assertEquals(1, writes)
            privateRepo = true
            assertFailsWith<IllegalArgumentException> { storage.upload(image) }
            privateRepo = false; viewer = "bob"
            assertFailsWith<IllegalArgumentException> { storage.upload(image) }
            viewer = "alice"; owner = "bob"
            assertFailsWith<IllegalArgumentException> { storage.upload(image) }
            assertEquals(1, writes, "私有仓库、不同令牌账号或非本人仓库均不能写入")
            val other = GithubImageStorage(login = { "bob" })
            assertEquals(CommunityImageConfig(), other.config(), "不同 GitHub 用户的图片授权隔离")
            storage.saveConfig(CommunityImageConfig())
            assertEquals(CommunityImageConfig(), storage.config())
        } finally { server.stop(0); dataRootOverride = null }
    }
}
