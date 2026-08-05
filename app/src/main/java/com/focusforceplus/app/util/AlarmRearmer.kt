package com.focusforceplus.app.util

import com.focusforceplus.app.data.repository.FocusRepository
import com.focusforceplus.app.data.repository.RoutineRepository
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.data.repository.TodoRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Re-arms every scheduled alarm in the app (routines, todos incl. digest slots,
 * focus reminders). One code path shared by everything that can invalidate
 * alarms: reboot, app update, wall-clock/timezone changes, and the periodic
 * [com.focusforceplus.app.worker.AlarmGuardWorker] safety net against OEM
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
    private val alarmHelper: AlarmHelper,
    private val todoAlarmHelper: TodoAlarmHelper,
    private val focusAlarmHelper: FocusAlarmHelper,
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
    }
}
