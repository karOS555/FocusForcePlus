package com.focusforceplus.app.compliance

/**
 * The effective snooze/reschedule caps persisted on a todo.
 */
data class TodoLimits(
    val maxSnoozeCount: Int,
    val maxRescheduleCount: Int,
)

/**
 * Normalises user-requested snooze/reschedule caps against [ComplianceLimits].
 *
 * High priority (2) is the todo module's lock-like mode: the alarm cannot be
 * dismissed, so its escape paths (snooze, reschedule) must be present and bounded.
 * The requested values are clamped into `1..MAX` — never 0, so at least one snooze
 * and one reschedule always exist as pressure valves.
 *
 * Low/medium priority always keeps a Dismiss action, so the fixed defaults apply and
 * reschedules stay unbounded (see [ComplianceLimits.TODO_UNLIMITED_RESCHEDULES]).
 *
 * Source of truth: `.claude/PLAY_STORE_COMPLIANCE.md` sections 2.1 and 2.3, applied
 * to todos per `.claude/todo-compliance-review.md`.
 */
fun clampTodoLimits(priority: Int, requestedMaxSnooze: Int, requestedMaxReschedule: Int): TodoLimits =
    if (priority == 2) {
        TodoLimits(
            maxSnoozeCount = requestedMaxSnooze.coerceIn(1, ComplianceLimits.MAX_TODO_SNOOZE_LIMIT),
            maxRescheduleCount = requestedMaxReschedule.coerceIn(1, ComplianceLimits.MAX_TODO_RESCHEDULE_LIMIT),
        )
    } else {
        TodoLimits(
            maxSnoozeCount = ComplianceLimits.DEFAULT_TODO_MAX_SNOOZES,
            maxRescheduleCount = ComplianceLimits.TODO_UNLIMITED_RESCHEDULES,
        )
    }
