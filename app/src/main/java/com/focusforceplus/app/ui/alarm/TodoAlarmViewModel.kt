package com.focusforceplus.app.ui.alarm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.focusforceplus.app.data.model.ChecklistItem
import com.focusforceplus.app.data.model.checklistFromJson
import com.focusforceplus.app.data.model.toChecklistJson
import com.focusforceplus.app.data.repository.TodoRepository
import com.focusforceplus.app.service.TodoAlarmForegroundService
import com.focusforceplus.app.util.TodoAlarmHelper
import com.focusforceplus.app.util.nextOccurrenceMillis
import com.focusforceplus.app.util.TodoNotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TodoAlarmUiState(
    val todoTitle: String = "",
    val todoDescription: String? = null,
    val priority: Int = 1,
    val snoozeCount: Int = 0,
    val maxSnoozeCount: Int = 2,
    val rescheduleCount: Int = 0,
    val maxRescheduleCount: Int = 1,
    val isLoading: Boolean = true,
    val checklistItems: List<ChecklistItem> = emptyList(),
    val showAllDonePrompt: Boolean = false,
    /**
     * True from the moment an action (done / snooze / reschedule / dismiss) starts
     * until the screen closes. Every action writes to the database and then finishes
     * the activity, so a second tap while the first is still running would burn a
     * second snooze for nothing.
     */
    val actionInProgress: Boolean = false,
) {
    val canSnooze: Boolean get() = snoozeCount < maxSnoozeCount
    val remainingSnoozes: Int get() = (maxSnoozeCount - snoozeCount).coerceAtLeast(0)
    val canReschedule: Boolean get() = rescheduleCount < maxRescheduleCount
    val remainingReschedules: Int get() = (maxRescheduleCount - rescheduleCount).coerceAtLeast(0)
}

@HiltViewModel
class TodoAlarmViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: TodoRepository,
    private val alarmHelper: TodoAlarmHelper,
    private val notifHelper: TodoNotificationHelper,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val todoId: Long = savedStateHandle.get<Long>("todoId") ?: 0L
    private val initialSnoozeCount: Int = savedStateHandle.get<Int>("snoozeCount") ?: 0

    private val _uiState = MutableStateFlow(TodoAlarmUiState())
    val uiState: StateFlow<TodoAlarmUiState> = _uiState.asStateFlow()

    init { loadTodo() }

    private fun loadTodo() {
        viewModelScope.launch {
            repository.getTodoById(todoId)?.let { todo ->
                _uiState.update {
                    it.copy(
                        todoTitle        = todo.title,
                        todoDescription  = todo.description,
                        priority         = todo.priority,
                        // DB is authoritative; the intent extra can be stale (e.g. when the
                        // screen is opened from a notification action posted before a snooze).
                        snoozeCount      = maxOf(initialSnoozeCount, todo.snoozeCount),
                        maxSnoozeCount   = todo.maxSnoozeCount,
                        rescheduleCount  = todo.rescheduleCount,
                        maxRescheduleCount = todo.maxRescheduleCount,
                        isLoading        = false,
                        checklistItems   = checklistFromJson(todo.checklistJson),
                    )
                }
            }
        }
    }

    fun toggleChecklistItem(index: Int) {
        val state = _uiState.value
        val items = state.checklistItems.toMutableList()
        if (index !in items.indices) return
        items[index] = items[index].copy(done = !items[index].done)
        val updated = items.toList()
        val allDone = updated.isNotEmpty() && updated.all { it.done }
        _uiState.update { it.copy(checklistItems = updated, showAllDonePrompt = allDone) }
        viewModelScope.launch {
            repository.updateChecklistJson(todoId, updated.toChecklistJson())
        }
    }

    fun dismissAllDonePrompt() = _uiState.update { it.copy(showAllDonePrompt = false) }

    /**
     * Runs [block] at most once per screen. Every action here ends with the alarm
     * screen closing, so re-entrancy can only ever double-spend a snooze or
     * reschedule — which is exactly what it used to do when the close was slower
     * than the user's second tap.
     */
    private fun runOnce(onSuccess: () -> Unit, block: suspend () -> Unit) {
        if (_uiState.value.actionInProgress) return
        _uiState.update { it.copy(actionInProgress = true) }
        viewModelScope.launch {
            val ok = runCatching { block() }.isSuccess
            if (ok) {
                onSuccess()
            } else {
                // The action did not land, so the alarm has to stay: closing here
                // would drop the reminder entirely. Re-open the buttons instead.
                _uiState.update { it.copy(actionInProgress = false) }
            }
        }
    }

    fun markDone(onSuccess: () -> Unit) = runOnce(onSuccess) {
        val todo = repository.getTodoById(todoId)
        repository.markCompleted(todoId, System.currentTimeMillis())
        alarmHelper.cancelTodoAlarm(todoId)
        alarmHelper.cancelTodoSnoozeAlarm(todoId)
        stopAlarmForegroundService()
        notifHelper.cancelNotification(TodoNotificationHelper.todoAlarmNotificationId(todoId))
        if (todo != null && todo.isRecurring && todo.recurringPattern != null && todo.dueDateTime != null) {
            val nextDue = nextOccurrenceMillis(todo.dueDateTime, todo.recurringPattern)
            val next = todo.copy(
                id              = 0L,
                dueDateTime     = nextDue,
                isCompleted     = false,
                completedAt     = null,
                snoozeCount     = 0,
                rescheduleCount = 0,
                postponedTo     = null,
            )
            val newId = repository.insertTodo(next)
            alarmHelper.scheduleTodoAlarm(next.copy(id = newId))
        }
    }

    fun snooze(minutes: Int, onSuccess: () -> Unit) {
        if (!_uiState.value.canSnooze) return
        runOnce(onSuccess) {
            val todo = repository.getTodoById(todoId) ?: return@runOnce
            // The database is the authority on how many snoozes are left; the screen
            // may have been opened from a notification posted before an earlier snooze.
            if (todo.snoozeCount >= todo.maxSnoozeCount) return@runOnce
            val newCount      = todo.snoozeCount + 1
            val triggerMillis = System.currentTimeMillis() + minutes * 60_000L
            repository.updateSnoozeCount(todoId, newCount)
            _uiState.update { it.copy(snoozeCount = newCount, maxSnoozeCount = todo.maxSnoozeCount) }
            alarmHelper.scheduleTodoSnoozeAlarm(
                todoId, todo.title, minutes, newCount, todo.maxSnoozeCount, todo.priority,
            )
            // Transition the pinned notification to its snoozed form. An OEM refusing
            // the background service start must not undo the snooze itself, so this
            // falls back to a plain post.
            val pinned = runCatching {
                context.startForegroundService(
                    TodoAlarmForegroundService.startSnoozedIntent(
                        context, todoId, todo.title, triggerMillis, newCount, todo.maxSnoozeCount, todo.priority,
                    )
                )
            }.isSuccess
            if (!pinned) {
                notifHelper.showNotification(
                    TodoNotificationHelper.todoAlarmNotificationId(todoId),
                    notifHelper.buildTodoSnoozedNotification(
                        todoId, todo.title, triggerMillis, newCount, todo.maxSnoozeCount, todo.priority,
                    ),
                )
            }
        }
    }

    fun reschedule(epochMillis: Long, onSuccess: () -> Unit) {
        if (!_uiState.value.canReschedule && _uiState.value.priority == 2) return
        runOnce(onSuccess) {
            val todo = repository.getTodoById(todoId) ?: return@runOnce
            // High priority enforces the reschedule cap. The UI disables the button;
            // this also guards the notification-action path.
            if (todo.priority == 2 && todo.rescheduleCount >= todo.maxRescheduleCount) return@runOnce
            val updated = todo.copy(
                dueDateTime     = epochMillis,
                postponedTo     = epochMillis,
                snoozeCount     = 0,
                rescheduleCount = todo.rescheduleCount + 1,
            )
            repository.updateTodo(updated)
            _uiState.update {
                it.copy(snoozeCount = 0, rescheduleCount = updated.rescheduleCount)
            }
            alarmHelper.cancelTodoAlarm(todoId)
            alarmHelper.cancelTodoSnoozeAlarm(todoId)
            alarmHelper.scheduleTodoAlarm(updated)
            stopAlarmForegroundService()
            notifHelper.cancelNotification(TodoNotificationHelper.todoAlarmNotificationId(todoId))
        }
    }

    private fun stopAlarmForegroundService() {
        runCatching { context.startService(TodoAlarmForegroundService.stopIntent(context)) }
    }

    fun dismiss(onSuccess: () -> Unit) = runOnce(onSuccess) {
        alarmHelper.cancelTodoSnoozeAlarm(todoId)
        val todo = if (_uiState.value.priority == 1) repository.getTodoById(todoId) else null
        if (todo != null) {
            // Medium: transition to the dismissed soft reminder (stays pinned, no flash).
            val pinned = runCatching {
                context.startForegroundService(
                    TodoAlarmForegroundService.startDismissedIntent(context, todoId, todo.title)
                )
            }.isSuccess
            if (!pinned) {
                notifHelper.showNotification(
                    TodoNotificationHelper.todoAlarmNotificationId(todoId),
                    notifHelper.buildMediumDismissedNotification(todoId, todo.title),
                )
            }
        } else {
            stopAlarmForegroundService()
            notifHelper.cancelNotification(TodoNotificationHelper.todoAlarmNotificationId(todoId))
        }
    }
}
