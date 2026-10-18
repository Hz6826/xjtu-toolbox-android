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
 * `files/` 里只有自建日程能落地；其余条目由 [withoutUnlandedFiles] 在导入前滤掉，`filesWritten` 才是真数。
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

    /** 本端没有文件系统，恒为 null；自建日程每次都走 [putFile] 的合并（幂等）。 */
    override fun readFile(suffix: String, relPath: String): String? = null

    /** 自建日程并进端内键值店；其余条目导入前已滤掉。 */
    override fun putFile(suffix: String, relPath: String, content: String) {
        WebArchiveRows.land(suffix, relPath, content, storeOf = storeOf)
    }
}

/** 滤掉本端没有落点的 `files/` 条目（保留 `manifest.json`、`stores/` 与自建日程）。 */
internal fun withoutUnlandedFiles(entries: Map<String, String>): Map<String, String> =
    entries.filterKeys { path ->
        val parsed = ArchivePaths.parseFilePath(path) ?: return@filterKeys true
        lands(parsed.first, parsed.second)
    }

/** 本端没有落点的 `files/` 条目数（导入结果里报出来）。 */
internal fun unlandedFilesCount(entries: Map<String, String>): Int =
    entries.keys.count { path ->
        val parsed = ArchivePaths.parseFilePath(path) ?: return@count false
        !lands(parsed.first, parsed.second)
    }

/** 这条 `files/` 路径在本端有没有落点：自建日程，且在 `global/` 下。 */
private fun lands(relPath: String, suffix: String): Boolean =
    suffix.isEmpty() && WebArchiveRows.of(relPath) != null
