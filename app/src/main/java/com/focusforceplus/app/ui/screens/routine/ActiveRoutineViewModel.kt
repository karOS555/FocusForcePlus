package com.focusforceplus.app.ui.screens.routine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.data.db.entity.RoutineTaskEntity
import com.focusforceplus.app.data.repository.RoutineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TimerState { BEFORE_START, RUNNING, OVERTIME }

data class CompletionStats(
    val totalScheduledMinutes: Int,
    val totalOvertimeMinutes: Int,
    val tasksCompleted: Int,
)

data class ActiveRoutineUiState(
    val routineName: String = "",
    val tasks: List<RoutineTaskEntity> = emptyList(),
    val maxSnoozeCount: Int = 2,
    val invincibleMode: Boolean = false,
    // Timer
    val currentTaskIndex: Int = 0,
    val timerState: TimerState = TimerState.BEFORE_START,
    val remainingSeconds: Int = 0,
    val taskTotalSeconds: Int = 0,
    val overtimeSeconds: Int = 0,
    // Snooze
    val snoozeCount: Int = 0,
    // Dialogs
    val showThreeMinWarning: Boolean = false,
    val showTimeUpDialog: Boolean = false,
    val showAddTimeDialog: Boolean = false,
    val showSnoozeDialog: Boolean = false,
    val showOvertimeReminder: Boolean = false,
    val overtimeReminderMinutes: Int = 0,
    val cancelConfirmStep: Int = 0, // 0=hidden 1=first-confirm 2=reschedule-picker
    // Completion
    val isCompleted: Boolean = false,
    val completionStats: CompletionStats? = null,
    val isLoading: Boolean = true,
) {
    val currentTask: RoutineTaskEntity? get() = tasks.getOrNull(currentTaskIndex)
    val canSnooze: Boolean
        get() = timerState == TimerState.BEFORE_START
                && currentTaskIndex == 0
                && snoozeCount < maxSnoozeCount
    val remainingSnoozes: Int get() = maxSnoozeCount - snoozeCount
    val overallProgress: Float
        get() = if (tasks.isEmpty()) 0f
                else currentTaskIndex.toFloat() / tasks.size.toFloat()
}

sealed class RoutineEvent {
    data object PlayAlarm : RoutineEvent()
    data object VibrateShort : RoutineEvent()
}

@HiltViewModel
class ActiveRoutineViewModel @Inject constructor(
    private val repository: RoutineRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val routineId: Long = savedStateHandle.get<Long>("routineId") ?: 0L

    private val _uiState = MutableStateFlow(ActiveRoutineUiState())
    val uiState: StateFlow<ActiveRoutineUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<RoutineEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<RoutineEvent> = _events.asSharedFlow()

    private var timerJob: Job? = null
    private var accumulatedOvertimeSeconds = 0

    init { loadRoutine() }

    private fun loadRoutine() {
        viewModelScope.launch {
            val routine = repository.getRoutineById(routineId).first() ?: return@launch
            val tasks = repository.getTasksForRoutine(routineId).first()
            val totalSec = (tasks.firstOrNull()?.durationMinutes ?: 0) * 60
            _uiState.update {
                it.copy(
                    routineName = routine.name,
                    tasks = tasks,
                    maxSnoozeCount = routine.maxSnoozeCount,
                    invincibleMode = routine.invincibleMode,
                    remainingSeconds = totalSec,
                    taskTotalSeconds = totalSec,
                    isLoading = false,
                )
            }
        }
    }

    // ── Timer control ────────────────────────────────────────────────────────

    fun startRoutine() {
        val task = _uiState.value.currentTask ?: return
        val totalSec = task.durationMinutes * 60
        _uiState.update {
            it.copy(
                timerState = TimerState.RUNNING,
                remainingSeconds = totalSec,
                taskTotalSeconds = totalSec,
                overtimeSeconds = 0,
            )
        }
        launchTimer()
    }

    private fun launchTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1_000L)
                tick()
            }
        }
    }

    private fun tick() {
        val s = _uiState.value
        when (s.timerState) {
            TimerState.RUNNING -> {
                val newRemaining = s.remainingSeconds - 1
                if (newRemaining <= 0) {
                    _events.tryEmit(RoutineEvent.PlayAlarm)
                    _events.tryEmit(RoutineEvent.VibrateShort)
                    _uiState.update {
                        it.copy(
                            remainingSeconds = 0,
                            timerState = TimerState.OVERTIME,
                            overtimeSeconds = 0,
                            showTimeUpDialog = true,
                        )
                    }
                } else {
                    val reminderAt = (s.currentTask?.reminderBeforeEndMinutes ?: 3) * 60
                    _uiState.update {
                        it.copy(
                            remainingSeconds = newRemaining,
                            showThreeMinWarning = newRemaining == reminderAt,
                        )
                    }
                }
            }
            TimerState.OVERTIME -> {
                val newOvertime = s.overtimeSeconds + 1
                accumulatedOvertimeSeconds++
                val intervalSec = (s.currentTask?.overtimeReminderIntervalMinutes ?: 5) * 60
                val trigger = intervalSec > 0 && newOvertime % intervalSec == 0
                if (trigger) _events.tryEmit(RoutineEvent.VibrateShort)
                _uiState.update {
                    it.copy(
                        overtimeSeconds = newOvertime,
                        showOvertimeReminder = trigger,
                        overtimeReminderMinutes = if (trigger) newOvertime / 60
                                                  else s.overtimeReminderMinutes,
                    )
                }
            }
            else -> {}
        }
    }

    // ── Task actions ─────────────────────────────────────────────────────────

    fun completeCurrentTask() {
        timerJob?.cancel()
        val s = _uiState.value
        val nextIndex = s.currentTaskIndex + 1
        if (nextIndex >= s.tasks.size) {
            _uiState.update {
                it.copy(
                    isCompleted = true,
                    timerState = TimerState.BEFORE_START,
                    showTimeUpDialog = false,
                    completionStats = CompletionStats(
                        totalScheduledMinutes = s.tasks.sumOf { t -> t.durationMinutes },
                        totalOvertimeMinutes = accumulatedOvertimeSeconds / 60,
                        tasksCompleted = s.tasks.size,
                    ),
                )
            }
        } else {
            val nextTask = s.tasks[nextIndex]
            val totalSec = nextTask.durationMinutes * 60
            _uiState.update {
                it.copy(
                    currentTaskIndex = nextIndex,
                    timerState = TimerState.RUNNING,
                    remainingSeconds = totalSec,
                    taskTotalSeconds = totalSec,
                    overtimeSeconds = 0,
                    showTimeUpDialog = false,
                    showThreeMinWarning = false,
                    showOvertimeReminder = false,
                )
            }
            launchTimer()
        }
    }

    fun addTime(minutes: Int) {
        timerJob?.cancel()
        _uiState.update {
            it.copy(
                remainingSeconds = it.remainingSeconds + minutes * 60,
                overtimeSeconds = 0,
                timerState = TimerState.RUNNING,
                showTimeUpDialog = false,
                showAddTimeDialog = false,
            )
        }
        launchTimer()
    }

    fun snooze(minutes: Int, onSnoozed: (Int) -> Unit) {
        if (!_uiState.value.canSnooze) return
        _uiState.update { it.copy(snoozeCount = it.snoozeCount + 1, showSnoozeDialog = false) }
        onSnoozed(minutes)
    }

    // ── Dialog toggles ───────────────────────────────────────────────────────

    fun showAddTimeDialog() = _uiState.update { it.copy(showAddTimeDialog = true) }
    fun dismissAddTimeDialog() = _uiState.update { it.copy(showAddTimeDialog = false) }
    fun showSnoozeDialog() = _uiState.update { it.copy(showSnoozeDialog = true) }
    fun dismissSnoozeDialog() = _uiState.update { it.copy(showSnoozeDialog = false) }
    fun dismissThreeMinWarning() = _uiState.update { it.copy(showThreeMinWarning = false) }
    fun dismissTimeUpDialog() = _uiState.update { it.copy(showTimeUpDialog = false) }
    fun dismissOvertimeReminder() = _uiState.update { it.copy(showOvertimeReminder = false) }
    fun onCancelFirstClick() = _uiState.update { it.copy(cancelConfirmStep = 1) }
    fun onCancelConfirmed() = _uiState.update { it.copy(cancelConfirmStep = 2) }
    fun dismissCancel() = _uiState.update { it.copy(cancelConfirmStep = 0) }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
