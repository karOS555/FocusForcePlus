package com.focusforceplus.app.ui.screens.todo

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.compliance.ComplianceLimits
import com.focusforceplus.app.compliance.clampTodoLimits
import com.focusforceplus.app.data.db.entity.TodoEntity
import com.focusforceplus.app.data.model.ChecklistItem
import com.focusforceplus.app.data.model.checklistFromJson
import com.focusforceplus.app.data.model.toChecklistJson
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.data.repository.TodoRepository
import com.focusforceplus.app.service.TodoAlarmForegroundService
import com.focusforceplus.app.util.TodoAlarmHelper
import com.focusforceplus.app.util.TodoNotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TodoUiState(
    val title: String = "",
    val description: String = "",
    val dueDateTime: Long? = null,
    val priority: Int = 1,
    val maxSnoozeCount: Int = 2,
    val maxRescheduleCount: Int = 1,
    val isRecurring: Boolean = false,
    val recurringType: String = "DAILY",
    val recurringDays: Set<String> = setOf("MO"),
    val checklistEnabled: Boolean = false,
    val checklistItems: List<ChecklistItem> = emptyList(),
    val isSaving: Boolean = false,
    val titleError: Boolean = false,
    val error: String? = null,
) {
    val checklistDoneCount: Int get() = checklistItems.count { it.done }
}

@HiltViewModel
class TodoViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: TodoRepository,
    private val alarmHelper: TodoAlarmHelper,
    private val notifHelper: TodoNotificationHelper,
    private val settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val editTodoId: Long = savedStateHandle.get<Long>("todoId") ?: 0L
    val isEditMode: Boolean get() = editTodoId != 0L

    // Holds the full original entity so immutable fields are preserved when saving in edit mode.
    private var originalTodo: TodoEntity? = null

    private val _uiState = MutableStateFlow(TodoUiState())
    val uiState: StateFlow<TodoUiState> = _uiState.asStateFlow()

    // Baseline for the discard-changes warning: state as loaded (edit) or default (create).
    private var snapshotState: TodoUiState? = null

    /** True when the user edited anything since opening the screen. */
    val hasUnsavedChanges: Boolean
        get() = snapshotState?.let { normalized(_uiState.value) != normalized(it) } ?: false

    private fun normalized(s: TodoUiState) =
        s.copy(isSaving = false, titleError = false, error = null)

    init {
        if (isEditMode) {
            loadTodo(editTodoId)
        } else {
            viewModelScope.launch {
                val defaultPriority = settingsRepository.defaultPriority.first()
                _uiState.update { it.copy(priority = defaultPriority) }
                snapshotState = _uiState.value
            }
        }
    }

    private fun loadTodo(id: Long) {
        viewModelScope.launch {
            repository.getTodoById(id)?.let { todo ->
                originalTodo = todo
                val (type, days) = parsePattern(todo.recurringPattern)
                val items = checklistFromJson(todo.checklistJson)
                _uiState.update {
                    it.copy(
                        title              = todo.title,
                        description        = todo.description ?: "",
                        dueDateTime        = todo.dueDateTime,
                        priority           = todo.priority,
                        maxSnoozeCount     = todo.maxSnoozeCount,
                        maxRescheduleCount = todo.maxRescheduleCount,
                        isRecurring        = todo.isRecurring,
                        recurringType      = type,
                        recurringDays      = days,
                        checklistEnabled   = items.isNotEmpty(),
                        checklistItems     = items,
                    )
                }
            }
            snapshotState = _uiState.value
        }
    }

    private fun parsePattern(pattern: String?): Pair<String, Set<String>> {
        if (pattern == null) return "DAILY" to setOf("MO")
        if (pattern.startsWith("WEEKLY_")) {
            val days = pattern.removePrefix("WEEKLY_")
                .split(",")
                .filter { it.isNotBlank() }
                .toSet()
            return "WEEKLY" to days.ifEmpty { setOf("MO") }
        }
        return pattern to setOf("MO")
    }

    fun updateTitle(v: String) = _uiState.update { it.copy(title = v, titleError = false) }
    fun updateDescription(v: String) = _uiState.update { it.copy(description = v) }
    fun updateDueDateTime(v: Long?) = _uiState.update { it.copy(dueDateTime = v) }
    fun updatePriority(v: Int) = _uiState.update { it.copy(priority = v) }
    fun updateMaxSnoozeCount(v: Int) = _uiState.update {
        it.copy(maxSnoozeCount = v.coerceIn(1, ComplianceLimits.MAX_TODO_SNOOZE_LIMIT))
    }
    fun updateMaxRescheduleCount(v: Int) = _uiState.update {
        it.copy(maxRescheduleCount = v.coerceIn(1, ComplianceLimits.MAX_TODO_RESCHEDULE_LIMIT))
    }
    fun updateRecurring(v: Boolean) = _uiState.update { it.copy(isRecurring = v) }
    fun updateRecurringType(v: String) = _uiState.update { it.copy(recurringType = v) }
    fun toggleRecurringDay(day: String) = _uiState.update {
        val days = it.recurringDays.toMutableSet()
        if (day in days && days.size > 1) days.remove(day) else days.add(day)
        it.copy(recurringDays = days)
    }

    fun toggleChecklist(enabled: Boolean) = _uiState.update { it.copy(checklistEnabled = enabled) }

    fun addChecklistItem(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        _uiState.update { it.copy(checklistItems = it.checklistItems + ChecklistItem(trimmed)) }
    }

    fun removeChecklistItem(index: Int) = _uiState.update {
        it.copy(checklistItems = it.checklistItems.toMutableList().also { list -> list.removeAt(index) })
    }

    fun toggleChecklistItem(index: Int) = _uiState.update { state ->
        val items = state.checklistItems.toMutableList()
        if (index in items.indices) items[index] = items[index].copy(done = !items[index].done)
        state.copy(checklistItems = items)
    }

    fun updateChecklistItemText(index: Int, text: String) = _uiState.update { state ->
        val items = state.checklistItems.toMutableList()
        if (index in items.indices) items[index] = items[index].copy(text = text)
        state.copy(checklistItems = items)
    }

    fun moveChecklistItem(from: Int, to: Int) = _uiState.update { state ->
        val items = state.checklistItems.toMutableList()
        if (from in items.indices && to in items.indices) {
            val item = items.removeAt(from)
            items.add(to, item)
        }
        state.copy(checklistItems = items)
    }

    fun saveTodo(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.title.isBlank()) {
            _uiState.update { it.copy(titleError = true) }
            return
        }
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val pattern = when {
                    !state.isRecurring -> null
                    state.recurringType == "WEEKLY" ->
                        "WEEKLY_${state.recurringDays.joinToString(",")}"
                    else -> state.recurringType
                }
                val limits = clampTodoLimits(state.priority, state.maxSnoozeCount, state.maxRescheduleCount)
                val checklistJson = if (state.checklistEnabled && state.checklistItems.isNotEmpty()) {
                    state.checklistItems.toChecklistJson()
                } else {
                    null
                }

                val original = originalTodo
                val todo = TodoEntity(
                    id                 = if (isEditMode) editTodoId else 0L,
                    title              = state.title.trim(),
                    description        = state.description.trim().ifBlank { null },
                    dueDateTime        = state.dueDateTime,
                    priority           = state.priority,
                    maxSnoozeCount     = limits.maxSnoozeCount,
                    maxRescheduleCount = limits.maxRescheduleCount,
                    isRecurring        = state.isRecurring,
                    recurringPattern   = pattern,
                    checklistJson      = checklistJson,
                    // Preserve fields that must survive an edit
                    createdAt          = if (isEditMode) original?.createdAt ?: System.currentTimeMillis()
                                         else System.currentTimeMillis(),
                    isCompleted        = if (isEditMode) original?.isCompleted ?: false else false,
                    completedAt        = if (isEditMode) original?.completedAt else null,
                    snoozeCount        = if (isEditMode) original?.snoozeCount ?: 0 else 0,
                    rescheduleCount    = if (isEditMode) original?.rescheduleCount ?: 0 else 0,
                    postponedTo        = if (isEditMode) original?.postponedTo else null,
                )

                if (isEditMode) {
                    alarmHelper.cancelTodoAlarm(editTodoId)
                    alarmHelper.cancelTodoSnoozeAlarm(editTodoId)
                    // If this todo is currently alarming, its notification is pinned by the
                    // alarm foreground service — cancelNotification alone won't remove it.
                    runCatching {
                        context.startService(TodoAlarmForegroundService.stopIntent(context))
                    }
                    notifHelper.cancelNotification(TodoNotificationHelper.todoAlarmNotificationId(editTodoId))
                    repository.updateTodo(todo)
                    // Only schedule alarm for non-completed todos with a future due date
                    if (!todo.isCompleted &&
                        todo.dueDateTime != null &&
                        todo.dueDateTime > System.currentTimeMillis()
                    ) {
                        alarmHelper.scheduleTodoAlarm(todo.copy(id = editTodoId))
                    }
                } else {
                    val newId = repository.insertTodo(todo)
                    if (todo.dueDateTime != null && todo.dueDateTime > System.currentTimeMillis()) {
                        alarmHelper.scheduleTodoAlarm(todo.copy(id = newId))
                    }
                }

                _uiState.update { it.copy(isSaving = false) }
                onSuccess()
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message ?: "Failed to save todo") }
            }
        }
    }
}
