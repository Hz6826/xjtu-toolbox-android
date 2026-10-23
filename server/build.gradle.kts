// :server —— serve 模式：用 Ktor（CIO）托管 `:web` 的 wasm 产物并实现 `/api/*`（契约见 docs/api-contract.md）。
plugins {
    alias(libs.plugins.kotlin.jvm)
    // `./gradlew :server:run --args="--port 18234 --dist …"`
    application
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // JDK 21 工具链
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":data"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
    // JUnit4：kotlin-jvm 上 `kotlin("test")` 要显式给引擎
    testImplementation(libs.junit)
    // ⚠️ `:testkit` 只能挂 test 源集：它的假统一认证收任何密码。
    testImplementation(project(":testkit"))
    // 黄页是 Ktor 接口 ⇒ 用 MockEngine 喂夹具响应
    testImplementation(libs.ktor.client.mock)
}

// :server 的发行包为什么会撞名：JetBrains 在桌面目标上发布的一组坐标是**空壳模块**（jar 里 0 个 class，
// 只有 META-INF / License），真类在同名的 androidx artifact 里 —— 两个 group 的 jar **文件名完全相同**，
// 于是 Gradle 9 的 Copy（installDist / distZip）直接失败：
//   Entry lib/runtime-desktop-1.12.1.jar is a duplicate but no duplicate handling strategy has been set
// 名单由 installDist 自己报全（把 duplicatesStrategy 临时设成 WARN 跑一次），共 8 个撞名，**只有**这些；
// 反例：JB 的 ui-desktop / foundation / material-icons 是真的（1779 / 2074 / 11105 个 class），不在名单里。
// 这里把空壳换成真身，而**不**设 duplicatesStrategy：WARN 的输出显示空壳 jar 会**覆盖**真身
// （org.jetbrains.compose.runtime:runtime-desktop … overwriting … androidx.compose.runtime:runtime-desktop），
// 那样打出来的包会缺 Compose runtime 的类。上游再引入新的空壳时，installDist 会以同样的 duplicate
// 报错响亮失败 —— 不会静默少类。
val jetbrainsDesktopShells = mapOf(
    "org.jetbrains.androidx.lifecycle:lifecycle-common-jvm" to "androidx.lifecycle:lifecycle-common-jvm",
    "org.jetbrains.androidx.lifecycle:lifecycle-runtime-desktop" to "androidx.lifecycle:lifecycle-runtime-desktop",
    "org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose-desktop" to "androidx.lifecycle:lifecycle-runtime-compose-desktop",
    "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-desktop" to "androidx.lifecycle:lifecycle-viewmodel-desktop",
    "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose-desktop" to "androidx.lifecycle:lifecycle-viewmodel-compose-desktop",
    "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-savedstate-desktop" to "androidx.lifecycle:lifecycle-viewmodel-savedstate-desktop",
    "org.jetbrains.compose.runtime:runtime-desktop" to "androidx.compose.runtime:runtime-desktop",
    "org.jetbrains.compose.runtime:runtime-saveable-desktop" to "androidx.compose.runtime:runtime-saveable-desktop",
)

configurations.configureEach {
    resolutionStrategy.eachDependency {
        // 版本跟着被替换的那个请求走 —— 两边版本本来就由 :core 的版本目录对齐。
        jetbrainsDesktopShells["${requested.group}:${requested.name}"]
            ?.let { useTarget("$it:${requested.version}") }
    }
}

application {
    mainClass.set("com.xjtu.toolbox.server.MainKt")
}

/** `run` 的工作目录固定成仓库根。 */
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

tasks.named<Test>("test") {
    val root = layout.buildDirectory.dir("testStoreRoot")
    doFirst { root.get().asFile.deleteRecursively() }
    systemProperty("xjtu.toolbox.keyValueRoot", root.get().asFile.absolutePath)
}
