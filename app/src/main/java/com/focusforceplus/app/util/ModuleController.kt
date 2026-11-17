package com.focusforceplus.app.util

import com.focusforceplus.app.compliance.TamperProtectionGuard
import com.focusforceplus.app.compliance.dayCodeFor
import com.focusforceplus.app.compliance.isRuleLocked
import com.focusforceplus.app.compliance.isSleepWindowActive
import com.focusforceplus.app.data.repository.BlockerRepository
import com.focusforceplus.app.data.repository.FocusRepository
import com.focusforceplus.app.data.repository.NavRepository
import com.focusforceplus.app.data.repository.RoutineRepository
import com.focusforceplus.app.data.repository.SleepRepository
import com.focusforceplus.app.service.ActiveRoutineRegistry
import com.focusforceplus.app.service.FocusSessionRegistry
import com.focusforceplus.app.ui.navigation.AppModule
import kotlinx.coroutines.flow.first
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Switches whole modules on and off.
 *
 * Off means: gone from the bar and the home screen, and nothing of that module
 * fires in the background any more. The data stays untouched, and switching the
 * module back on re-arms everything.
 *
 * Switching off must never become a side door around a commitment, so it follows
 * the same rules as the module's own controls: not while a session is running, and
 * for the blocker not while a rule is locked and only inside the Tamper Protection
 * window when invincible rules exist.
 */
@Singleton
class ModuleController @Inject constructor(
    private val navRepository: NavRepository,
    private val routineRepository: RoutineRepository,
    private val focusRepository: FocusRepository,
    private val blockerRepository: BlockerRepository,
    private val sleepRepository: SleepRepository,
    private val alarmHelper: AlarmHelper,
    private val focusAlarmHelper: FocusAlarmHelper,
    private val sleepAlarmHelper: SleepAlarmHelper,
    private val todoReminders: TodoReminderEngine,
    private val alarmRearmer: AlarmRearmer,
    private val activeRoutineRegistry: ActiveRoutineRegistry,
    private val focusSessionRegistry: FocusSessionRegistry,
    private val tamperGuard: TamperProtectionGuard,
) {
    /** Returns null on success, or the reason the change was refused. */
    suspend fun setModuleOn(module: AppModule, on: Boolean): String? {
        if (!module.isFeature) return null
        if (on) {
            navRepository.setOff(module, false)
            alarmRearmer.rearmAll()
            return null
        }
        refusalToTurnOff(module)?.let { return it }
        navRepository.setOff(module, true)
        pause(module)
        return null
    }

    private suspend fun refusalToTurnOff(module: AppModule): String? = when (module) {
        AppModule.ROUTINES ->
            if (activeRoutineRegistry.activeRoutineId.value != null) {
                "A routine is running. Finish it before switching routines off."
            } else null

        AppModule.FOCUS ->
            if (focusSessionRegistry.activeSession.value != null) {
                "A focus session is running. Let it end before switching focus off."
            } else null

        AppModule.BLOCKER -> blockerRefusal()

        AppModule.SLEEP -> {
            val schedule = sleepRepository.schedule.first()
            val cal = Calendar.getInstance()
            val minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            val running = schedule.enabled && isSleepWindowActive(
                schedule.bedtimeMinutes, schedule.wakeMinutes, schedule.activeDaysCsv,
                minutes, dayCodeFor(cal.get(Calendar.DAY_OF_WEEK)),
            )
            if (running) "Sleep mode is running right now. Switch it off after wake-up." else null
        }

        else -> null
    }

    private suspend fun blockerRefusal(): String? {
        val cal = Calendar.getInstance()
        val minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val rules = blockerRepository.getAllAppsOnce()
        val locked = rules.count { isRuleLocked(it, minutes) }
        if (locked > 0) {
            return "$locked blocking rule(s) are locked by Invincible Mode right now. " +
                "They release at midnight or at the end of their window."
        }
        if (rules.any { it.invincibleMode && it.isBlocked }) {
            val verdict = tamperGuard.checkProtectedChange()
            if (!verdict.allowed) return verdict.message
        }
        return null
    }

    private suspend fun pause(module: AppModule) {
        when (module) {
            AppModule.ROUTINES -> runCatching {
                routineRepository.getAllRoutines().first().forEach { alarmHelper.cancelRoutineAlarms(it.id) }
            }
            AppModule.TODOS -> runCatching { todoReminders.pauseAll() }
            AppModule.FOCUS -> runCatching {
                focusRepository.getAllSessions().first().forEach { focusAlarmHelper.cancelSessionAlarms(it.id) }
            }
            AppModule.SLEEP -> runCatching { sleepAlarmHelper.cancelAll() }
            else -> Unit
        }
    }
}
