package com.focusforceplus.app.ui.alarm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.data.repository.RoutineRepository
import com.focusforceplus.app.util.AlarmHelper
import com.focusforceplus.app.util.NotificationHelper
import com.focusforceplus.app.util.PendingRescheduleTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class AlarmUiState(
    val routineName: String = "",
    val routineDescription: String? = null,
    val routineIconKey: String? = null,
    val invincibleMode: Boolean = false,
    val maxSnoozeCount: Int = 2,
    val maxRescheduleCount: Int = 1,
    val snoozeCount: Int = 0,
    val rescheduleCount: Int = 0,
    val isLoading: Boolean = true,
) {
    val canSnooze: Boolean get() = snoozeCount < maxSnoozeCount
    val canReschedule: Boolean get() = rescheduleCount < maxRescheduleCount
    val remainingSnoozes: Int get() = maxSnoozeCount - snoozeCount
    val remainingReschedules: Int get() = maxRescheduleCount - rescheduleCount
}

@HiltViewModel
class RoutineAlarmViewModel @Inject constructor(
    private val repository: RoutineRepository,
    private val alarmHelper: AlarmHelper,
    private val notificationHelper: NotificationHelper,
    private val rescheduleTracker: PendingRescheduleTracker,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val routineId: Long = savedStateHandle.get<Long>("routineId") ?: 0L
    private val initialSnoozeCount: Int = savedStateHandle.get<Int>("snoozeCount") ?: 0

    private val _uiState = MutableStateFlow(AlarmUiState())
    val uiState: StateFlow<AlarmUiState> = _uiState.asStateFlow()

    init { loadRoutine() }

    private fun loadRoutine() {
        viewModelScope.launch {
            val routine = repository.getRoutineById(routineId).first() ?: return@launch
            _uiState.update {
                it.copy(
                    routineName        = routine.name,
                    routineDescription = routine.description,
                    routineIconKey     = routine.iconKey,
                    invincibleMode     = routine.invincibleMode,
                    maxSnoozeCount     = routine.maxSnoozeCount,
                    maxRescheduleCount = routine.maxRescheduleCount,
                    snoozeCount        = initialSnoozeCount,
                    isLoading          = false,
                )
            }
        }
    }

    fun snooze(minutes: Int) {
        val s = _uiState.value
        if (!s.canSnooze) return
        val newSnoozeCount = s.snoozeCount + 1
        _uiState.update { it.copy(snoozeCount = newSnoozeCount) }
        viewModelScope.launch {
            val routine = repository.getRoutineById(routineId).first() ?: return@launch
            val triggerMillis = System.currentTimeMillis() + minutes * 60_000L

            alarmHelper.cancelRoutineAlarms(routineId)
            alarmHelper.scheduleRoutineAlarms(routine)
            alarmHelper.scheduleSnoozeAlarm(routineId, routine.name, minutes)

            // Replace alarm notification with a snooze countdown notification.
            notificationHelper.cancelNotification(NotificationHelper.alarmNotificationId(routineId))
            notificationHelper.showNotification(
                NotificationHelper.alarmNotificationId(routineId),
                notificationHelper.buildSnoozedNotification(
                    routineId      = routineId,
                    routineName    = routine.name,
                    triggerMillis  = triggerMillis,
                    snoozeCount    = newSnoozeCount,
                    maxSnoozeCount = routine.maxSnoozeCount,
                    invincibleMode = routine.invincibleMode,
                ),
            )
        }
    }

    fun reschedule(hour: Int, minute: Int, tomorrow: Boolean) {
        val s = _uiState.value
        if (!s.canReschedule) return
        _uiState.update { it.copy(rescheduleCount = it.rescheduleCount + 1) }
        viewModelScope.launch {
            val routine = repository.getRoutineById(routineId).first() ?: return@launch
            val triggerAt = absoluteTime(hour, minute, tomorrow)

            alarmHelper.cancelRoutineAlarms(routineId)
            alarmHelper.scheduleRoutineAlarms(routine)
            alarmHelper.scheduleRescheduleAlarm(routineId, routine.name, triggerAt)
            rescheduleTracker.set(routineId, triggerAt)

            // Replace alarm notification with a rescheduled countdown notification.
            notificationHelper.cancelNotification(NotificationHelper.alarmNotificationId(routineId))
            notificationHelper.showNotification(
                NotificationHelper.alarmNotificationId(routineId),
                notificationHelper.buildSnoozedNotification(
                    routineId      = routineId,
                    routineName    = routine.name,
                    triggerMillis  = triggerAt,
                    snoozeCount    = s.snoozeCount,
                    maxSnoozeCount = routine.maxSnoozeCount,
                    invincibleMode = routine.invincibleMode,
                ),
            )
        }
    }

    private fun absoluteTime(hour: Int, minute: Int, tomorrow: Boolean): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (tomorrow) add(Calendar.DAY_OF_YEAR, 1)
        }
        if (!tomorrow && cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }
}
