package com.focusforceplus.app.ui.screens.routine

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.data.db.entity.RoutineTaskEntity
import com.focusforceplus.app.data.repository.RoutineRepository
import com.focusforceplus.app.service.ActiveRoutineRegistry
import com.focusforceplus.app.service.RoutineForegroundService
import com.focusforceplus.app.service.RoutineSessionPhase
import com.focusforceplus.app.util.AlarmHelper
import com.focusforceplus.app.util.NotificationHelper
import com.focusforceplus.app.util.PendingRescheduleTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
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
    val maxRescheduleCount: Int = 1,
    val invincibleMode: Boolean = false,
    val appBlockerEnabled: Boolean = false,
    // Timer
    val currentTaskIndex: Int = 0,
    val timerState: TimerState = TimerState.BEFORE_START,
    val remainingSeconds: Int = 0,
    val taskTotalSeconds: Int = 0,
    val overtimeSeconds: Int = 0,
    // Snooze / reschedule
    val snoozeCount: Int = 0,
    val rescheduleCount: Int = 0,
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
    val canReschedule: Boolean get() = rescheduleCount < maxRescheduleCount
    val overallProgress: Float
        get() = if (tasks.isEmpty()) 0f
                else currentTaskIndex.toFloat() / tasks.size.toFloat()
}

sealed class RoutineEvent {
    data object NavigateBack : RoutineEvent()
}

/**
 * Thin observer over the session owned by [RoutineForegroundService]: the service
 * drives the timer and survives the UI, this ViewModel merely mirrors its progress
 * into UI state and forwards user actions as service commands. Reopening the screen
 * mid-session resumes seamlessly from the live service values.
 */
@HiltViewModel
class ActiveRoutineViewModel @Inject constructor(
    private val repository: RoutineRepository,
    private val alarmHelper: AlarmHelper,
    private val notificationHelper: NotificationHelper,
    private val rescheduleTracker: PendingRescheduleTracker,
    private val registry: ActiveRoutineRegistry,
    @param:ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val routineId: Long = savedStateHandle.get<Long>("routineId") ?: 0L

    private val _uiState = MutableStateFlow(ActiveRoutineUiState())
    val uiState: StateFlow<ActiveRoutineUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<RoutineEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<RoutineEvent> = _events.asSharedFlow()

    /** Edge detection for the reminder-before-end dialog. */
    private var lastRemainingSeconds: Int = Int.MAX_VALUE

    /**
     * The registry keeps the last COMPLETED progress after the service stops, so a
     * freshly opened screen must not mistake yesterday's completion for its own.
     * Completion is only honored after this instance saw the session live.
     */
    private var observedLiveSession = false

    init {
        loadRoutine()
        observeSession()
    }

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
                    maxRescheduleCount = routine.maxRescheduleCount,
                    invincibleMode = routine.invincibleMode,
                    appBlockerEnabled = routine.appBlockerEnabled,
                    remainingSeconds = totalSec,
                    taskTotalSeconds = totalSec,
                    isLoading = false,
                )
            }
        }
    }

    private fun observeSession() {
        viewModelScope.launch {
            registry.sessionProgress.collect { p ->
                if (p == null || p.routineId != routineId) return@collect
                if (p.phase == RoutineSessionPhase.COMPLETED) {
                    if (!observedLiveSession) return@collect
                    _uiState.update {
                        it.copy(
                            isCompleted = true,
                            timerState = TimerState.BEFORE_START,
                            showTimeUpDialog = false,
                            showThreeMinWarning = false,
                            showOvertimeReminder = false,
                            completionStats = CompletionStats(
                                totalScheduledMinutes = p.totalScheduledMinutes,
                                totalOvertimeMinutes = p.totalOvertimeMinutes,
                                tasksCompleted = p.taskCount,
                            ),
                        )
                    }
                    return@collect
                }

                observedLiveSession = true
                val s = _uiState.value
                if (s.isCompleted) return@collect
                val task = s.tasks.getOrNull(p.taskIndex)
                val reminderAt = (task?.reminderBeforeEndMinutes ?: 3) * 60
                val crossedReminder = p.phase == RoutineSessionPhase.RUNNING &&
                    lastRemainingSeconds > reminderAt && p.remainingSeconds <= reminderAt &&
                    p.remainingSeconds > 0
                lastRemainingSeconds =
                    if (p.phase == RoutineSessionPhase.RUNNING) p.remainingSeconds else Int.MAX_VALUE

                val intervalSec = (task?.overtimeReminderIntervalMinutes ?: 5) * 60
                val overtimeNag = p.phase == RoutineSessionPhase.OVERTIME &&
                    intervalSec > 0 && p.overtimeSeconds > 0 && p.overtimeSeconds % intervalSec == 0

                _uiState.update {
                    it.copy(
                        currentTaskIndex = p.taskIndex,
                        timerState = when (p.phase) {
                            RoutineSessionPhase.RUNNING -> TimerState.RUNNING
                            RoutineSessionPhase.OVERTIME -> TimerState.OVERTIME
                            RoutineSessionPhase.COMPLETED -> TimerState.BEFORE_START
                        },
                        remainingSeconds = p.remainingSeconds,
                        taskTotalSeconds = p.taskTotalSeconds,
                        overtimeSeconds = p.overtimeSeconds,
                        showTimeUpDialog = p.phase == RoutineSessionPhase.OVERTIME,
                        showThreeMinWarning = it.showThreeMinWarning || crossedReminder,
                        showOvertimeReminder = it.showOvertimeReminder || overtimeNag,
                        overtimeReminderMinutes = if (overtimeNag) p.overtimeSeconds / 60
                                                  else it.overtimeReminderMinutes,
                    )
                }
            }
        }
    }

    // ── Timer control (delegated to the service) ─────────────────────────────

    fun startRoutine() {
        RoutineForegroundService.start(context, routineId)
    }

    fun completeCurrentTask() {
        RoutineForegroundService.completeTask(context)
    }

    fun addTime(minutes: Int) {
        RoutineForegroundService.addTime(context, minutes)
        _uiState.update { it.copy(showAddTimeDialog = false, showTimeUpDialog = false) }
    }

    fun snooze(minutes: Int, onSnoozed: (Int) -> Unit) {
        if (!_uiState.value.canSnooze) return
        _uiState.update { it.copy(snoozeCount = it.snoozeCount + 1, showSnoozeDialog = false) }
        onSnoozed(minutes)
    }

    /** Tears down the running session (no-op while still in BEFORE_START). */
    fun cancelSession() {
        RoutineForegroundService.cancel(context)
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

    fun onCancelConfirmed() {
        val s = _uiState.value
        if (s.maxRescheduleCount > 0 && s.canReschedule) {
            _uiState.update { it.copy(cancelConfirmStep = 2) }
        } else {
            _uiState.update { it.copy(cancelConfirmStep = 0) }
            cancelSession()
            _events.tryEmit(RoutineEvent.NavigateBack)
        }
    }

    fun reschedule(hour: Int, minute: Int, tomorrow: Boolean) {
        val s = _uiState.value
        if (!s.canReschedule) return
        _uiState.update { it.copy(rescheduleCount = it.rescheduleCount + 1, cancelConfirmStep = 0) }
        cancelSession()
        viewModelScope.launch {
            try {
                val routine = repository.getRoutineById(routineId).first() ?: return@launch
                val triggerAt = absoluteTime(hour, minute, tomorrow)

                alarmHelper.cancelRoutineAlarms(routineId)
                alarmHelper.scheduleRoutineAlarms(routine)
                alarmHelper.scheduleRescheduleAlarm(routineId, routine.name, triggerAt)
                rescheduleTracker.set(routineId, triggerAt)

                notificationHelper.cancelNotification(NotificationHelper.alarmNotificationId(routineId))
                notificationHelper.showNotification(
                    NotificationHelper.alarmNotificationId(routineId),
                    notificationHelper.buildSnoozedNotification(
                        routineId      = routineId,
                        routineName    = routine.name,
                        triggerMillis  = triggerAt,
                        snoozeCount    = 0,
                        maxSnoozeCount = routine.maxSnoozeCount,
                        invincibleMode = routine.invincibleMode,
                    ),
                )
            } catch (_: Exception) {}
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

    fun dismissCancel() = _uiState.update { it.copy(cancelConfirmStep = 0) }
}
