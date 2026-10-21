package com.xjtu.toolbox.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.xjtu.toolbox.ui.theme.XJTUToolBoxTheme
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.*
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalComposeUiApi::class)
class CustomThemeTest {
    @Test
    fun customSeedsGenerateDistinctReadablePalettesInBothModes() {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait {
            try {
                val palettes = mutableListOf<Color>()
                for (mode in listOf("light", "dark")) {
                    for (hex in listOf("#B73945", "#208A77", "#000000", "#FFFFFF")) {
                        var primary: Color? = null
                        var foreground: Color? = null
                        val scene = ImageComposeScene(320, 140, Density(1f)) {
                            XJTUToolBoxTheme(darkModeOverride = mode, customColorHex = hex, applySystemBars = false) {
                                val colors = MiuixTheme.colorScheme
                                SideEffect { primary = colors.primary; foreground = colors.onPrimary }
                                Column(Modifier.fillMaxSize().background(colors.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("主题预览 · $mode · $hex", color = colors.onSurface)
                                    Box(Modifier.fillMaxWidth().background(colors.primary).padding(12.dp)) {
                                        Text("应用主题色", color = colors.onPrimary)
                                    }
                                }
                            }
                        }
                        try {
                            val image = scene.render(0)
                            val a = assertNotNull(primary).luminance()
                            val b = assertNotNull(foreground).luminance()
                            assertTrue((maxOf(a, b) + .05f) / (minOf(a, b) + .05f) >= 4.5f, "$mode $hex 的按钮文字应可读")
                            if (hex == "#B73945" || hex == "#208A77") palettes += assertNotNull(primary)
                            val out = File("build/reports/theme/${mode}-${hex.drop(1)}.png")
                            out.parentFile.mkdirs()
                            out.writeBytes(assertNotNull(image.encodeToData()).bytes)
                        } finally { scene.close() }
                    }
                }
                assertNotEquals(palettes[0], palettes[1], "选择不同种子色应改变主题")
                assertNotEquals(palettes[0], palettes[2], "深色模式应调整色阶")
            } catch (error: Throwable) { failure = error }
        }
        failure?.let { throw it }
    }
}
