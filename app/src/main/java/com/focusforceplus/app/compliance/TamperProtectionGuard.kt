package com.focusforceplus.app.compliance

import android.os.SystemClock
import com.focusforceplus.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runtime gate for Tamper Protection. Every protection-weakening change (turning
 * an Invincible flag off, weakening an invincible blocker rule, disabling the
 * blocker master switch, toggling TP itself) asks this guard first.
 *
 * Time-manipulation handling (Golden Rule #12): before trusting the wall clock,
 * it is validated against the monotonic clock via a persisted anchor. A plausible
 * reading re-anchors (sliding anchor); an implausible one is treated as "outside
 * the window" with an honest message. Reboots reset the anchor — see
 * [isWallClockPlausible] for why that is acceptable friction.
 */
@Singleton
class TamperProtectionGuard @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    data class Verdict(val allowed: Boolean, val message: String? = null)

    suspend fun currentConfig(): TamperProtectionConfig = TamperProtectionConfig(
        enabled = settingsRepository.tpEnabled.first(),
        windowStartMinutes = settingsRepository.tpWindowStartMinutes.first(),
        windowDurationMinutes = settingsRepository.tpWindowDurationMinutes.first(),
    )

    /** May a protection-weakening change happen right now? */
    suspend fun checkProtectedChange(): Verdict {
        val config = currentConfig()
        if (!config.enabled) return Verdict(allowed = true)

        if (!validateAndRefreshClock()) {
            return Verdict(
                allowed = false,
                message = "Tamper Protection: a system-time change was detected, so the " +
                    "change window cannot be trusted right now. It re-syncs on its own " +
                    "as real time passes.",
            )
        }

        return if (isInTpWindow(config, minutesOfDayNow())) {
            Verdict(allowed = true)
        } else {
            Verdict(
                allowed = false,
                message = "Tamper Protection: this change is only possible during your " +
                    "daily window (${formatTpWindow(config.windowStartMinutes, config.windowDurationMinutes)}).",
            )
        }
    }

    /**
     * Enables or disables Tamper Protection itself. Per the compliance guide BOTH
     * directions only work inside the window — once on, it cannot be panic-toggled
     * off, and enabling forces the user to prove the window is actually reachable.
     */
    suspend fun trySetEnabled(target: Boolean): Verdict {
        val config = currentConfig()
        if (config.enabled == target) return Verdict(allowed = true)

        validateTpWindowDuration(config.windowDurationMinutes)?.let {
            return Verdict(allowed = false, message = it)
        }
        if (!validateAndRefreshClock()) {
            return Verdict(
                allowed = false,
                message = "Tamper Protection: a system-time change was detected. Try again " +
                    "once real time has passed.",
            )
        }
        // Evaluate against the configured window regardless of the current enabled
        // state — the window is the deliberate moment for both arming and disarming.
        val inWindow = isInBlockWindow(
            minutesOfDayNow(),
            config.windowStartMinutes,
            (config.windowStartMinutes + config.windowDurationMinutes) % (24 * 60),
        )
        if (!inWindow) {
            return Verdict(
                allowed = false,
                message = "Tamper Protection can only be turned ${if (target) "on" else "off"} " +
                    "inside its daily window " +
                    "(${formatTpWindow(config.windowStartMinutes, config.windowDurationMinutes)}). " +
                    "Adjust the window first if it never suits you.",
            )
        }
        settingsRepository.saveTpEnabled(target)
        anchorNow()
        return Verdict(allowed = true)
    }

    /**
     * Window configuration changes are themselves protected while TP is enabled
     * (otherwise moving the window would be the trivial bypass).
     */
    suspend fun trySetWindow(startMinutes: Int, durationMinutes: Int): Verdict {
        validateTpWindowDuration(durationMinutes)?.let {
            return Verdict(allowed = false, message = it)
        }
        val gate = checkProtectedChange()
        if (!gate.allowed) return gate
        settingsRepository.saveTpWindow(startMinutes, durationMinutes)
        return Verdict(allowed = true)
    }

    /** True when the wall clock is currently trustworthy; re-anchors when it is. */
    private suspend fun validateAndRefreshClock(): Boolean {
        val (anchorWall, anchorElapsed) = settingsRepository.tpClockAnchor.first()
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val plausible = isWallClockPlausible(anchorWall, anchorElapsed, nowWall, nowElapsed)
        if (plausible) anchorNow()
        return plausible
    }

    private suspend fun anchorNow() {
        settingsRepository.saveTpClockAnchor(System.currentTimeMillis(), SystemClock.elapsedRealtime())
    }

    private fun minutesOfDayNow(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }
}
