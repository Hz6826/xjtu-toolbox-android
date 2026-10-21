package com.xjtu.toolbox.community

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.window.WindowDialog

internal val LocalCommunityImageStorage = staticCompositionLocalOf<CommunityImageStorage?> { null }

@Composable
internal fun CommunityImageActions(enabled: Boolean, onInsert: (String) -> Unit, onBusy: (Boolean) -> Unit) {
    val storage = LocalCommunityImageStorage.current
    var show by remember { mutableStateOf(false) }
    var showConfig by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var repo by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var image by remember { mutableStateOf<CommunityImage?>(null) }
    var uploadedLink by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var uploading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentInsert by rememberUpdatedState(onInsert)
    val currentBusy by rememberUpdatedState(onBusy)
    DisposableEffect(Unit) { onDispose { currentBusy(false) } }
    val pick = rememberCommunityImagePicker { result ->
        result.fold(onSuccess = { image = it; uploadedLink = null; error = null; show = true }, onFailure = { error = it.message ?: "读取图片失败"; show = true })
    }
    TextButton(text = if (uploading) "上传中…" else "图片", enabled = enabled && !uploading, onClick = {
        error = null
        storage?.config()?.let { repo = it.repository; token = it.token }
        show = true
    })
    if (show) WindowDialog(
        show = true, title = if (image == null) "插入图片" else "确认公开上传",
        onDismissRequest = { if (!uploading) { show = false; image = null; token = ""; showConfig = false } },
    ) {
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (image != null) {
                Text("这张图片（${image!!.bytes.size / 1024} KB）将上传到你的公开仓库 $repo，所有人都可以访问。图片上传独立于发帖；放弃草稿不会删除仓库里的图片。")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(text = "取消", enabled = !uploading, modifier = Modifier.weight(1f), onClick = { image = null })
                    TextButton(text = if (uploading) "上传中…" else "公开上传并插入", enabled = !uploading, modifier = Modifier.weight(1f), onClick = {
                        val selected = image ?: return@TextButton
                        val uploader = storage ?: return@TextButton
                        uploading = true; currentBusy(true); error = null
                        scope.launch {
                            try {
                                val link = uploadedLink ?: uploader.upload(selected).also { uploadedLink = it }
                                currentInsert(communityImageMarkdown(link))
                                image = null; show = false; token = ""
                            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch (e: Exception) { error = e.message ?: "上传失败，正文已保留" }
                            finally { uploading = false; currentBusy(false) }
                        }
                    })
                }
            } else {
                TextField(value = url, onValueChange = { url = it }, label = "HTTPS 图片地址", singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(text = "插入链接", enabled = url.isNotBlank(), onClick = {
                    try { currentInsert(communityImageMarkdown(url)); show = false; url = ""; token = "" }
                    catch (e: IllegalArgumentException) { error = e.message }
                })
                if (storage != null && pick != null) {
                    TextButton(text = "从本机选择图片", onClick = {
                        if (repo.isBlank() || token.isBlank()) showConfig = true else { error = null; pick() }
                    })
                    TextButton(text = "图片上传设置", onClick = { showConfig = !showConfig })
                    if (showConfig) {
                        Text("使用你自己的公开 GitHub 图片仓库。另建仅授权该仓库 Contents 读写的细粒度令牌；社区登录授权无需更改。令牌只保存在本机安全存储。")
                        TextField(value = repo, onValueChange = { repo = it }, label = "自己的仓库名称", singleLine = true, modifier = Modifier.fillMaxWidth())
                        TextField(value = token, onValueChange = { token = it }, label = "图片仓库令牌", visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(text = "清除授权", modifier = Modifier.weight(1f), onClick = {
                                storage.saveConfig(CommunityImageConfig()); repo = ""; token = ""; error = null
                            })
                            TextButton(text = "保存", modifier = Modifier.weight(1f), onClick = {
                                try { storage.saveConfig(CommunityImageConfig(repo, token)); showConfig = false; error = null }
                                catch (e: IllegalArgumentException) { error = e.message }
                            })
                        }
                    }
                }
                TextButton(text = "关闭", onClick = { show = false; token = ""; showConfig = false })
            }
            error?.let { Text(it) }
        }
    }
}
