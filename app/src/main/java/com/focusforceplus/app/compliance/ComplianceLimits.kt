package com.focusforceplus.app.compliance

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Central, hard-coded compliance limits for FocusForce+.
 *
 * These values back the Invincible Mode and Tamper Protection features and exist so a Play Store
 * reviewer never sees a configuration that looks like a user could realistically lock themselves
 * out of their device or commit to abusive durations. The limits are intentionally not user-
 * configurable beyond what is exposed here — anyone wanting harder caps is outside the target
 * audience, and Play Store policy compliance ranks above that minority feature wish.
 *
 * Source of truth: `.claude/PLAY_STORE_COMPLIANCE.md`, section 2.3 (Policy-Safe Limits).
 *
 * All time values use [kotlin.time.Duration] for Kotlin-idiomatic interop.
 */
object ComplianceLimits {

    /**
     * Maximum duration a single routine run may stay locked under Invincible Mode.
     *
     * Four hours is unambiguously a "long focus session" rather than a coercive lock-in.
     * Reviewers comparing against typical productivity / focus apps see no abuse pattern,
     * and the cap prevents pathological configurations like "24 h cleaning routine".
     */
    val MAX_ROUTINE_DURATION: Duration = 4.hours

    /**
     * Maximum duration a single focus session may stay locked under Invincible Mode.
     *
     * Mirrors [MAX_ROUTINE_DURATION] for the same reasoning: a four-hour session is a
     * recognisable deep-work block, while anything longer would read as locking the user
     * out for an unhealthy stretch of time.
     */
    val MAX_FOCUS_SESSION_DURATION: Duration = 4.hours

    /**
     * Maximum duration of a single app-blocking window (e.g. 09:00–21:00 work day).
     *
     * Twelve hours is the longest window that still maps to an obvious real-world use
     * case ("workday") that any reviewer will recognise. Anything beyond would start
     * to look like 24/7 lockout territory and trigger Device-and-Network-Abuse review.
     */
    val MAX_APP_BLOCK_WINDOW: Duration = 12.hours

    /**
     * Minimum length of the daily Tamper Protection change window.
     *
     * Ten minutes is the smallest window where the user can realistically reach their
     * phone, open Settings, and make a change without missing the slot. Below this it
     * would be effectively unhittable, which would read as a dark pattern (locking
     * settings under the guise of a window that never opens in practice).
     */
    val MIN_TP_WINDOW: Duration = 10.minutes

    /**
     * Maximum length of the daily Tamper Protection change window.
     *
     * One hour caps the window at "occasional, deliberate maintenance", well short of
     * "always open and therefore meaningless". A larger window would defeat the
     * commitment value of Tamper Protection without adding compliance headroom.
     */
    val MAX_TP_WINDOW: Duration = 60.minutes

    /**
     * Minimum frequency at which a Tamper Protection change window must reoccur.
     *
     * The window must open at least once every 24 hours. This guarantees the user
     * always has a daily route back to their settings without uninstalling, which is
     * the critical compliance property: Tamper Protection adds friction, not a trap.
     * Anything sparser (e.g. weekly) would push the feature toward "lock-in" territory
     * that Google explicitly disallows.
     */
    val MIN_TP_WINDOW_FREQUENCY: Duration = 24.hours

    /**
     * Maximum number of snoozes the user may apply to a single routine occurrence.
     *
     * Bounded so the alarm-style reminder system cannot be perpetually deferred,
     * which would both undermine the routine's purpose and create a "nag forever"
     * pattern that reviewers can flag under deceptive-behaviour policies.
     */
    const val MAX_SNOOZES_PER_ROUTINE: Int = 2

    // ── Todos ─────────────────────────────────────────────────────────────────

    /**
     * Highest snooze cap the user may configure on a single high-priority todo.
     *
     * High-priority todos are the todo module's lock-like feature (non-dismissable
     * alarm + pinned notification). The cap keeps every configuration a reviewer can
     * construct visibly bounded: after at most this many snoozes the alarm reaches
     * its terminal state and the remaining paths are Done, Reschedule (also bounded),
     * or editing the todo inside the app — the data itself is never locked.
     */
    const val MAX_TODO_SNOOZE_LIMIT: Int = 5

    /**
     * Highest reschedule cap the user may configure on a single high-priority todo.
     * Same rationale as [MAX_TODO_SNOOZE_LIMIT].
     */
    const val MAX_TODO_RESCHEDULE_LIMIT: Int = 5

    /**
     * Fixed snooze cap for low/medium-priority todos (not user-configurable).
     * Mirrors [MAX_SNOOZES_PER_ROUTINE] so both modules present the same commitment
     * baseline.
     */
    const val DEFAULT_TODO_MAX_SNOOZES: Int = 2

    /**
     * Sentinel for "reschedules are not limited" on low/medium-priority todos.
     *
     * Unlimited is compliant here because those priorities always keep a Dismiss
     * action: rescheduling grants the user *more* freedom, not less, so there is no
     * lock to bound. High priority never uses this sentinel.
     */
    const val TODO_UNLIMITED_RESCHEDULES: Int = Int.MAX_VALUE
}
