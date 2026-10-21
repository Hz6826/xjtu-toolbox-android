package com.xjtu.toolbox.notification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class NoticeDeltaTest {
    private val today = LocalDate(2026, 10, 21)
    private val source = NotificationSource.JWC
    private fun item(id: String, day: Int) = Notification(id, "https://example.invalid/$id", source,
        date = LocalDate(2026, 10, day))

    @Test
    fun failedFirstFetchDoesNotBaselineAServiceOrReplayItsHistoryOnRecovery() {
        val failed = noticeDelta(emptyList(), emptySet(), emptyList(), emptySet(), emptyMap(), today)
        assertTrue(failed.baselined.isEmpty())
        val recovery = noticeDelta(listOf(item("old", 1)), setOf(source), failed.seen, failed.baselined, failed.latestDates, today)
        assertTrue(recovery.fresh.isEmpty())
        assertTrue(source.name in recovery.baselined)
    }

    @Test
    fun anOldPinnedArticleDoesNotBecomeNewAfterItsLinkIsEvicted() {
        val delta = noticeDelta(listOf(item("pinned", 1), item("new", 21)), setOf(source), emptyList(),
            setOf(source.name), mapOf(source to LocalDate(2026, 10, 20)), today)
        assertEquals(listOf("new"), delta.fresh.map { it.title })
        assertEquals(today, delta.latestDates[source])
    }

    @Test
    fun sameDayNewLinksAreKeptAndRepeatedPollingIsQuiet() {
        val first = noticeDelta(listOf(item("new", 21)), setOf(source), emptyList(),
            setOf(source.name), mapOf(source to today), today)
        assertEquals(1, first.fresh.size)
        val again = noticeDelta(listOf(item("new", 21)), setOf(source), first.seen,
            first.baselined, first.latestDates, today)
        assertTrue(again.fresh.isEmpty())
    }

    @Test
    fun legacyLinkOnlyStateRecoversTheDateWatermarkAndSourcesAreIndependent() {
        val known = item("known", 20)
        val delta = noticeDelta(listOf(known, item("old", 1), item("new", 21)), setOf(source), listOf(known.link),
            setOf(source.name), emptyMap(), today)
        assertEquals(listOf("new"), delta.fresh.map { it.title })
        assertTrue(NotificationSource.GS.name !in delta.baselined)
    }
}
