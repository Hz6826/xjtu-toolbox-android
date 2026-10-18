package com.xjtu.toolbox.desktop

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// 桌面包的版本号必须与 `:app` 的 `versionName` 同源（逐字相同）。
//
// 为什么要有这一条：`desktopPackageVersion` 是 jpackage 的 `--app-version`，它同时决定四种安装包的文件名
// （`.deb` / `.AppImage` / `.msi` / `.exe`，见 desktop/build.gradle.kts 的 `packageVersion`）与
// `DesktopBuildConfig.VERSION_NAME`（进而决定设置页的 `vX.Y.Z (desktop)`、反馈表的「版本」列、归档清单的
// `appVersion` 与 tar.gz 的文件名）。而它与 `app/build.gradle.kts` 的 `versionName` 是两个互不相干的手抄本：
// 发版时只改一处就会漂。
//
// 实证（CI 上那次发版事故）：`:app` 已经是 5.2.0，而 `:desktop:packageMsi` 的 jpackage 命令行里输出的仍是旧版本
// —— `…\msi\xjtu-toolbox-5.1.1.msi`。同一个 release 里 App 叫 5.2.0、Windows 安装包叫 5.1.1。
class DesktopPackageVersionJvmTest {

    @Test
    fun `桌面包版本与 app 的 versionName 逐字相同 —— 升级时只改一处就会红`() {
        val desktop = desktopPackageVersion()
        val app = appVersionName()
        assertEquals(
            app,
            desktop,
            "desktop/build.gradle.kts 的 desktopPackageVersion 与 app/build.gradle.kts 的 versionName 必须同源：" +
                "现在桌面包是 \"$desktop\"、App 是 \"$app\" —— 只改一处就会出「App 是 5.2.0、安装包叫 5.1.1」这种事故",
        )
    }

    @Test
    fun `桌面包版本是 jpackage 只收的 x y z 形状 —— 带后缀的名字进不了包`() {
        val desktop = desktopPackageVersion()
        assertTrue(
            Regex("""\d+\.\d+\.\d+""").matches(desktop),
            "jpackage 的 `--app-version` 只收 x.y.z（见 desktop/build.gradle.kts 那一行上方的注释）：" +
                "现在 desktopPackageVersion = \"$desktop\"",
        )
    }

    // ── 读源码 ──

    // 从仓库根按相对路径读一份源码；读不到就明确失败（不静默跳过，也不当成空文件比过去）。
    private fun readRepoFile(relative: String): String {
        val file = File(repoRoot(), relative)
        assertTrue(file.isFile, "读不到 $relative：按仓库根 ${repoRoot()} 拼出来的路径是 ${file.absolutePath}")
        return file.readText()
    }

    // desktop/build.gradle.kts 里 `val desktopPackageVersion = "x.y.z"` 的那个字面值。
    private fun desktopPackageVersion(): String = theOnlyMatch(
        Regex("""(?m)^\s*val\s+desktopPackageVersion\s*=\s*"([^"]*)"""),
        readRepoFile("desktop/build.gradle.kts"),
        "desktop/build.gradle.kts 里的 desktopPackageVersion",
    )

    // app/build.gradle.kts 里 `defaultConfig` 那一处 `versionName = "x.y.z"` 的字面值。
    // 形状锚在 `versionName =` 上，所以 `buildTypes` 里的 `versionNameSuffix = "-dev.…"` 不会串进来。
    private fun appVersionName(): String = theOnlyMatch(
        Regex("""(?m)^\s*versionName\s*=\s*"([^"]*)"""),
        readRepoFile("app/build.gradle.kts"),
        "app/build.gradle.kts 里的 versionName",
    )

    // 只认唯一一处匹配：写法的形状变了（一处都没有、或突然多处）也当失败，不猜是哪一处。
    private fun theOnlyMatch(regex: Regex, source: String, what: String): String {
        val hits = regex.findAll(source).map { it.groupValues[1] }.toList()
        assertEquals(1, hits.size, "$what 应当只有一处声明，实际 ${hits.size} 处：$hits")
        return hits.single()
    }

    // 仓库根目录：从工作目录往上找 `settings.gradle.kts`（Gradle 的 Test 任务以模块目录为工作目录）。
    private fun repoRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        error("找不到仓库根目录（settings.gradle.kts）：工作目录是 ${File(".").absolutePath}")
    }
}
