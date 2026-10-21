package com.xjtu.toolbox.inbox

import com.xjtu.toolbox.nav.AppRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InboxRoutesTest {
    @Test
    fun `系统通知点进的页面对应哪类收纳`() {
        assertEquals(InboxCategories.GRADE, InboxCategories.ofRoute(AppRoute.JwappScore))
        assertEquals(InboxCategories.LMS, InboxCategories.ofRoute(AppRoute.Lms(3)))
        assertNull(InboxCategories.ofRoute(AppRoute.Settings))
    }

    @Test
    fun homeworkDeepLinkRoundTripsAndOldCachedTodosMigrate() {
        val target = AppRoute.Lms(7, 42)
        assertEquals("lms?courseId=7&activityId=42", target.id)
        assertEquals(target, com.xjtu.toolbox.nav.appRouteOf(target.id))
        assertEquals(AppRoute.Lms(7), com.xjtu.toolbox.nav.appRouteOf("lms?courseId=7"))
        val old = InboxItem("lms:7:42", InboxCategories.LMS, "课程", "作业", route = "lms?courseId=7")
        assertEquals(target.id, old.resolvedRouteId())
        assertEquals(old.route, old.copy(id = "lms:exam:7:42").resolvedRouteId())
        assertEquals(old.route, old.copy(id = "lms:bad:42").resolvedRouteId())
    }
}