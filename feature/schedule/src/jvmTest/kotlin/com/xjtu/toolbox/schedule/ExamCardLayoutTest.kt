package com.xjtu.toolbox.schedule

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.xjtu.toolbox.ui.theme.XJTUToolBoxTheme
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDateTime

@OptIn(ExperimentalComposeUiApi::class)
class ExamCardLayoutTest {
    @Test
    fun longCourseAndRoomWrapAtPhoneWidthInsteadOfHidingTheRoom() {
        val location = "创新港校区涵英楼一号教学楼东翼二层 2021 多媒体教室（请从东门进入）"
        val course = "电路与电子技术实验以及综合设计期末考试（闭卷）"
        val roomText = "考场 · $location"
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait {
            val scene = ImageComposeScene(360, 440, Density(1f)) {
                XJTUToolBoxTheme(darkModeOverride = "light") {
                    ExamCard(ExamItem(courseName = course, examDate = "2026-10-25", examTime = "14:00-16:00",
                        location = location, seatNumber = "32"), now = LocalDateTime(2026, 10, 21, 12, 0))
                }
            }
            try {
                val image = scene.render(0)
                val root = scene.semanticsOwners.first().rootSemanticsNode
                fun find(node: SemanticsNode, value: String): SemanticsNode? =
                    if (node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == value }) node
                    else node.children.firstNotNullOfOrNull { find(it, value) }
                val room = assertNotNull(find(root, roomText))
                val title = assertNotNull(find(root, course))
                assertTrue(room.boundsInRoot.height > 20, "长教室名应换行显示")
                assertTrue(title.boundsInRoot.height > 25, "长课程名应换行显示")
                assertTrue(room.boundsInRoot.bottom < 440, "考场应在卡片中完整可见")
                val out = File("build/reports/layout/exam-long-text.png")
                out.parentFile.mkdirs()
                out.writeBytes(assertNotNull(image.encodeToData()).bytes)
            } catch (error: Throwable) { failure = error }
            finally { scene.close() }
        }
        failure?.let { throw it }
    }
}
