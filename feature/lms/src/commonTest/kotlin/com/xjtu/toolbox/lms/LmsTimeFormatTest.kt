package com.xjtu.toolbox.lms

import kotlin.test.Test
import kotlin.test.assertEquals

class LmsTimeFormatTest {
    @Test
    fun issue162MatchesSchoolPage() {
        val activity = LmsActivity(type = LmsActivityType.HOMEWORK,
            startTime = "2026-10-20T00:00:00Z", deadline = "2026-10-26T15:59:00Z")
        assertEquals("开始 2026/10/20 08:00 ~ 截止 2026/10/26 23:59", lmsActivityTimeLine(activity))
    }

    @Test
    fun equivalentInstantsAlwaysHaveTheSameCampusClock() {
        listOf(
            "2026-10-26T15:59:00Z",
            "2026-10-26T23:59:00+08:00",
            "2026-10-26T10:59:00-05:00",
            "2026-10-26T23:59:00+08:00[Asia/Shanghai]",
            "2026-10-26T15:59:00.123Z",
        ).forEach { assertEquals("2026/10/26 23:59", formatLmsTime(it), it) }
    }

    @Test
    fun crossesDateMonthAndYearBoundaries() {
        assertEquals("2027/01/01 00:00", formatLmsTime("2026-12-31T16:00:00Z"))
        assertEquals("2026/11/01 01:30", formatLmsTime("2026-10-31T17:30:00Z"))
    }

    @Test
    fun zoneLessLegacyValuesStayOnTheCampusClock() {
        assertEquals("2026/10/26 23:59", formatLmsTime("2026-10-26T23:59:00"))
        assertEquals("2026/10/26 23:59", formatLmsTime("2026-10-26 23:59:00"))
        assertEquals("", formatLmsTime(" "))
        assertEquals("未设置", formatLmsTime("未设置"))
    }

    @Test
    fun deadlinePriorityAndVisibilityFallbackSurviveFormatting() {
        val activity = LmsActivity(type = LmsActivityType.HOMEWORK,
            startTime = " ", visibleStartAt = "2026-10-20T00:00:00Z",
            endTime = "2026-10-25T15:59:00Z", deadline = "2026-10-26T15:59:00Z")
        assertEquals("可见 2026/10/20 08:00 ~ 截止 2026/10/26 23:59", lmsActivityTimeLine(activity))
        assertEquals("可见 2026/10/20 08:00 ~ 截止 2026/10/25 23:59",
            lmsActivityTimeLine(activity.copy(deadline = " ")))
        assertEquals("", lmsActivityTimeLine(LmsActivity(type = LmsActivityType.HOMEWORK)))
    }

    @Test
    fun lessonEndsAreStillLessonEndsRatherThanHomeworkDeadlines() {
        val activity = LmsActivity(type = LmsActivityType.LESSON,
            startTime = "2026-10-20T16:40:00Z", endTime = "2026-10-20T17:30:00Z",
            deadline = "2026-10-22T15:59:00Z")
        assertEquals("2026/10/21 00:40 ~ 2026/10/21 01:30", lmsActivityTimeLine(activity))
    }
}
