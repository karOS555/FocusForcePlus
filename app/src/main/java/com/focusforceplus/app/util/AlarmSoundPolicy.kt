package com.focusforceplus.app.util

/**
 * Process-wide snapshot of the global "Alarm sound" / "Alarm vibration" settings.
 *
 * [FocusForcePlusApp] keeps these fields in sync with the DataStore flows; alarm
 * surfaces (alarm activities, task-complete overlay) read them synchronously right
 * before playing sound or vibrating — those call sites cannot suspend.
 * Defaults are `true` so a cold start before the first sync errs on the loud side,
 * which is the safe direction for an alarm app.
 */
object AlarmSoundPolicy {
    @Volatile var soundEnabled: Boolean = true
    @Volatile var vibrationEnabled: Boolean = true
}
