package com.focusforceplus.app.util

import com.focusforceplus.app.compliance.NagPolicy
import com.focusforceplus.app.data.db.entity.TodoEntity

/**
 * The timing rules of [TodoReminderEngine]: when a todo makes itself heard next.
 * They only look at the stored todo and the clock, which is what makes them easy
 * to test on their own.
 */
internal object TodoReminderRules {

    /** Delay before catching up on a wake-up that was missed (phone off, app killed). */
    const val CATCH_UP_MILLIS = 5_000L
    const val MINUTE = 60_000L

    fun isSnoozed(todo: TodoEntity, now: Long): Boolean = (todo.snoozedUntil ?: 0L) > now

    /** Medium or high, due, and never heard: its wake-up was missed. */
    fun isMissed(todo: TodoEntity, now: Long): Boolean =
        todo.priority >= 1 && !todo.isCompleted && !todo.isInTrash && !isSnoozed(todo, now) &&
            todo.dueDateTime != null && todo.dueDateTime <= now &&
            todo.lastAlarmAt == null && todo.nagCount != TodoEntity.NAG_DISMISSED

    /**
     * A todo saved with a time that has already passed does not ring the moment it
     * is saved: it shows as open and joins the next daily reminder. One set for the
     * minute that is running right now still rings.
     */
    fun quietIfOverdue(todo: TodoEntity, now: Long): TodoEntity {
        val due = todo.dueDateTime ?: return todo
        val unheard = todo.nagCount == 0 && todo.lastAlarmAt == null
        return if (unheard && due <= now - MINUTE) todo.copy(nagCount = TodoEntity.NAG_DISMISSED) else todo
    }

    /**
     * When this todo should next make itself heard, or null if it should not.
     *
     * [policy] is how often and how far apart a medium or high todo repeats.
     * [turn] is the moment a missed wake-up was given when several were missed at
     * once, so that they do not all ring together.
     */
    fun nextWake(todo: TodoEntity, now: Long, policy: NagPolicy?, turn: Long? = null): Long? {
        if (todo.isCompleted || todo.isInTrash) return null
        todo.snoozedUntil?.takeIf { it > now }?.let { return it }
        val due = todo.dueDateTime
        if (due != null && due > now) return due

        // Dismissed for now, or brought back quietly: nothing until a daily reminder.
        if (todo.nagCount == TodoEntity.NAG_DISMISSED) return null
        if (todo.priority == 0) {
            // Low priority speaks once. If its moment passed unheard, soon.
            return if (due != null && todo.lastAlarmAt == null) now + CATCH_UP_MILLIS else null
        }
        // Without a date a todo only starts once a daily reminder has brought it up.
        if (due == null && todo.lastAlarmAt == null) return null
        // Its moment passed unheard: soon, or when its turn comes if several did.
        val last = todo.lastAlarmAt ?: return turn?.takeIf { it > now } ?: (now + CATCH_UP_MILLIS)

        if (policy == null || todo.nagCount > policy.maxRepeats) return null
        return maxOf(last + policy.intervalMinutes * MINUTE, now + CATCH_UP_MILLIS)
    }

    /**
     * Deals out the turns for todos whose wake-up was missed: the most important
     * one soon, the others [gapMillis] apart behind it.
     */
    fun catchUpTurns(todos: List<TodoEntity>, now: Long, gapMillis: Long): Map<Long, Long> =
        todos.filter { isMissed(it, now) }
            .sortedWith(
                compareByDescending<TodoEntity> { it.priority }
                    .thenBy { it.dueDateTime ?: Long.MAX_VALUE }
                    .thenBy { it.createdAt }
            )
            .mapIndexed { index, todo -> todo.id to now + CATCH_UP_MILLIS + index * gapMillis }
            .toMap()
}
