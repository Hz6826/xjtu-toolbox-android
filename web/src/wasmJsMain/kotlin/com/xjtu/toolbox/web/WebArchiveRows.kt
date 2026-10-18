package com.xjtu.toolbox.web

import com.xjtu.toolbox.archive.ArchiveFile
import com.xjtu.toolbox.archive.ArchiveRowFamilies
import com.xjtu.toolbox.archive.MergeOutcome
import com.xjtu.toolbox.platform.KeyValueStore
import com.xjtu.toolbox.platform.keyValueStore

/**
 * 三族行数组在 web 的落点：只有自建日程。浏览器没有下载队列和教材书架，
 * 那两族的条目导入时不落地，由导入结果报未落地条数。
 */
internal object WebArchiveRows {

    private val LANDED_STORES: Map<ArchiveRowFamilies.Family, String> = mapOf(
        ArchiveRowFamilies.Family.CUSTOM_COURSES to WebScheduleHost.CUSTOM_COURSE_STORE,
    )

    val landed: List<ArchiveRowFamilies.Family> = LANDED_STORES.keys.toList()

    /** 本端有落点的族；否则 null。 */
    fun of(relPath: String): ArchiveRowFamilies.Family? =
        ArchiveRowFamilies.of(relPath)?.takeIf { family -> family in LANDED_STORES }

    fun rowsOf(
        family: ArchiveRowFamilies.Family,
        storeOf: (String) -> KeyValueStore = { keyValueStore(it) },
    ): String? = storeOf(LANDED_STORES.getValue(family)).getString(family.key)

    /** 导出：有行的族 → `files/` 条目。 */
    fun filesOf(
        storeOf: (String) -> KeyValueStore = { keyValueStore(it) },
    ): List<ArchiveFile> = landed.mapNotNull { family ->
        val content = ArchiveRowFamilies.rowsForArchive(family, rowsOf(family, storeOf)) ?: return@mapNotNull null
        ArchiveFile(suffix = "", relPath = family.relPath, content = content)
    }

    /** 导入：合并后整段写回。没有落点（或带账号后缀）返回 null。 */
    fun land(
        suffix: String,
        relPath: String,
        content: String,
        storeOf: (String) -> KeyValueStore = { keyValueStore(it) },
    ): MergeOutcome? {
        if (suffix.isNotEmpty()) return null
        val family = of(relPath) ?: return null
        val outcome = ArchiveRowFamilies.merge(family, rowsOf(family, storeOf), content)
        storeOf(LANDED_STORES.getValue(family)).putString(family.key, outcome.content)
        return outcome
    }
}
