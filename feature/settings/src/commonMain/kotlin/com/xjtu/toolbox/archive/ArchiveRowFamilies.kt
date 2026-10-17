package com.xjtu.toolbox.archive

import com.xjtu.toolbox.util.AppJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 三族「行数组」在**端内**与**档案 `files/` 层**之间的那两件纯事（§2.3 / §6.3 / §6.4）。
 *
 * 端内源按端不同：Android 是 Room 三张表（只读投影在 `:app` 的 `RoomArchiveFamilies`），桌面/web
 * 是键值店里的一个键（单键里那段 JSON 本身就是行数组 ⇒ 「拆成行数组」= 按 §2.3 重排 + 原样搬运）。
 *
 *  - [rowsForArchive]：端内那段 JSON → 档案行数组的字节（排序交给 [ArchiveFileRows]，不另写一份）；
 *  - [merge]：档案行数组 → 端内那段 JSON（§6.4 的「本端胜」在**行**这一级）。
 *
 * 这一层不碰任何 I/O：键值店的读写归各端宿主（桌面 `DesktopArchiveRows`、web `WebArchiveRows`）。
 * 列名以 Room 三张实体为准（`CustomCourseEntity` / `DownloadTaskEntity` / `Jiaocai1ShelfEntity`），
 * 行内的键名、值与字段集合一个不改。
 */
object ArchiveRowFamilies {

    /** §2.3：`files/courses/custom_courses.json`。 */
    const val CUSTOM_COURSES_REL = "courses/custom_courses.json"

    /** §2.3：`files/media/download_tasks.json`。 */
    const val DOWNLOAD_TASKS_REL = "media/download_tasks.json"

    /** §2.3：`files/jiaocai/jiaocai1_shelf.json`。 */
    const val JIAOCAI_SHELF_REL = "jiaocai/jiaocai1_shelf.json"

    /**
     * 一族行数组的形状。
     *
     * @param relPath 档案里 `files/` 之下的相对路径（§2.3 逐字）
     * @param key 桌面/web 端内键值店里那一族的**键名**（Android 没有这一层：那边是 Room 表）
     * @param primaryKey 主键列名（合并的判据）
     * @param requiredColumns 缺了就不追加的列：`custom_courses` 的端内读法是严格解码
     *   （`AppJson.decodeFromString<List<CustomCourse>>`，这几个字段没有默认值），缺列的行进来会让
     *   整段解不开；另两族的读法宽容（缺字段回落默认值），所以必填列是空表。
     */
    enum class Family(
        val relPath: String,
        val key: String,
        val primaryKey: String,
        val requiredColumns: List<String>,
    ) {
        /** `custom_courses` 表 / 桌面的 `custom_courses` 单键。 */
        CUSTOM_COURSES(
            CUSTOM_COURSES_REL,
            "custom_courses",
            "id",
            listOf("courseName", "weekBits", "dayOfWeek", "startSection", "endSection", "termCode"),
        ),

        /** `download_tasks` 表 / 桌面的 `download_tasks<账号后缀>` 店里的 `rows` 键。 */
        DOWNLOAD_TASKS(DOWNLOAD_TASKS_REL, "rows", "id", emptyList()),

        /** `jiaocai1_shelf` 表 / 桌面的 `jiaocai1_shelf<账号后缀>` 店里的 `rows` 键。 */
        JIAOCAI_SHELF(JIAOCAI_SHELF_REL, "rows", "ssno", emptyList()),
    }

    /** 档案里的相对路径 → 族；不是这三条之一 ⇒ null（别的 `files/` 条目与这一层无关）。 */
    fun of(relPath: String): Family? = Family.entries.firstOrNull { it.relPath == relPath }

    /** §2.3 的排序口径（逐字调 [ArchiveFileRows]，不在这里另写一份比较器）。 */
    fun sort(family: Family, rows: JsonArray): JsonArray = when (family) {
        Family.CUSTOM_COURSES -> ArchiveFileRows.sortCustomCourses(rows)
        Family.DOWNLOAD_TASKS -> ArchiveFileRows.sortDownloadTasks(rows)
        Family.JIAOCAI_SHELF -> ArchiveFileRows.sortJiaocaiShelf(rows)
    }

    /**
     * 端内那段 JSON → 档案行数组的**字节**（§2.3：原样搬运 + 排好序）。
     *
     * 缺键 / 空表 / 解不开 ⇒ `null` = 这一族**不写进档案**：桌面上这三族没有「一张空表还在那儿」
     * 那种东西（那是 Room 才有的形状），写一个 `[]` 会声称本端这一族是空的。
     */
    fun rowsForArchive(family: Family, raw: String?): String? {
        val rows = parseRows(raw) ?: return null
        if (rows.isEmpty()) return null
        return sort(family, rows).toString()
    }

    /**
     * 档案行数组 → 端内那段 JSON 的**合并**（§6.4 的「本端胜」在行这一级）：
     *  1. 本端已有的主键**整行保留**（值一个字不改、位置也不动）——这三族在端内没有 `at` 可比；
     *  2. 只追加端内没有的主键，`id` 保持档案里的原值（`maxOf(id) + 1` 的自增口径因此不破）；
     *  3. 缺主键、或缺 [Family.requiredColumns] 的行跳过并计入 [MergeOutcome.skipped] —— 宁可少一行，
     *     也不让端内整段数组解不开。
     *
     * 档案里那段解不开（不是 JSON 数组）⇒ 端内那段**原样返回**、一行都不追加。
     */
    fun merge(family: Family, localRaw: String?, archiveContent: String): MergeOutcome {
        val local = parseRows(localRaw)?.filterIsInstance<JsonObject>() ?: emptyList()
        val archive = parseRows(archiveContent)
            ?: return MergeOutcome(localRaw ?: "[]", appended = 0, kept = local.size, skipped = 0)

        val known = HashSet<String>(local.size * 2)
        local.forEach { row -> row.keyOf(family.primaryKey)?.let { known.add(it) } }

        val out = ArrayList<JsonObject>(local.size + archive.size)
        out.addAll(local)
        var appended = 0
        var skipped = 0
        for (element in archive) {
            val row = element as? JsonObject
            val key = row?.keyOf(family.primaryKey)
            val complete = row != null && key != null && family.requiredColumns.all { row[it] != null }
            if (!complete) {
                skipped++
                continue
            }
            // 本端已有这个主键 ⇒ 本端胜（档案里那一行不进来，端内那一行不改）
            if (!known.add(key)) continue
            out.add(row)
            appended++
        }
        return MergeOutcome(JsonArray(out).toString(), appended = appended, kept = local.size, skipped = skipped)
    }

    // ── 内部 ──────────────────────────────────────────────────────────────

    /** 一段 JSON → 行数组；空串 / 缺键 / 不是数组 ⇒ null（坏的那一段当「没有」）。 */
    private fun parseRows(raw: String?): JsonArray? {
        if (raw.isNullOrBlank()) return null
        return runCatching { AppJson.parseToJsonElement(raw) as? JsonArray }.getOrNull()
    }

    /** 主键列的值按**文本**比（数字与字符串两种主键同一处处理）；JSON null / 缺列 ⇒ null。 */
    private fun JsonObject.keyOf(primaryKey: String): String? =
        (this[primaryKey] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
}

/** 一次「档案行数组 → 端内键值店」的合并结果。 */
data class MergeOutcome(
    /** 要写回端内那个键的 JSON（本端的行在前、档案里新追加的行在后）。 */
    val content: String,
    /** 追加的行数（端内没有这个主键的那些）。 */
    val appended: Int,
    /** 原样保留的行数（本端胜的那些）。 */
    val kept: Int,
    /** 档案里缺主键或缺必填列、因而没有追加的行数。 */
    val skipped: Int,
)
