package com.focusforceplus.app.compliance

import com.focusforceplus.app.data.db.entity.BlockedAppEntity
import com.focusforceplus.app.data.db.entity.BlockerGroupEntity
import kotlin.math.abs

/**
 * Tamper Protection (guide section 2.2): the meta-lock that keeps the user from
 * impulsively disabling their own Invincible-Mode settings. While enabled,
 * protection-weakening changes are only possible inside a self-chosen daily
 * window. It is a pure in-app settings lock — no Device Admin, no uninstall
 * blocking, no reaction to system settings (Golden Rules #1, #2, #9).
 */
data class TamperProtectionConfig(
    val enabled: Boolean,
    /** Daily window start, minutes since midnight. */
    val windowStartMinutes: Int,
    /** Window length in minutes, clamped to [ComplianceLimits.MIN_TP_WINDOW]..[ComplianceLimits.MAX_TP_WINDOW]. */
    val windowDurationMinutes: Int,
)

/** Whether protected settings may be changed right now. Without TP: always. */
fun isInTpWindow(config: TamperProtectionConfig, minutesOfDay: Int): Boolean {
    if (!config.enabled) return true
    val start = config.windowStartMinutes
    val end = (start + config.windowDurationMinutes) % (24 * 60)
    return isInBlockWindow(minutesOfDay, start, end)
}

/** Validates the window length against the compliance bounds (10-60 min). */
fun validateTpWindowDuration(minutes: Int): String? {
    val min = ComplianceLimits.MIN_TP_WINDOW.inWholeMinutes.toInt()
    val max = ComplianceLimits.MAX_TP_WINDOW.inWholeMinutes.toInt()
    return when {
        minutes < min -> "The change window must be at least $min minutes — shorter would be " +
            "practically unhittable, which Google Play treats as a dark pattern."
        minutes > max -> "The change window is capped at $max minutes — longer and the " +
            "protection stops meaning anything."
        else -> null
    }
}

/**
 * Clock-manipulation guard (Golden Rule #12): compares how much wall-clock time
 * passed against how much real (monotonic) time passed since the stored anchor.
 *
 * Returns true when the wall clock looks honest. After a reboot the monotonic
 * clock restarts (nowElapsed < anchorElapsed) and no comparison is possible —
 * that case is accepted and the caller re-anchors; a reboot per bypass attempt
 * is exactly the kind of friction this feature is meant to provide, not prevent.
 */
fun isWallClockPlausible(
    anchorWallMillis: Long,
    anchorElapsedMillis: Long,
    nowWallMillis: Long,
    nowElapsedMillis: Long,
    toleranceMillis: Long = 10 * 60_000L,
): Boolean {
    if (anchorWallMillis <= 0L) return true // no anchor yet
    if (nowElapsedMillis < anchorElapsedMillis) return true // reboot — cannot validate
    val wallDelta = nowWallMillis - anchorWallMillis
    val elapsedDelta = nowElapsedMillis - anchorElapsedMillis
    return abs(wallDelta - elapsedDelta) <= toleranceMillis
}

/** "08:00-08:30" style label for the daily window. */
fun formatTpWindow(startMinutes: Int, durationMinutes: Int): String {
    val end = (startMinutes + durationMinutes) % (24 * 60)
    return "%02d:%02d-%02d:%02d".format(startMinutes / 60, startMinutes % 60, end / 60, end % 60)
}

/**
 * Whether saving [new] over [old] weakens an invincible blocker rule — the set of
 * changes Tamper Protection gates for the blocker: turning the rule or its
 * invincibility off, raising/removing the daily limit, or removing/shortening the
 * blocking window. Strengthening is always allowed.
 */
fun weakensInvincibleRule(old: BlockedAppEntity, new: BlockedAppEntity): Boolean {
    if (!old.invincibleMode) return false
    if (!new.invincibleMode || !new.isBlocked) return true

    val oldLimit = old.dailyLimitMinutes
    val newLimit = new.dailyLimitMinutes
    if (oldLimit != null && (newLimit == null || newLimit > oldLimit)) return true

    val oldHasWindow = old.windowStartMinutes != null && old.windowEndMinutes != null
    val newHasWindow = new.windowStartMinutes != null && new.windowEndMinutes != null
    if (oldHasWindow) {
        if (!newHasWindow) return true
        val oldLen = blockWindowLengthMinutes(old.windowStartMinutes!!, old.windowEndMinutes!!)
        val newLen = blockWindowLengthMinutes(new.windowStartMinutes!!, new.windowEndMinutes!!)
        if (newLen < oldLen) return true
    }
    return false
}

/**
 * Whether saving [new] over [old] weakens an invincible blocker group: turning
 * invincibility off, or raising/removing the shared daily limit. Membership
 * removal and group deletion are gated separately at the call sites.
 */
fun weakensInvincibleGroup(old: BlockerGroupEntity, new: BlockerGroupEntity): Boolean {
    if (!old.invincibleMode) return false
    if (!new.invincibleMode) return true
    val oldLimit = old.sharedDailyLimitMinutes
    val newLimit = new.sharedDailyLimitMinutes
    if (oldLimit != null && (newLimit == null || newLimit > oldLimit)) return true
    return false
}
