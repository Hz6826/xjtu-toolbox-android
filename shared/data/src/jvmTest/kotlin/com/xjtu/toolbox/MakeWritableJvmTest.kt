package com.xjtu.toolbox

import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue

/**
 * [makeWritable] 的钉子：CI 上那条
 * `keytool error: java.io.FileNotFoundException: .../trust.p12 (Permission denied)`
 * 就是「`Files.copy` 把只读的 cacerts 权限一起带过去」造成的，这里用 444 的临时文件复刻同一前提。
 */
class MakeWritableJvmTest {

    @Test
    fun `只读的副本被改成可写（copy 会把权限一起带过来；CI 的 cacerts 是 444）`() {
        val path = Files.createTempFile("make-writable", ".p12")
        // 非 POSIX 文件系统（Windows）没有权限位可设：跳过。
        assumeTrue(Files.getFileStore(path).supportsFileAttributeView("posix"))
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("r--r--r--"))
        assertFalse(path.toFile().canWrite(), "444 的副本本来就该是只读的 —— 这是 CI 那条 bug 的前提")

        makeWritable(path)

        assertTrue(path.toFile().canWrite(), "makeWritable 之后必须可写，否则 keytool -importcert 报 Permission denied")
        assertEquals(
            PosixFilePermissions.fromString("rw-------"),
            Files.getPosixFilePermissions(path),
            "只给属主读写（信任库副本里有私钥材料）",
        )
        // 真的写一次：keytool 卡住的就是这一步。
        Files.write(path, byteArrayOf(1, 2, 3))
    }
}
