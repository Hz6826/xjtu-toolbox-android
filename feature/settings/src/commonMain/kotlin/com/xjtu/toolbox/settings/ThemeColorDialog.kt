package com.xjtu.toolbox.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xjtu.toolbox.schedule.CourseColors
import com.xjtu.toolbox.ui.theme.XJTUToolBoxTheme
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun ThemeColorDialog(initialHex: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var color by remember { mutableStateOf(CourseColors.parseHex(initialHex) ?: Color(0xFF3482FF)) }
    var hex by remember { mutableStateOf(CourseColors.toHex(color)) }
    val valid = CourseColors.parseHex(hex) != null
    WindowDialog(show = true, title = "自定义主题色", summary = "选择种子色，自动生成适合浅色与深色的配色", onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ColorPalette(color = color, onColorChanged = {
                color = it.copy(alpha = 1f)
                hex = CourseColors.toHex(color)
            })
            TextField(value = hex, onValueChange = {
                hex = it
                CourseColors.parseHex(it)?.let { parsed -> color = parsed }
            }, label = "Hex 色值（#RRGGBB）", singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("light" to "浅色", "dark" to "深色").forEach { (mode, label) ->
                    XJTUToolBoxTheme(darkModeOverride = mode, customColorHex = CourseColors.toHex(color), applySystemBars = false) {
                        Column(
                            Modifier.weight(1f).background(MiuixTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(label, color = MiuixTheme.colorScheme.onSurface)
                            Box(Modifier.fillMaxWidth().height(28.dp).background(MiuixTheme.colorScheme.primary, RoundedCornerShape(8.dp)))
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(text = "取消", onClick = onDismiss, modifier = Modifier.weight(1f))
                TextButton(text = "应用", enabled = valid, onClick = { onConfirm(CourseColors.toHex(color)) }, modifier = Modifier.weight(1f))
            }
        }
    }
}
