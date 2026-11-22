package com.focusforceplus.app.ui.screens.todo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.compliance.ComplianceLimits
import com.focusforceplus.app.compliance.Guard
import com.focusforceplus.app.compliance.GuardAction
import com.focusforceplus.app.compliance.GuardOverrides
import com.focusforceplus.app.compliance.GuardSettings
import com.focusforceplus.app.compliance.clampTodoLimits
import com.focusforceplus.app.data.db.entity.TodoEntity
import com.focusforceplus.app.data.model.ChecklistItem
import com.focusforceplus.app.data.model.checklistFromJson
import com.focusforceplus.app.data.model.toChecklistJson
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.data.repository.TodoRepository
import com.focusforceplus.app.util.TodoReminderEngine
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val iconKey: String? = null,
    val dueDateTime: Long? = null,
    val priority: Int = 1,
    val maxSnoozeCount: Int = 2,
    val maxRescheduleCount: Int = 1,
    val isRecurring: Boolean = false,
    val recurringType: String = "DAILY",
    val recurringDays: Set<String> = setOf("MO"),
    val checklistItems: List<ChecklistItem> = emptyList(),
    /**
     * One id per checklist item, in the same order, that stays with the item when
     * it is moved. Rows are told apart by it while they are dragged: two items may
     * carry the same text, and the position changes with every move.
     */
    val checklistKeys: List<Long> = emptyList(),
    /** A list this todo is about, e.g. the groceries for "Go shopping". */
    val listId: Long? = null,
    val lists: List<com.focusforceplus.app.data.db.entity.ListEntity> = emptyList(),
    /** This todo's own speed bumps; an action not listed follows the default. */
    val guardOverrides: GuardOverrides = GuardOverrides(),
    val guardDefaults: GuardSettings = GuardSettings(),
    val isCompleted: Boolean = false,
    val isSaving: Boolean = false,
    val titleError: Boolean = false,
    val error: String? = null,
) {
    /** What the default is for this priority (and the master switch). */
    fun defaultGuard(action: GuardAction): Guard = guardDefaults.defaultFor(priority, action)

    /** What will actually apply when the todo is saved like this. */
    fun guardFor(action: GuardAction): Guard =
        if (!guardDefaults.enabled) Guard.NONE else guardOverrides[action] ?: defaultGuard(action)
}

/** Longest wording of one checklist item. */
private const val MAX_CHECKLIST_ITEM = 200

@HiltViewModel
class TodoViewModel @Inject constructor(
    private val repository: TodoRepository,
    private val settingsRepository: SettingsRepository,
    private val engine: TodoReminderEngine,
    private val listRepository: com.focusforceplus.app.data.repository.ListRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // A new todo can come from a list ("Remind me"), with its list and title set.
    private val presetListId: Long? = savedStateHandle.get<Long>("listId")?.takeIf { it > 0L }
    private val presetTitle: String? = savedStateHandle.get<String>("title")

    private val editTodoId: Long = savedStateHandle.get<Long>("todoId") ?: 0L
    val isEditMode: Boolean get() = editTodoId != 0L

    private var createdAt: Long? = null

    private val _uiState = MutableStateFlow(TodoUiState())
    val uiState: StateFlow<TodoUiState> = _uiState.asStateFlow()

    // Baseline for the discard-changes warning.
    private var snapshotState: TodoUiState? = null

    val hasUnsavedChanges: Boolean
        get() = snapshotState?.let { normalized(_uiState.value) != normalized(it) } ?: false

    private fun normalized(s: TodoUiState) = s.copy(
        isSaving = false, titleError = false, error = null, guardDefaults = GuardSettings(), lists = emptyList(),
        checklistKeys = emptyList(),
    )

    private var nextChecklistKey = 0L

    init {
        viewModelScope.launch {
            listRepository.getLists().collect { lists -> _uiState.update { it.copy(lists = lists) } }
        }
        viewModelScope.launch {
            val guards = settingsRepository.guardSettings.first()
            if (isEditMode) {
                repository.getTodoById(editTodoId)?.let { todo ->
                    createdAt = todo.createdAt
                    val (type, days) = parsePattern(todo.recurringPattern)
                    _uiState.update {
                        it.copy(
                            title = todo.title,
                            description = todo.description.orEmpty(),
                            iconKey = todo.iconKey,
                            dueDateTime = todo.dueDateTime,
                            priority = todo.priority,
                            maxSnoozeCount = todo.maxSnoozeCount,
                            maxRescheduleCount = todo.maxRescheduleCount
                                .coerceAtMost(ComplianceLimits.MAX_TODO_RESCHEDULE_LIMIT),
                            isRecurring = todo.isRecurring,
                            recurringType = type,
                            recurringDays = days,
                            checklistItems = checklistFromJson(todo.checklistJson),
                            checklistKeys = checklistFromJson(todo.checklistJson).map { nextChecklistKey++ },
                            guardOverrides = GuardOverrides.decode(todo.guards),
                            guardDefaults = guards,
                            isCompleted = todo.isCompleted,
                            listId = todo.listId,
                        )
                    }
                }
            } else {
                val defaultPriority = settingsRepository.defaultPriority.first()
                _uiState.update {
                    it.copy(
                        priority = defaultPriority,
                        guardDefaults = guards,
                        listId = presetListId,
                        title = presetTitle.orEmpty(),
                    )
                }
            }
            snapshotState = _uiState.value
        }
    }

    private fun parsePattern(pattern: String?): Pair<String, Set<String>> {
        if (pattern == null) return "DAILY" to setOf("MO")
        if (pattern.startsWith("WEEKLY_")) {
            val days = pattern.removePrefix("WEEKLY_").split(",").filter { it.isNotBlank() }.toSet()
            return "WEEKLY" to days.ifEmpty { setOf("MO") }
        }
        return pattern to setOf("MO")
    }

    fun updateTitle(v: String) = _uiState.update { it.copy(title = v, titleError = false) }
    fun updateDescription(v: String) = _uiState.update { it.copy(description = v) }
    fun updateIcon(v: String?) = _uiState.update { it.copy(iconKey = v) }
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
    /** null hands the action back to the default. */
    fun updateGuard(action: GuardAction, guard: Guard?) =
        _uiState.update { it.copy(guardOverrides = it.guardOverrides.with(action, guard)) }

    fun addChecklistItem(text: String) {
        val trimmed = text.trim().take(MAX_CHECKLIST_ITEM)
        if (trimmed.isBlank()) return
        _uiState.update {
            it.copy(
                checklistItems = it.checklistItems + ChecklistItem(trimmed),
                checklistKeys = it.checklistKeys + nextChecklistKey++,
            )
        }
    }

    /** The wording of an item, changed in place. An item emptied this way is dropped when saving. */
    fun editChecklistItem(index: Int, text: String) = _uiState.update { state ->
        state.copy(
            checklistItems = state.checklistItems.mapIndexed { i, item ->
                // One item is one line, whatever a keyboard with real keys or a paste brings.
                if (i == index) item.copy(text = text.replace('\n', ' ').take(MAX_CHECKLIST_ITEM)) else item
            }
        )
    }

    fun removeChecklistItem(index: Int) = _uiState.update { state ->
        state.copy(
            checklistItems = state.checklistItems.filterIndexed { i, _ -> i != index },
            checklistKeys = state.checklistKeys.filterIndexed { i, _ -> i != index },
        )
    }

    fun toggleChecklistItem(index: Int) = _uiState.update { state ->
        state.copy(
            checklistItems = state.checklistItems.mapIndexed { i, item ->
                if (i == index) item.copy(done = !item.done) else item
            }
        )
    }

    fun setList(listId: Long?) = _uiState.update { it.copy(listId = listId) }

    fun moveChecklistItem(from: Int, to: Int) = _uiState.update { state ->
        val items = state.checklistItems.toMutableList()
        val keys = state.checklistKeys.toMutableList()
        if (from in items.indices && to in items.indices) {
            items.add(to, items.removeAt(from))
            if (from in keys.indices && to in keys.indices) keys.add(to, keys.removeAt(from))
        }
        state.copy(checklistItems = items, checklistKeys = keys)
    }

    /**
     * Set once this editor has saved. It is closing then, and a second tap on Save
     * while it still shows must not save again: for a new one that would be a copy.
     */
    private var hasSaved = false

    fun saveTodo(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (hasSaved || state.isSaving) return
        if (state.title.isBlank()) {
            _uiState.update { it.copy(titleError = true) }
            return
        }
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val pattern = when {
                    !state.isRecurring -> null
                    state.recurringType == "WEEKLY" -> "WEEKLY_${state.recurringDays.joinToString(",")}"
                    else -> state.recurringType
                }
                val limits = clampTodoLimits(state.priority, state.maxSnoozeCount, state.maxRescheduleCount)
                // Only the fields of this form. What else the stored todo carries
                // (done, snoozes, its calendar event, its synced task) may have
                // changed while the editor was open, so the engine takes it from the
                // stored row when it saves.
                val todo = TodoEntity(
                    id = if (isEditMode) editTodoId else 0L,
                    createdAt = createdAt ?: System.currentTimeMillis(),
                    title = state.title.trim(),
                    description = state.description.trim().ifBlank { null },
                    iconKey = state.iconKey,
                    dueDateTime = state.dueDateTime,
                    priority = state.priority,
                    maxSnoozeCount = limits.maxSnoozeCount,
                    maxRescheduleCount = limits.maxRescheduleCount,
                    isRecurring = state.isRecurring,
                    recurringPattern = pattern,
                    checklistJson = state.checklistItems
                        .mapNotNull { item -> item.text.trim().takeIf { it.isNotEmpty() }?.let { item.copy(text = it) } }
                        .takeIf { it.isNotEmpty() }?.toChecklistJson(),
                    guards = state.guardOverrides.encode(),
                    listId = state.listId,
                )
                engine.save(todo)
                _uiState.update { it.copy(isSaving = false) }
                hasSaved = true
                onSuccess()
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message ?: "Could not save the todo") }
            }
        }
    }

    fun deleteTodo(onDeleted: () -> Unit) {
        if (!isEditMode) return
        viewModelScope.launch {
            engine.moveToTrash(editTodoId)
            onDeleted()
        }
    }
}
