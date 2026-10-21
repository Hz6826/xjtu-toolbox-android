package com.xjtu.toolbox.community

import androidx.compose.runtime.Composable

/** 本地读取并重新编码，移除 EXIF 等元数据。取消不返回错误。 */
@Composable
internal expect fun rememberCommunityImagePicker(onPicked: (Result<CommunityImage>) -> Unit): (() -> Unit)?
