package com.focusforceplus.app.util

import com.focusforceplus.app.compliance.NagPolicy
import com.focusforceplus.app.data.db.entity.TodoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoReminderRulesTest {

    private val now = 1_800_000_000_000L
    private val minute = 60_000L
    private val hour = 60 * minute
    private val policy = NagPolicy(intervalMinutes = 5, maxRepeats = 3)

    private fun todo(
        id: Long = 1,
        priority: Int = 2,
        due: Long? = now - hour,
        nagCount: Int = 0,
        lastAlarmAt: Long? = null,
        snoozedUntil: Long? = null,
        done: Boolean = false,
        createdAt: Long = 0,
    ) = TodoEntity(
        id = id,
        title = "Todo $id",
        priority = priority,
        dueDateTime = due,
        nagCount = nagCount,
        lastAlarmAt = lastAlarmAt,
        snoozedUntil = snoozedUntil,
        isCompleted = done,
        createdAt = createdAt,
    )

    // ── nextWake ──────────────────────────────────────────────────────────────

    @Test
    fun `a todo due in the future wakes at its due time`() {
        assertEquals(now + hour, TodoReminderRules.nextWake(todo(due = now + hour), now, policy))
    }

    @Test
    fun `a snooze comes before everything else`() {
        val snoozed = todo(snoozedUntil = now + 10 * minute, nagCount = TodoEntity.NAG_DISMISSED)
        assertEquals(now + 10 * minute, TodoReminderRules.nextWake(snoozed, now, policy))
    }

    @Test
    fun `a missed wake-up is caught up soon`() {
        assertEquals(
            now + TodoReminderRules.CATCH_UP_MILLIS,
            TodoReminderRules.nextWake(todo(), now, policy),
        )
    }

    @Test
    fun `a missed wake-up with a turn waits for that turn`() {
        val turn = now + 10 * minute
        assertEquals(turn, TodoReminderRules.nextWake(todo(), now, policy, turn))
    }

    @Test
    fun `a turn that has passed is not waited for`() {
        assertEquals(
            now + TodoReminderRules.CATCH_UP_MILLIS,
            TodoReminderRules.nextWake(todo(), now, policy, now - minute),
        )
    }

    @Test
    fun `what the migration from 0_9_3 leaves behind stays quiet`() {
        // MIGRATION_10_11 marks overdue todos like this.
        for (priority in 0..2) {
            val migrated = todo(priority = priority, nagCount = TodoEntity.NAG_DISMISSED, lastAlarmAt = now - hour)
            assertNull("priority $priority", TodoReminderRules.nextWake(migrated, now, policy))
        }
    }

    @Test
    fun `a dismissed low priority todo does not speak`() {
        val low = todo(priority = 0, nagCount = TodoEntity.NAG_DISMISSED)
        assertNull(TodoReminderRules.nextWake(low, now, null))
    }

    @Test
    fun `low priority speaks once`() {
        assertEquals(
            now + TodoReminderRules.CATCH_UP_MILLIS,
            TodoReminderRules.nextWake(todo(priority = 0), now, null),
        )
        assertNull(TodoReminderRules.nextWake(todo(priority = 0, lastAlarmAt = now - minute, nagCount = 1), now, null))
    }

    @Test
    fun `a todo without a date waits for a daily reminder`() {
        assertNull(TodoReminderRules.nextWake(todo(due = null), now, policy))
    }

    @Test
    fun `repeats are spaced by the policy and end with it`() {
        val rung = todo(lastAlarmAt = now - 2 * minute, nagCount = 1)
        assertEquals(now + 3 * minute, TodoReminderRules.nextWake(rung, now, policy))

        val spent = todo(lastAlarmAt = now - 2 * minute, nagCount = policy.maxRepeats + 1)
        assertNull(TodoReminderRules.nextWake(spent, now, policy))
    }

    @Test
    fun `done and trashed todos never wake`() {
        assertNull(TodoReminderRules.nextWake(todo(done = true), now, policy))
        assertNull(TodoReminderRules.nextWake(todo().copy(deletedAt = now), now, policy))
    }

    // ── quietIfOverdue ────────────────────────────────────────────────────────

    @Test
    fun `saving with a time that has passed does not ring on the spot`() {
        val saved = TodoReminderRules.quietIfOverdue(todo(due = now - 5 * hour), now)
        assertEquals(TodoEntity.NAG_DISMISSED, saved.nagCount)
        assertNull(TodoReminderRules.nextWake(saved, now, policy))
    }

    @Test
    fun `saving for the minute that is running still rings`() {
        val saved = TodoReminderRules.quietIfOverdue(todo(due = now - 20_000L), now)
        assertEquals(0, saved.nagCount)
    }

    @Test
    fun `saving leaves a todo alone that is due later or has rung already`() {
        assertEquals(0, TodoReminderRules.quietIfOverdue(todo(due = now + hour), now).nagCount)
        assertEquals(0, TodoReminderRules.quietIfOverdue(todo(due = null), now).nagCount)
        val rung = todo(nagCount = 2, lastAlarmAt = now - minute)
        assertEquals(2, TodoReminderRules.quietIfOverdue(rung, now).nagCount)
    }

    // ── Missed wake-ups take turns ────────────────────────────────────────────

    @Test
    fun `missed todos take turns, the most important first`() {
        val gap = 5 * minute
        val turns = TodoReminderRules.catchUpTurns(
            listOf(
                todo(id = 1, priority = 1, due = now - 3 * hour),
                todo(id = 2, priority = 2, due = now - hour),
                todo(id = 3, priority = 2, due = now - 2 * hour),
            ),
            now, gap,
        )
        val first = now + TodoReminderRules.CATCH_UP_MILLIS
        assertEquals(mapOf(3L to first, 2L to first + gap, 1L to first + 2 * gap), turns)
    }

    @Test
    fun `only todos that were never heard get a turn`() {
        val turns = TodoReminderRules.catchUpTurns(
            listOf(
                todo(id = 1, priority = 0),
                todo(id = 2, due = now + hour),
                todo(id = 3, lastAlarmAt = now - minute, nagCount = 1),
                todo(id = 4, nagCount = TodoEntity.NAG_DISMISSED),
                todo(id = 5, snoozedUntil = now + minute),
                todo(id = 6, done = true),
                todo(id = 7, due = null),
                todo(id = 8),
            ),
            now, 5 * minute,
        )
        assertEquals(setOf(8L), turns.keys)
    }

    @Test
    fun `isMissed needs a due time in the past`() {
        assertTrue(TodoReminderRules.isMissed(todo(), now))
        assertFalse(TodoReminderRules.isMissed(todo(due = now + 1), now))
    }
}
