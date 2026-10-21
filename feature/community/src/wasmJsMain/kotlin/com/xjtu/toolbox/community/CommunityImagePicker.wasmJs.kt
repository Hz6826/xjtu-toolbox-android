package com.xjtu.toolbox.community

import androidx.compose.runtime.Composable

@Composable
internal actual fun rememberCommunityImagePicker(onPicked: (Result<CommunityImage>) -> Unit): (() -> Unit)? = null
