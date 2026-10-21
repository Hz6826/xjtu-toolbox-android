package com.xjtu.toolbox.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.xjtu.toolbox.ui.components.LocalPageVisible
import com.xjtu.toolbox.util.nowOnCampus
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDateTime

/** 可见页面每到整分钟重算，回到前台 / 切回 tab 时立即校准；后台和隐藏 tab 不轮询。 */
@Composable
fun rememberCampusNow(): LocalDateTime {
    var now by remember { mutableStateOf(nowOnCampus()) }
    val owner = LocalLifecycleOwner.current
    val visible = LocalPageVisible.current
    LaunchedEffect(owner, visible) {
        if (visible) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = nowOnCampus()
                delay((60 - now.second) * 1_000L)
            }
        }
    }
    return now
}
