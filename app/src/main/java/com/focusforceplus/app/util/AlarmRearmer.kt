package com.focusforceplus.app.util

import com.focusforceplus.app.data.repository.FocusRepository
import com.focusforceplus.app.data.repository.NavRepository
import com.focusforceplus.app.data.repository.RoutineRepository
import com.focusforceplus.app.data.repository.SleepRepository
import com.focusforceplus.app.ui.navigation.AppModule
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Re-arms every scheduled alarm in the app (routines, todos incl. digest slots,
 * focus reminders, the sleep schedule). One code path shared by everything that
 * can invalidate alarms: reboot, app update, wall-clock/timezone changes, and the
 * periodic [com.focusforceplus.app.worker.AlarmGuardWorker] safety net against OEM
 * battery managers silently purging alarms.
 *
 * Each block is isolated in runCatching so one failing module never takes the
 * other alarms down with it.
 */
@Singleton
class AlarmRearmer @Inject constructor(
    private val routineRepository: RoutineRepository,
    private val focusRepository: FocusRepository,
    private val sleepRepository: SleepRepository,
    private val navRepository: NavRepository,
    private val alarmHelper: AlarmHelper,
    private val todoReminders: TodoReminderEngine,
    private val focusAlarmHelper: FocusAlarmHelper,
    private val sleepAlarmHelper: SleepAlarmHelper,
    private val sleepStateReconciler: SleepStateReconciler,
) {
    suspend fun rearmAll() {
        // A module the user switched off stays quiet: its alarms are not re-armed
        // until it is switched back on (which calls this again).
        val nav = runCatching { navRepository.current() }.getOrNull()
        fun on(module: AppModule) = nav?.isOn(module) ?: true

        if (on(AppModule.ROUTINES)) runCatching {
            alarmHelper.rescheduleAll(routineRepository.getAllRoutines().first())
        }
        // The engine checks the module switch itself and clears everything if it is off.
        runCatching { todoReminders.syncAll() }
        if (on(AppModule.FOCUS)) runCatching {
            focusAlarmHelper.rescheduleAll(focusRepository.getAllSessions().first())
        }
        if (on(AppModule.SLEEP)) runCatching {
            sleepAlarmHelper.scheduleAll(sleepRepository.schedule.first())
        }
        // A reboot during the night can swallow the wake-up; make sure DND and the
        // sleep notification don't outlive the night they belong to.
        runCatching { sleepStateReconciler.reconcile() }
    }
}
