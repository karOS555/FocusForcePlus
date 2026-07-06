package com.focusforceplus.app.compliance

import com.focusforceplus.app.data.db.entity.BlockedAppEntity
import com.focusforceplus.app.data.db.entity.BlockerGroupEntity

/**
 * Why a blocked-app screen is being shown. Also drives the copy on that screen.
 */
enum class BlockReason {
    /** Ungrouped rule with neither limit nor window → blocked whenever enabled. */
    MANUAL,
    /** Daily usage limit reached; unblocks at midnight. */
    DAILY_LIMIT,
    /** The app's group reached its shared daily limit; unblocks at midnight. */
    GROUP_LIMIT,
    /** Inside the configured blocking time window. */
    TIME_WINDOW,
    /** A routine with app blocking is currently running. */
    ROUTINE_ACTIVE,
    /** A focus session with app blocking is currently running. */
    FOCUS_ACTIVE,
}

/** Maximum temporary exceptions per app per day ("5 more minutes"). */
const val MAX_BLOCK_EXCEPTIONS_PER_DAY: Int = 2

/** Length of one temporary exception in milliseconds. */
const val BLOCK_EXCEPTION_DURATION_MILLIS: Long = 5 * 60_000L

/**
 * Packages that must never be blocked. Blocking Android's own Settings app would
 * violate Golden Rule #2 (never obstruct the user's path to system settings); the
 * dialer stays reachable for emergencies; blocking ourselves would brick the UI.
 * The default launcher is excluded dynamically (see AppBlockerAccessibilityService).
 */
val NEVER_BLOCK_PACKAGES: Set<String> = setOf(
    "com.focusforceplus.app",
    "com.android.settings",
    "com.android.dialer",
    "com.google.android.dialer",
    "com.android.emergency",
    "com.android.systemui",
)

/**
 * Whether [minutesOfDay] falls inside the window [startMinutes, endMinutes).
 * Windows may wrap past midnight (e.g. 22:00-06:00 → start > end).
 */
fun isInBlockWindow(minutesOfDay: Int, startMinutes: Int, endMinutes: Int): Boolean =
    if (startMinutes <= endMinutes) {
        minutesOfDay in startMinutes until endMinutes
    } else {
        minutesOfDay >= startMinutes || minutesOfDay < endMinutes
    }

/** Window length in minutes, wrap-around aware. start == end yields 0 (invalid). */
fun blockWindowLengthMinutes(startMinutes: Int, endMinutes: Int): Int =
    ((endMinutes - startMinutes) + 24 * 60) % (24 * 60)

/**
 * Evaluates whether [app] should be blocked right now, and why.
 *
 * Group semantics: apps that belong to a group are governed by the group's shared
 * limit ([groupLimitReached]) plus their own limit/window — the plain "always
 * blocked" mode only applies to ungrouped rules ([inGroup] = false). Grouping an
 * app therefore never silently hard-blocks it.
 *
 * @param nowMillis          wall-clock time
 * @param minutesOfDay       minutes since local midnight (0..1439)
 * @param routineBlocking    a routine with app blocking enabled is running AND the
 *                           global "block during routines" setting is on
 * @param focusBlocking      same for focus sessions (already narrowed to the
 *                           session's selected groups by the caller)
 * @param inGroup            the app belongs to a blocker group
 * @param groupLimitReached  that group's shared daily limit is used up
 * @return the reason to block, or null if the app is allowed
 */
fun evaluateBlock(
    app: BlockedAppEntity,
    nowMillis: Long,
    minutesOfDay: Int,
    routineBlocking: Boolean,
    focusBlocking: Boolean,
    inGroup: Boolean = false,
    groupLimitReached: Boolean = false,
): BlockReason? {
    if (app.packageName in NEVER_BLOCK_PACKAGES) return null
    if (!app.isBlocked) return null
    if (app.exceptionUntilMillis > nowMillis) return null

    // Session-driven blocking applies regardless of limit/window state.
    if (routineBlocking && app.blockDuringRoutines) return BlockReason.ROUTINE_ACTIVE
    if (focusBlocking && app.blockDuringFocus) return BlockReason.FOCUS_ACTIVE

    if (groupLimitReached) return BlockReason.GROUP_LIMIT

    val hasLimit = app.dailyLimitMinutes != null
    val hasWindow = app.windowStartMinutes != null && app.windowEndMinutes != null

    if (hasLimit && app.usedTodayMinutes >= app.dailyLimitMinutes!!) return BlockReason.DAILY_LIMIT
    if (hasWindow && isInBlockWindow(minutesOfDay, app.windowStartMinutes!!, app.windowEndMinutes!!)) {
        return BlockReason.TIME_WINDOW
    }
    if (!hasLimit && !hasWindow && !inGroup) return BlockReason.MANUAL

    return null
}

/**
 * Invincible state machine for groups: locked only while the shared daily limit is
 * reached (natural end: midnight). While locked, the group must not be weakened
 * (limit raised/removed, invincible off, members removed, group deleted) and its
 * members must not be freed. A group without a shared limit never locks.
 */
fun isGroupLocked(group: BlockerGroupEntity, membersUsedMinutes: Int): Boolean {
    if (!group.invincibleMode) return false
    val limit = group.sharedDailyLimitMinutes ?: return false
    return membersUsedMinutes >= limit
}

/**
 * Whether this rule is currently in its Invincible-locked ACTIVE state.
 *
 * State machine (`.claude/PLAY_STORE_COMPLIANCE.md` section 2.1): the lock arms only
 * while the rule actively blocks due to a *bounded* condition — daily limit reached
 * (natural end: midnight) or inside its time window (natural end: window end, capped
 * at 12h). A plain manual block never locks: it has no natural end, so an invincible
 * manual block would be an indefinite lock-in, which the policy forbids. While
 * locked, the rule must not be disabled, deleted, weakened, or have Invincible Mode
 * turned off.
 */
fun isRuleLocked(app: BlockedAppEntity, minutesOfDay: Int): Boolean {
    if (!app.invincibleMode || !app.isBlocked) return false
    val limitReached = app.dailyLimitMinutes != null && app.usedTodayMinutes >= app.dailyLimitMinutes
    val inWindow = app.windowStartMinutes != null && app.windowEndMinutes != null &&
            isInBlockWindow(minutesOfDay, app.windowStartMinutes, app.windowEndMinutes)
    return limitReached || inWindow
}

/**
 * Whether the blocked screen may offer the "5 more minutes" exception for [reason].
 *
 * Not offered when the rule itself is invincible-locked, when the app's group is
 * invincible-locked, when the block comes from an invincible session (that
 * session's own lock governs), or when today's budget
 * ([MAX_BLOCK_EXCEPTIONS_PER_DAY]) is used up.
 */
fun canGrantException(
    app: BlockedAppEntity,
    reason: BlockReason,
    minutesOfDay: Int,
    sessionIsInvincible: Boolean,
    groupIsLocked: Boolean = false,
): Boolean {
    if (app.exceptionsUsedToday >= MAX_BLOCK_EXCEPTIONS_PER_DAY) return false
    if (isRuleLocked(app, minutesOfDay)) return false
    if (groupIsLocked) return false
    if ((reason == BlockReason.ROUTINE_ACTIVE || reason == BlockReason.FOCUS_ACTIVE) && sessionIsInvincible) {
        return false
    }
    return true
}

/**
 * Validates a blocking window; returns a user-facing error or null when acceptable.
 * Overnight windows (start > end, e.g. 22:00-06:00) are supported. Windows are
 * capped at [ComplianceLimits.MAX_APP_BLOCK_WINDOW] (12h) so no rule can be
 * configured into near-permanent lockout territory.
 */
fun validateBlockWindow(startMinutes: Int, endMinutes: Int): String? {
    val length = blockWindowLengthMinutes(startMinutes, endMinutes)
    if (length == 0) {
        return "The window start and end must be different times."
    }
    val maxMinutes = ComplianceLimits.MAX_APP_BLOCK_WINDOW.inWholeMinutes.toInt()
    if (length > maxMinutes) {
        val maxHours = ComplianceLimits.MAX_APP_BLOCK_WINDOW.inWholeHours.toInt()
        return "Blocking windows are capped at $maxHours hours to stay compliant with " +
                "Google Play policies — longer windows would look like permanent lockout."
    }
    return null
}
