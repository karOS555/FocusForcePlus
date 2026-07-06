package com.focusforceplus.app.compliance

/**
 * Validates a focus session duration against [ComplianceLimits.MAX_FOCUS_SESSION_DURATION].
 * Same pattern and rationale as [validateRoutineDuration].
 */
fun validateFocusDuration(durationMinutes: Int): String? {
    if (durationMinutes < 1) return "Session duration must be at least 1 minute."
    val maxMinutes = ComplianceLimits.MAX_FOCUS_SESSION_DURATION.inWholeMinutes.toInt()
    if (durationMinutes <= maxMinutes) return null

    val maxHours = ComplianceLimits.MAX_FOCUS_SESSION_DURATION.inWholeHours.toInt()
    return "Focus sessions are capped at $maxHours hours. The cap keeps Invincible Mode " +
            "compliant with Google Play policies — longer locked sessions risk being " +
            "flagged as coercive lockout. Split longer work into multiple sessions."
}
