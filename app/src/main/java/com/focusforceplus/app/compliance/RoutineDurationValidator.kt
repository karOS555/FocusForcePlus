package com.focusforceplus.app.compliance

/**
 * Validates that a routine's combined task duration stays within
 * [ComplianceLimits.MAX_ROUTINE_DURATION].
 *
 * Returns a user-facing error message when the limit is exceeded, or null when the
 * routine is acceptable. The message intentionally explains the compliance rationale
 * so the cap does not read as an arbitrary product decision — see
 * `.claude/PLAY_STORE_COMPLIANCE.md`, section 2.3.
 */
fun validateRoutineDuration(totalDurationMinutes: Int): String? {
    val maxMinutes = ComplianceLimits.MAX_ROUTINE_DURATION.inWholeMinutes.toInt()
    if (totalDurationMinutes <= maxMinutes) return null

    val maxHours = ComplianceLimits.MAX_ROUTINE_DURATION.inWholeHours.toInt()
    return "This routine totals $totalDurationMinutes minutes, which exceeds the " +
            "$maxHours-hour cap for a single routine. The cap keeps Invincible Mode " +
            "compliant with Google Play policies — longer lock-ins risk being flagged " +
            "as coercive lockout. Please shorten or split the routine."
}
