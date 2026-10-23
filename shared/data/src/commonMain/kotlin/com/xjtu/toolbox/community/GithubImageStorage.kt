package com.xjtu.toolbox.community

import com.xjtu.toolbox.platform.secureKeyValueStore
import com.xjtu.toolbox.util.AppJson
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.io.encoding.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/** 图片只写入当前 GitHub 用户明确配置的公开仓库，不修改项目仓库或复用社区只读授权。 */
class GithubImageStorage(
    private val login: () -> String?,
    private val client: OkHttpClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .callTimeout(45, TimeUnit.SECONDS).build(),
    private val apiBase: String = "https://api.github.com/",
) : CommunityImageStorage {
    private fun store() = secureKeyValueStore("community_images_${username().lowercase()}")
    private fun username(): String = login().orEmpty().also {
        require(it.matches(Regex("[A-Za-z0-9-]{1,39}"))) { "请先登录 GitHub" }
    }
    override fun config() = CommunityImageConfig(store().getString("repository").orEmpty(), store().getString("token").orEmpty())
    override fun saveConfig(config: CommunityImageConfig) {
        val repo = config.repository.trim()
        require(repo.isEmpty() || repo.matches(Regex("[A-Za-z0-9_.-]{1,100}")) && repo !in setOf(".", "..")) { "只填写自己仓库的名称，例如 community-images" }
        store().putString("repository", repo)
        store().putString("token", config.token.trim())
    }

    override suspend fun upload(image: CommunityImage): String = withContext(Dispatchers.IO) {
        val owner = username()
        val config = config()
        require(config.repository.isNotEmpty() && config.token.isNotBlank()) { "请先配置自己的图片仓库和访问令牌" }
        require(!(owner.equals(CommunityRepo.OWNER, true) && config.repository.equals(CommunityRepo.NAME, true))) { "请使用独立图片仓库，不能将图片写入应用源码仓库" }
        require(image.bytes.size in 1..2_097_152) { "图片需要小于 2 MB" }
        val extension = when (image.mimeType) { "image/jpeg" -> "jpg"; "image/png" -> "png"; else -> error("只支持 JPEG / PNG 图片") }
        val base = apiBase.toHttpUrl()
        require(base.isHttps || base.host in setOf("localhost", "127.0.0.1", "::1"))
        fun request(path: String, body: JsonObject? = null): JsonObject {
            val req = Request.Builder().url(base.resolve(path) ?: error("GitHub 地址无效"))
                .header("Authorization", "Bearer ${config.token}")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "XJTUToolbox-Community")
                .apply { body?.let { put(it.toString().toRequestBody("application/json".toMediaType())) } }.build()
            return client.newCall(req).execute().use {
                require(it.isSuccessful) { when (it.code) {
                    401, 403 -> "图片上传授权不足：请检查所选仓库的 Contents 读写权限"
                    404 -> "图片仓库不存在或令牌无权访问"
                    409, 422 -> "图片仓库暂不能写入，请检查仓库状态后重试"
                    else -> "图片上传失败（HTTP ${it.code}）"
                } }
                AppJson.parseToJsonElement(it.body.string()).jsonObject
            }
        }
        val viewer = request("user")["login"]?.jsonPrimitive?.content.orEmpty()
        require(viewer.equals(owner, true)) { "图片令牌必须属于当前登录的 GitHub 账号" }
        val repo = request("repos/$owner/${config.repository}")
        require(repo["private"]?.jsonPrimitive?.booleanOrNull == false) { "图片仓库必须公开，私有仓库图片无法在社区显示" }
        require(repo["owner"]?.jsonObject?.get("login")?.jsonPrimitive?.content.equals(owner, true)) { "只能上传到自己的图片仓库" }
        require(repo["permissions"]?.jsonObject?.get("push")?.jsonPrimitive?.booleanOrNull == true) { "当前令牌没有仓库写入权限" }
        require(username().equals(owner, true) && config() == config) { "账号或配置已变更，请重新选择图片" }
        val path = "community/${UUID.randomUUID()}.$extension"
        val result = request("repos/$owner/${config.repository}/contents/$path", buildJsonObject {
            put("message", "Upload community image")
            put("content", Base64.encode(image.bytes))
        })
        val sha = result["commit"]?.jsonObject?.get("sha")?.jsonPrimitive?.content.orEmpty()
        require(sha.matches(Regex("[0-9a-fA-F]{40,64}"))) { "上传结果缺少提交标识" }
        "https://raw.githubusercontent.com/$owner/${config.repository}/$sha/$path"
    }
}
