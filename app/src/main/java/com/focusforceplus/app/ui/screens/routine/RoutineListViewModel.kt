package com.focusforceplus.app.ui.screens.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.data.db.entity.RoutineEntity
import com.focusforceplus.app.data.repository.RoutineRepository
import com.focusforceplus.app.util.AlarmHelper
import com.focusforceplus.app.util.PendingRescheduleTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RoutineWithReschedule(
    val routine: RoutineEntity,
    /** Non-null when a one-time reschedule alarm is pending for this routine. */
    val pendingRescheduleMillis: Long? = null,
)

@HiltViewModel
class RoutineListViewModel @Inject constructor(
    private val repository: RoutineRepository,
    private val alarmHelper: AlarmHelper,
    private val rescheduleTracker: PendingRescheduleTracker,
) : ViewModel() {

    val routinesWithReschedule: StateFlow<List<RoutineWithReschedule>> = repository
        .getAllRoutines()
        .map { routines ->
            routines.map { routine ->
                RoutineWithReschedule(
                    routine                = routine,
                    pendingRescheduleMillis = rescheduleTracker.get(routine.id),
                )
            }
        }
        .stateIn(
            scope          = viewModelScope,
            started        = SharingStarted.WhileSubscribed(5_000),
            initialValue   = emptyList(),
        )

    fun toggleActive(routine: RoutineEntity) {
        viewModelScope.launch {
            val updated = routine.copy(isActive = !routine.isActive)
            repository.updateRoutine(updated)
            if (updated.isActive) {
                alarmHelper.scheduleRoutineAlarms(updated)
            } else {
                alarmHelper.cancelRoutineAlarms(updated.id)
            }
        }
    }

    fun deleteRoutine(routine: RoutineEntity) {
        viewModelScope.launch {
            alarmHelper.cancelRoutineAlarms(routine.id)
            alarmHelper.cancelRescheduleAlarm(routine.id)
            rescheduleTracker.clear(routine.id)
            repository.deleteRoutine(routine)
        }
    }

    fun cancelPendingReschedule(routineId: Long) {
        viewModelScope.launch {
            alarmHelper.cancelRescheduleAlarm(routineId)
            rescheduleTracker.clear(routineId)
        }
    }
}
