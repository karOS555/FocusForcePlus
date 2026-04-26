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
) {
    val canSnooze: Boolean get() = snoozeCount < maxSnoozeCount
    val remainingSnoozes: Int get() = maxSnoozeCount - snoozeCount
    val canReschedule: Boolean get() = rescheduleCount < maxRescheduleCount
    val remainingReschedules: Int get() = maxRescheduleCount - rescheduleCount
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
                        snoozeCount      = initialSnoozeCount,
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

    fun markDone(onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
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
            } finally {
                onSuccess()
            }
        }
    }

    fun snooze(minutes: Int, onSuccess: () -> Unit) {
        if (!_uiState.value.canSnooze) return
        viewModelScope.launch {
            try {
                val todo = repository.getTodoById(todoId)
                if (todo != null) {
                    val newCount      = todo.snoozeCount + 1
                    val triggerMillis = System.currentTimeMillis() + minutes * 60_000L
                    repository.updateSnoozeCount(todoId, newCount)
                    alarmHelper.scheduleTodoSnoozeAlarm(todoId, todo.title, minutes, newCount, todo.maxSnoozeCount, todo.priority)
                    // Transition FGS to snoozed notification — keeps it pinned for Medium/High
                    context.startForegroundService(
                        TodoAlarmForegroundService.startSnoozedIntent(
                            context, todoId, todo.title, triggerMillis, newCount, todo.maxSnoozeCount, todo.priority,
                        )
                    )
                }
            } finally {
                onSuccess()
            }
        }
    }

    fun reschedule(epochMillis: Long, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val todo = repository.getTodoById(todoId)
                if (todo != null) {
                    val updated = todo.copy(
                        dueDateTime     = epochMillis,
                        postponedTo     = epochMillis,
                        snoozeCount     = 0,
                        rescheduleCount = todo.rescheduleCount + 1,
                    )
                    repository.updateTodo(updated)
                    alarmHelper.cancelTodoAlarm(todoId)
                    alarmHelper.cancelTodoSnoozeAlarm(todoId)
                    alarmHelper.scheduleTodoAlarm(updated)
                    stopAlarmForegroundService()
                    notifHelper.cancelNotification(TodoNotificationHelper.todoAlarmNotificationId(todoId))
                }
            } finally {
                onSuccess()
            }
        }
    }

    private fun stopAlarmForegroundService() {
        runCatching { context.startService(TodoAlarmForegroundService.stopIntent(context)) }
    }

    fun dismiss(onSuccess: () -> Unit) {
        val state = _uiState.value
        viewModelScope.launch {
            try {
                alarmHelper.cancelTodoSnoozeAlarm(todoId)
                if (state.priority == 1) {
                    // Medium: transition FGS to dismissed soft reminder (stays pinned, no stop/restart flash)
                    val todo = repository.getTodoById(todoId)
                    if (todo != null) {
                        context.startForegroundService(
                            TodoAlarmForegroundService.startDismissedIntent(context, todoId, todo.title)
                        )
                    } else {
                        stopAlarmForegroundService()
                        notifHelper.cancelNotification(TodoNotificationHelper.todoAlarmNotificationId(todoId))
                    }
                } else {
                    stopAlarmForegroundService()
                    notifHelper.cancelNotification(TodoNotificationHelper.todoAlarmNotificationId(todoId))
                }
            } finally {
                onSuccess()
            }
        }
    }
}
