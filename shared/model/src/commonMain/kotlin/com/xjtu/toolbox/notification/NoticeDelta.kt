package com.xjtu.toolbox.notification

import kotlinx.datetime.LocalDate

/** 每源独立的公告基线；请求失败不会建立基线，旧链接被轮出缓存后也不重新当作新公告。 */
data class NoticeDelta(
    val fresh: List<Notification>,
    val seen: List<String>,
    val baselined: Set<String>,
    val latestDates: Map<NotificationSource, LocalDate>,
)

fun noticeDelta(
    items: List<Notification>,
    successfulSources: Set<NotificationSource>,
    seenLinks: List<String>,
    baselinedSources: Set<String>,
    latestDates: Map<NotificationSource, LocalDate>,
    today: LocalDate,
): NoticeDelta {
    val seen = seenLinks.toMutableSet()
    val baselined = baselinedSources.toMutableSet()
    val cutoffs = latestDates.toMutableMap()
    val fresh = mutableListOf<Notification>()
    for (source in successfulSources) {
        val page = items.filter { it.source == source }.distinctBy { it.link }
        val initial = source.name !in baselined
        // 旧版本只有链接集合，迁移时从仍在集合里的文章恢复日期水位。
        val cutoff = cutoffs[source] ?: page.filter { it.link in seen }.maxOfOrNull { it.date }
        if (!initial) fresh += page.filter { it.link !in seen && (cutoff == null || it.date >= cutoff) }
        seen += page.map { it.link }
        baselined += source.name
        val latest = page.maxOfOrNull { it.date } ?: today
        cutoffs[source] = maxOf(cutoff ?: latest, latest)
    }
    return NoticeDelta(fresh.sortedByDescending { it.date }, seen.toList(), baselined, cutoffs)
}
