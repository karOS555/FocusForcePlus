package com.focusforceplus.app.ui.screens.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.data.db.entity.RoutineEntity
import com.focusforceplus.app.data.repository.RoutineRepository
import com.focusforceplus.app.service.ActiveRoutineRegistry
import com.focusforceplus.app.ui.common.routineTodayKey
import com.focusforceplus.app.util.AlarmHelper
import com.focusforceplus.app.util.PendingRescheduleTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One routine as the list shows it. */
data class RoutineRow(
    val routine: RoutineEntity,
    val taskCount: Int,
    val totalMinutes: Int,
    /** Set while a one-time move of the next run is pending. */
    val pendingRescheduleMillis: Long?,
    val running: Boolean,
)

data class RoutineListUiState(
    val loading: Boolean = true,
    val today: List<RoutineRow> = emptyList(),
    val otherDays: List<RoutineRow> = emptyList(),
    val paused: List<RoutineRow> = emptyList(),
) {
    val isEmpty: Boolean get() = !loading && today.isEmpty() && otherDays.isEmpty() && paused.isEmpty()
}

@HiltViewModel
class RoutineListViewModel @Inject constructor(
    private val repository: RoutineRepository,
    private val alarmHelper: AlarmHelper,
    private val rescheduleTracker: PendingRescheduleTracker,
    private val activeRegistry: ActiveRoutineRegistry,
) : ViewModel() {

    /** Bumped to re-read the pending moves, which live outside the database. */
    private val refresh = MutableStateFlow(0)

    val uiState: StateFlow<RoutineListUiState> = combine(
        repository.getAllRoutines(),
        repository.getAllTasks(),
        activeRegistry.activeRoutineId,
        refresh,
    ) { routines, tasks, activeId, _ ->
        val byRoutine = tasks.groupBy { it.routineId }
        val today = routineTodayKey()
        val rows = routines.map { routine ->
            val steps = byRoutine[routine.id].orEmpty()
            RoutineRow(
                routine = routine,
                taskCount = steps.size,
                totalMinutes = steps.sumOf { it.durationMinutes },
                pendingRescheduleMillis = rescheduleTracker.get(routine.id),
                running = activeId == routine.id,
            )
        }
        fun startOf(row: RoutineRow) = row.routine.startTimeHour * 60 + row.routine.startTimeMinute
        val (active, paused) = rows.partition { it.routine.isActive }
        val (onToday, otherDays) = active.partition { today in it.routine.activeDays.split(",") }
        RoutineListUiState(
            loading = false,
            today = onToday.sortedBy(::startOf),
            otherDays = otherDays.sortedBy(::startOf),
            paused = paused.sortedBy(::startOf),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutineListUiState())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() = _message.update { null }

    fun isRunning(routineId: Long): Boolean = activeRegistry.isActive(routineId)

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

    /**
     * Moves the routine to the trash. A running routine cannot be deleted
     * (Golden Rule #11: while it runs, deleting would be a way to cancel it).
     */
    fun deleteRoutine(routine: RoutineEntity) {
        if (activeRegistry.isActive(routine.id)) {
            _message.value = "A running routine cannot be deleted. Finish or move it first."
            return
        }
        viewModelScope.launch {
            alarmHelper.cancelRoutineAlarms(routine.id)
            alarmHelper.cancelRescheduleAlarm(routine.id)
            rescheduleTracker.clear(routine.id)
            repository.moveToTrash(routine.id)
            _message.value = "\"${routine.name}\" moved to the trash"
        }
    }

    fun duplicate(routine: RoutineEntity) {
        viewModelScope.launch {
            val copy = repository.duplicate(routine.id) ?: return@launch
            _message.value = "Copied as \"${copy.name}\". It stays paused until you switch it on."
        }
    }

    fun cancelPendingReschedule(routineId: Long) {
        viewModelScope.launch {
            alarmHelper.cancelRescheduleAlarm(routineId)
            rescheduleTracker.clear(routineId)
            refresh.update { it + 1 }
        }
    }
}
