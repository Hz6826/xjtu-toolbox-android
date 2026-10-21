package com.xjtu.toolbox.community

import androidx.compose.runtime.*
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal actual fun rememberCommunityImagePicker(onPicked: (Result<CommunityImage>) -> Unit): (() -> Unit)? {
    val callback by rememberUpdatedState(onPicked)
    val scope = rememberCoroutineScope()
    return {
        val chooser = JFileChooser().apply { fileFilter = FileNameExtensionFilter("图片（PNG / JPEG）", "png", "jpg", "jpeg") }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) scope.launch {
            val file = chooser.selectedFile
            val result = withContext(Dispatchers.IO) { runCatching {
                require(file.length() in 1..20_971_520) { "原图不能超过 20 MB" }
                ImageIO.createImageInputStream(file).use { input ->
                    val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull() ?: error("无法解码图片")
                    try {
                        require(reader.formatName.lowercase() in setOf("png", "jpeg", "jpg")) { "请选择 PNG 或 JPEG 图片" }
                        reader.input = input
                        val width = reader.getWidth(0); val height = reader.getHeight(0)
                        require(width.toLong() * height <= 40_000_000) { "图片尺寸过大" }
                        val source = reader.read(0)
                        val ratio = minOf(1.0, 2048.0 / maxOf(width, height))
                        val alpha = source.colorModel.hasAlpha()
                        val target = BufferedImage(maxOf(1, (width * ratio).toInt()), maxOf(1, (height * ratio).toInt()), if (alpha) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB)
                        val graphics = target.createGraphics()
                        try {
                            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                            graphics.drawImage(source, 0, 0, target.width, target.height, null)
                        } finally { graphics.dispose(); source.flush() }
                        val output = ByteArrayOutputStream()
                        try { require(ImageIO.write(target, if (alpha) "png" else "jpg", output)) } finally { target.flush() }
                        require(output.size() <= 2_097_152) { "压缩后图片仍超过 2 MB，请先缩小图片" }
                        CommunityImage(output.toByteArray(), if (alpha) "image/png" else "image/jpeg")
                    } finally { reader.dispose() }
                }
            } }
            callback(result)
        }
    }
}
