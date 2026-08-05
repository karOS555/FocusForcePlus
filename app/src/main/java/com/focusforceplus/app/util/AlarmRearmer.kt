package com.focusforceplus.app.util

import com.focusforceplus.app.data.repository.FocusRepository
import com.focusforceplus.app.data.repository.RoutineRepository
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.data.repository.SleepRepository
import com.focusforceplus.app.data.repository.TodoRepository
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
    private val todoRepository: TodoRepository,
    private val focusRepository: FocusRepository,
    private val settingsRepository: SettingsRepository,
    private val sleepRepository: SleepRepository,
    private val alarmHelper: AlarmHelper,
    private val todoAlarmHelper: TodoAlarmHelper,
    private val focusAlarmHelper: FocusAlarmHelper,
    private val sleepAlarmHelper: SleepAlarmHelper,
    private val sleepStateReconciler: SleepStateReconciler,
) {
    suspend fun rearmAll() {
        runCatching {
            alarmHelper.rescheduleAll(routineRepository.getAllRoutines().first())
        }
        runCatching {
            val digestTimes = settingsRepository.digestTimes.first()
            todoAlarmHelper.rescheduleAll(todoRepository.getAllTodos().first(), digestTimes)
        }
        runCatching {
            focusAlarmHelper.rescheduleAll(focusRepository.getAllSessions().first())
        }
        runCatching {
            sleepAlarmHelper.scheduleAll(sleepRepository.schedule.first())
        }
        // A reboot during the night can swallow the wake-up; make sure DND and the
        // sleep notification don't outlive the night they belong to.
        runCatching { sleepStateReconciler.reconcile() }
    }
}
