package com.xjtu.toolbox.web

import com.xjtu.toolbox.archive.ArchiveCodec
import com.xjtu.toolbox.archive.ArchiveImportTarget
import com.xjtu.toolbox.archive.ArchivePaths
import com.xjtu.toolbox.archive.StoreSnapshot
import com.xjtu.toolbox.archive.StoreValue
import com.xjtu.toolbox.archive.archiveStoreBackend
import com.xjtu.toolbox.platform.KeyValueStore
import com.xjtu.toolbox.platform.keyValueStore

/**
 * Web 这一端的导入写侧：`:core` 的 [ArchiveImportTarget] 在 wasm 上的落点。
 *
 * `files/` 那一层这一端**只有三族行数组里有落点的那一族**（自建日程，见 [WebArchiveRows]）：[putFile]
 * 按 §6.4 的合并把它并进端内那个键；其余条目（另两族的行数组、`agent_sessions/…`、以及任何带 suffix
 * 的那些）本端没有落点 ⇒ [withoutUnlandedFiles] 在导入**之前**把它们滤掉，而不是「假装写进去」
 * —— 这样 `StoreArchive.import` 报的 `filesWritten` 就是真写进本端的条数。
 */
internal class WebArchiveImportTarget(
    /** 写侧：按端内名取那个键值缝。 */
    private val storeOf: (String) -> KeyValueStore = { keyValueStore(it) },
) : ArchiveImportTarget {

    override fun readStore(rawName: String): StoreSnapshot? = archiveStoreBackend().readStore(rawName)

    override fun putKey(rawName: String, key: String, value: StoreValue, at: Long?) {
        val store = storeOf(rawName)
        when (value) {
            is StoreValue.S -> store.putString(key, value.v)
            is StoreValue.B -> store.putBoolean(key, value.v)
            is StoreValue.I -> store.putInt(key, value.v)
            is StoreValue.L -> store.putString(key, value.v.toString())
            is StoreValue.F -> store.putString(key, value.v.toString())
            is StoreValue.SetValue -> store.putStringSet(key, value.v.toSet())
        }
        if (at != null) store.putString(key + ArchiveCodec.WRITTEN_AT_SUFFIX, at.toString())
    }

    /**
     * 本端没有文件系统 ⇒ 这里恒为 `null`：`files/` 那一层里没有一样东西能说「端内已有一份、跳过」。
     * 三族行数组里有落点的那一族因此每次都走 [putFile] 的合并（合并本身是幂等的：已有主键不重复追加）。
     */
    override fun readFile(suffix: String, relPath: String): String? = null

    /**
     * 三族行数组里**有落点**的那一族（自建日程）⇒ 合并进端内那个键值店（§6.4 的「本端胜」在行这一级）。
     * 其余条目本端没有落点、导入前已被 [withoutUnlandedFiles] 滤掉 ⇒ 走到这里也只能什么都不写
     * （`filesWritten` 因此只数真落地的那些）。
     */
    override fun putFile(suffix: String, relPath: String, content: String) {
        WebArchiveRows.land(suffix, relPath, content, storeOf = storeOf)
    }
}

/**
 * 把档案里**本端没有落点**的 `files/` 条目滤掉（保留 `manifest.json`、`stores/` 与有落点的那一族行数组）——
 * 这一端没有文件系统，另两族的业务也不存在；滤掉之后「写了几笔」的计数才是真的。
 */
internal fun withoutUnlandedFiles(entries: Map<String, String>): Map<String, String> =
    entries.filterKeys { path ->
        val parsed = ArchivePaths.parseFilePath(path) ?: return@filterKeys true
        lands(parsed.first, parsed.second)
    }

/** 档案里**本端没有落点**的 `files/` 条目数（状态文本要如实说「几条没落地」）。 */
internal fun unlandedFilesCount(entries: Map<String, String>): Int =
    entries.keys.count { path ->
        val parsed = ArchivePaths.parseFilePath(path) ?: return@count false
        !lands(parsed.first, parsed.second)
    }

/** 这一条 `files/` 路径在本端有没有落点（三族行数组里本端有落点的那一族，且路径在 `global/` 下）。 */
private fun lands(relPath: String, suffix: String): Boolean =
    suffix.isEmpty() && WebArchiveRows.of(relPath) != null
