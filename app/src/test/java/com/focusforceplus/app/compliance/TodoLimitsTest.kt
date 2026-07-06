package com.focusforceplus.app.compliance

import org.junit.Assert.assertEquals
import org.junit.Test

class TodoLimitsTest {

    @Test
    fun `high priority keeps requested values inside the caps`() {
        val limits = clampTodoLimits(priority = 2, requestedMaxSnooze = 3, requestedMaxReschedule = 2)
        assertEquals(3, limits.maxSnoozeCount)
        assertEquals(2, limits.maxRescheduleCount)
    }

    @Test
    fun `high priority clamps values above the caps`() {
        val limits = clampTodoLimits(priority = 2, requestedMaxSnooze = 99, requestedMaxReschedule = 99)
        assertEquals(ComplianceLimits.MAX_TODO_SNOOZE_LIMIT, limits.maxSnoozeCount)
        assertEquals(ComplianceLimits.MAX_TODO_RESCHEDULE_LIMIT, limits.maxRescheduleCount)
    }

    @Test
    fun `high priority never allows zero — one escape valve always remains`() {
        val limits = clampTodoLimits(priority = 2, requestedMaxSnooze = 0, requestedMaxReschedule = -5)
        assertEquals(1, limits.maxSnoozeCount)
        assertEquals(1, limits.maxRescheduleCount)
    }

    @Test
    fun `medium priority uses fixed defaults regardless of request`() {
        val limits = clampTodoLimits(priority = 1, requestedMaxSnooze = 5, requestedMaxReschedule = 5)
        assertEquals(ComplianceLimits.DEFAULT_TODO_MAX_SNOOZES, limits.maxSnoozeCount)
        assertEquals(ComplianceLimits.TODO_UNLIMITED_RESCHEDULES, limits.maxRescheduleCount)
    }

    @Test
    fun `low priority uses fixed defaults regardless of request`() {
        val limits = clampTodoLimits(priority = 0, requestedMaxSnooze = 1, requestedMaxReschedule = 1)
        assertEquals(ComplianceLimits.DEFAULT_TODO_MAX_SNOOZES, limits.maxSnoozeCount)
        assertEquals(ComplianceLimits.TODO_UNLIMITED_RESCHEDULES, limits.maxRescheduleCount)
    }
}
