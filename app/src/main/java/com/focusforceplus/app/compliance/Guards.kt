package com.focusforceplus.app.compliance

import com.focusforceplus.app.data.db.entity.RoutineEntity
import com.focusforceplus.app.data.db.entity.TodoEntity

/**
 * Speed bumps are the small, deliberate obstacles in front of the actions that are
 * easiest to do on reflex: ticking something off that is not done, snoozing an
 * alarm, ending a focus session, asking for a few more minutes of a blocked app.
 *
 * They exist to turn a reflex into a decision, not to lock anyone in. That sets
 * their limits, and those limits are the compliance contract:
 *
 * - every speed bump can be passed by anyone in about a minute at most: asking
 *   again is one tap, the countdown is capped by
 *   [ComplianceLimits.MAX_GUARD_WAIT_SECONDS], and every task has a plain
 *   alternative for screen readers and switch access,
 * - the user picks them, per item and as defaults, and one switch turns all of
 *   them off,
 * - they only ever sit in front of actions inside this app. Nothing here touches
 *   system settings, uninstalling, or anything outside FocusForce+.
 */

/** The hands-on part of a speed bump. */
enum class Challenge(val key: String, val label: String, val detail: String) {
    NONE("none", "Nothing", "No task."),
    HOLD("hold", "Hold", "Press and hold a button for a moment."),
    SLIDE("slide", "Slider", "Slide across, in a direction that changes."),
    PATH("path", "Path slider", "Drag along a path that is laid out differently every time."),
    MATH("math", "Sum", "Work out a small sum.");

    companion object {
        fun fromKey(key: String?): Challenge? = entries.firstOrNull { it.key == key }
    }
}

/**
 * The speed bump in front of one action. Its three parts are each optional and add
 * up. They run in this order: asking again, a countdown, then the [challenge].
 */
data class Guard(
    val confirm: Boolean = false,
    val waitSeconds: Int = 0,
    val challenge: Challenge = Challenge.NONE,
) {
    val isNone: Boolean get() = !confirm && waitSeconds <= 0 && challenge == Challenge.NONE

    fun clamped(): Guard = copy(waitSeconds = waitSeconds.coerceIn(0, ComplianceLimits.MAX_GUARD_WAIT_SECONDS))

    /** For lists and rows: "Ask again · Wait 10 s · Path slider", or "Nothing". */
    fun describe(): String {
        if (isNone) return "Nothing"
        return buildList {
            if (confirm) add("Ask again")
            if (waitSeconds > 0) add("Wait $waitSeconds s")
            if (challenge != Challenge.NONE) add(challenge.label)
        }.joinToString(" · ")
    }

    /** Compact text form for storage, for example "confirm+wait10+path". */
    fun encode(): String = if (isNone) {
        NONE_KEY
    } else {
        buildList {
            if (confirm) add("confirm")
            if (waitSeconds > 0) add("wait$waitSeconds")
            if (challenge != Challenge.NONE) add(challenge.key)
        }.joinToString("+")
    }

    companion object {
        private const val NONE_KEY = "none"
        val NONE = Guard()

        /** The countdown lengths offered in the editor; 0 = no countdown. */
        val WAIT_CHOICES = listOf(0, 5, 10, 30, 60)

        /** Null when [text] is empty or not something [encode] wrote. */
        fun decode(text: String?): Guard? {
            if (text.isNullOrBlank()) return null
            if (text == NONE_KEY) return NONE
            var confirm = false
            var wait = 0
            var challenge = Challenge.NONE
            for (part in text.split('+')) {
                when {
                    part == "confirm" -> confirm = true
                    part.startsWith("wait") -> wait = part.removePrefix("wait").toIntOrNull() ?: return null
                    else -> challenge = Challenge.fromKey(part) ?: return null
                }
            }
            return Guard(confirm, wait, challenge).clamped()
        }
    }
}

/** The actions a single item can carry its own speed bump for. */
enum class GuardAction(val key: String) {
    DONE("done"),
    SNOOZE("snooze"),
    RESCHEDULE("reschedule"),
}

/**
 * An item's own speed bumps, kept in one text column, for example
 * "done=confirm+path;snooze=wait10+math". An action that is not listed follows the
 * defaults from Settings.
 */
data class GuardOverrides(val byAction: Map<GuardAction, Guard> = emptyMap()) {

    operator fun get(action: GuardAction): Guard? = byAction[action]

    fun with(action: GuardAction, guard: Guard?): GuardOverrides =
        GuardOverrides(if (guard == null) byAction - action else byAction + (action to guard.clamped()))

    fun encode(): String? = byAction.entries
        .sortedBy { it.key.ordinal }
        .takeIf { it.isNotEmpty() }
        ?.joinToString(";") { "${it.key.key}=${it.value.encode()}" }

    companion object {
        fun decode(text: String?): GuardOverrides {
            if (text.isNullOrBlank()) return GuardOverrides()
            val map = text.split(';').mapNotNull { entry ->
                val parts = entry.split('=', limit = 2)
                if (parts.size != 2) return@mapNotNull null
                val action = GuardAction.entries.firstOrNull { it.key == parts[0] } ?: return@mapNotNull null
                val guard = Guard.decode(parts[1]) ?: return@mapNotNull null
                action to guard
            }.toMap()
            return GuardOverrides(map)
        }
    }
}

/** Every place in the app that has a default speed bump, with that default. */
enum class GuardSlot(
    val key: String,
    val group: String,
    val label: String,
    val default: Guard,
) {
    TODO_HIGH_DONE(
        "todo_high_done", "High-priority todos", "Marking it done",
        Guard(confirm = true, challenge = Challenge.PATH),
    ),
    TODO_HIGH_SNOOZE("todo_high_snooze", "High-priority todos", "Snoozing", Guard(challenge = Challenge.MATH)),
    TODO_HIGH_RESCHEDULE("todo_high_reschedule", "High-priority todos", "Rescheduling", Guard(challenge = Challenge.MATH)),
    TODO_MEDIUM_DONE("todo_medium_done", "Medium-priority todos", "Marking it done", Guard(challenge = Challenge.SLIDE)),
    TODO_MEDIUM_SNOOZE("todo_medium_snooze", "Medium-priority todos", "Snoozing", Guard(waitSeconds = 10)),
    TODO_MEDIUM_RESCHEDULE("todo_medium_reschedule", "Medium-priority todos", "Rescheduling", Guard(waitSeconds = 10)),
    ROUTINE_SNOOZE("routine_snooze", "Routines", "Snoozing the alarm", Guard(challenge = Challenge.MATH)),
    ROUTINE_RESCHEDULE("routine_reschedule", "Routines", "Rescheduling", Guard(challenge = Challenge.MATH)),
    ROUTINE_END("routine_end", "Routines", "Stopping a routine early", Guard(waitSeconds = 30)),
    FOCUS_END("focus_end", "Focus", "Ending a session early", Guard(waitSeconds = 30)),
    BLOCKER_EXCEPTION("blocker_exception", "Blocker", "A few more minutes in a blocked app", Guard(waitSeconds = 10)),
    BLOCKER_PAUSE("blocker_pause", "Blocker", "Pausing the blocker", Guard(waitSeconds = 10)),
}

/** The app-wide defaults, set under Settings. Per-item choices override them. */
data class GuardSettings(
    /** Master switch. Off means no speed bump anywhere, whatever an item says. */
    val enabled: Boolean = true,
    /** Defaults the user changed; a slot that is missing uses its built-in default. */
    val slots: Map<GuardSlot, Guard> = emptyMap(),
) {
    /** What the slot is set to, regardless of the master switch (for Settings). */
    fun configured(slot: GuardSlot): Guard = slots[slot] ?: slot.default

    /** What actually applies for the slot right now. */
    operator fun get(slot: GuardSlot): Guard = if (enabled) configured(slot) else Guard.NONE

    fun with(slot: GuardSlot, guard: Guard): GuardSettings = copy(slots = slots + (slot to guard.clamped()))

    /** The default for a todo of [priority]; low priority has none. */
    fun defaultFor(priority: Int, action: GuardAction): Guard =
        slotFor(priority, action)?.let { this[it] } ?: Guard.NONE

    fun forTodo(todo: TodoEntity, action: GuardAction): Guard {
        if (!enabled) return Guard.NONE
        return GuardOverrides.decode(todo.guards)[action] ?: defaultFor(todo.priority, action)
    }

    fun forRoutine(routine: RoutineEntity, action: GuardAction): Guard {
        if (!enabled) return Guard.NONE
        GuardOverrides.decode(routine.guards)[action]?.let { return it }
        return when (action) {
            GuardAction.SNOOZE -> this[GuardSlot.ROUTINE_SNOOZE]
            GuardAction.RESCHEDULE -> this[GuardSlot.ROUTINE_RESCHEDULE]
            GuardAction.DONE -> Guard.NONE
        }
    }

    companion object {
        fun slotFor(priority: Int, action: GuardAction): GuardSlot? = when (priority) {
            2 -> when (action) {
                GuardAction.DONE -> GuardSlot.TODO_HIGH_DONE
                GuardAction.SNOOZE -> GuardSlot.TODO_HIGH_SNOOZE
                GuardAction.RESCHEDULE -> GuardSlot.TODO_HIGH_RESCHEDULE
            }
            1 -> when (action) {
                GuardAction.DONE -> GuardSlot.TODO_MEDIUM_DONE
                GuardAction.SNOOZE -> GuardSlot.TODO_MEDIUM_SNOOZE
                GuardAction.RESCHEDULE -> GuardSlot.TODO_MEDIUM_RESCHEDULE
            }
            else -> null
        }
    }
}

/**
 * How persistently an unanswered todo comes back, per priority.
 *
 * A todo that is due rings, and if nobody answers it rings again every
 * [intervalMinutes] up to [maxRepeats] times. After that it rests as a plain
 * notification until the next daily reminder time starts a new round. The repeat
 * count is what keeps this a reminder and not an alarm that never ends.
 */
data class NagPolicy(
    val intervalMinutes: Int,
    val maxRepeats: Int,
) {
    companion object {
        val HIGH_DEFAULT = NagPolicy(intervalMinutes = 5, maxRepeats = 6)
        val MEDIUM_DEFAULT = NagPolicy(intervalMinutes = 30, maxRepeats = 2)
        val NONE = NagPolicy(intervalMinutes = 0, maxRepeats = 0)

        fun clamp(policy: NagPolicy): NagPolicy = NagPolicy(
            intervalMinutes = policy.intervalMinutes.coerceIn(
                ComplianceLimits.MIN_TODO_NAG_INTERVAL_MINUTES,
                ComplianceLimits.MAX_TODO_NAG_INTERVAL_MINUTES,
            ),
            maxRepeats = policy.maxRepeats.coerceIn(0, ComplianceLimits.MAX_TODO_NAG_REPEATS),
        )
    }
}
