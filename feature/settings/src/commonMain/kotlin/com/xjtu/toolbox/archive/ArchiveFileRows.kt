package com.xjtu.toolbox.archive

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * `files` 层里「行数组」的排序纯函数（§2.3）。
 *
 * 输入的 [JsonArray] 由各端宿主给出（Android 是 Room 三张表的只读投影、桌面/web 是端内键值店里那段
 * 行数组，见 [ArchiveRowFamilies]），这里只是它们要调的排序，不含任何端内 I/O。
 */
object ArchiveFileRows {

    /** §2.3：`files/courses/custom_courses.json` 按六键升序。 */
    fun sortCustomCourses(rows: JsonArray): JsonArray =
        reorder(rows,
            compareBy<JsonObject> { it.str("accountId") }
                .thenBy { it.str("termCode") }
                .thenBy { it.int("dayOfWeek") }
                .thenBy { it.int("startSection") }
                .thenBy { it.int("startMinuteOfDay") }
                .thenBy { it.long("id") })

    /** §2.3：`files/media/download_tasks.json` 按 `(createTime desc, id)`。 */
    fun sortDownloadTasks(rows: JsonArray): JsonArray =
        reorder(rows,
            compareByDescending<JsonObject> { it.long("createTime") }
                .thenBy { it.long("id") })

    /** §2.3：`files/jiaocai/jiaocai1_shelf.json` 按 `(addedAt desc, ssno)`。 */
    fun sortJiaocaiShelf(rows: JsonArray): JsonArray =
        reorder(rows,
            compareByDescending<JsonObject> { it.long("addedAt") }
                .thenBy { it.str("ssno") })

    /** §2.3：`files/agent_sessions/index.json` 按 `updatedAt` 降序。 */
    fun sortAgentSessionsIndex(rows: JsonArray): JsonArray =
        reorder(rows, compareByDescending<JsonObject> { it.long("updatedAt") })

    // ── 内部 ──────────────────────────────────────────────────────────────

    private fun reorder(rows: JsonArray, comparator: Comparator<JsonObject>): JsonArray {
        val objects: List<JsonObject> = rows.mapNotNull { it as? JsonObject }
        return JsonArray(objects.sortedWith(comparator).map { it as JsonElement })
    }

    private fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.content ?: ""

    private fun JsonObject.int(key: String): Int = (this[key] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0

    private fun JsonObject.long(key: String): Long = (this[key] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L
}
