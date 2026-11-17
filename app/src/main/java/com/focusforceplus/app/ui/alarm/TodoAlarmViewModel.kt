package com.focusforceplus.app.ui.alarm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.compliance.Guard
import com.focusforceplus.app.compliance.GuardAction
import com.focusforceplus.app.data.db.entity.TodoEntity
import com.focusforceplus.app.data.model.ChecklistItem
import com.focusforceplus.app.data.model.checklistFromJson
import com.focusforceplus.app.data.model.toChecklistJson
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.data.repository.TodoRepository
import com.focusforceplus.app.util.TodoAlarmScheduler
import com.focusforceplus.app.util.TodoReminderEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TodoAlarmUiState(
    val loading: Boolean = true,
    val todo: TodoEntity? = null,
    /** Nothing left to answer here: the todo is done, gone, or was dealt with elsewhere. */
    val finished: Boolean = false,
    val snoozed: Boolean = false,
    val doneGuard: Guard = Guard.NONE,
    val snoozeGuard: Guard = Guard.NONE,
    val rescheduleGuard: Guard = Guard.NONE,
    val canSnooze: Boolean = false,
    val canReschedule: Boolean = false,
    val checklist: List<ChecklistItem> = emptyList(),
) {
    val priority: Int get() = todo?.priority ?: 1
    val remainingSnoozes: Int
        get() = todo?.let { (it.maxSnoozeCount - it.snoozeCount).coerceAtLeast(0) } ?: 0
    val remainingReschedules: Int
        get() = todo?.let { (it.maxRescheduleCount - it.rescheduleCount).coerceAtLeast(0) } ?: 0
}

/**
 * The alarm screen holds no state of its own. It shows the stored todo and asks
 * the engine to act, so when the same todo is snoozed or finished from a
 * notification while this screen is open, the screen notices and closes instead of
 * offering buttons that no longer apply.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodoAlarmViewModel @Inject constructor(
    private val engine: TodoReminderEngine,
    private val repository: TodoRepository,
    settings: SettingsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val todoId = MutableStateFlow(
        savedStateHandle.get<Long>(TodoAlarmScheduler.EXTRA_TODO_ID) ?: 0L
    )

    /** The todo this screen is showing. */
    val currentId: StateFlow<Long> = todoId.asStateFlow()

    /** Set once the user answered here; the screen then closes whatever the state. */
    private val answered = MutableStateFlow(false)

    /** Whether the todo was asking for an answer when it first showed up. */
    private var wasActionable: Boolean? = null

    val uiState: StateFlow<TodoAlarmUiState> = combine(
        todoId.flatMapLatest { repository.observeTodo(it) },
        settings.guardSettings,
        answered,
    ) { todo, guards, done ->
        if (todo == null || todo.isCompleted || todo.isInTrash) {
            return@combine TodoAlarmUiState(loading = false, todo = todo, finished = true)
        }
        val actionable = engine.isActionable(todo)
        val snoozed = engine.isSnoozed(todo)
        if (wasActionable == null) wasActionable = actionable
        // It was ringing and now it is not: someone answered it somewhere else.
        val answeredElsewhere = wasActionable == true && !actionable
        TodoAlarmUiState(
            loading = false,
            todo = todo,
            finished = done || answeredElsewhere,
            snoozed = snoozed,
            doneGuard = guards.forTodo(todo, GuardAction.DONE),
            snoozeGuard = guards.forTodo(todo, GuardAction.SNOOZE),
            rescheduleGuard = guards.forTodo(todo, GuardAction.RESCHEDULE),
            canSnooze = engine.canSnooze(todo),
            canReschedule = engine.canReschedule(todo),
            checklist = checklistFromJson(todo.checklistJson),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodoAlarmUiState())

    /** The screen was asked to show another todo (a second alarm came in). */
    fun show(id: Long) {
        if (id <= 0L || id == todoId.value) return
        wasActionable = null
        answered.value = false
        todoId.value = id
    }

    /**
     * Moves on to the next todo that came due while this one was on screen. Returns
     * false when there is none, which is when the screen closes.
     */
    suspend fun advance(): Boolean {
        while (true) {
            val next = TodoAlarmActivity.nextWaiting() ?: return false
            val todo = repository.getTodoById(next) ?: continue
            if (next != todoId.value && engine.isActionable(todo)) {
                show(next)
                return true
            }
        }
    }

    fun done() = answer { engine.complete(it) }

    fun snooze(minutes: Int) = answer { engine.snooze(it, minutes) }

    fun reschedule(dueMillis: Long) = answer { engine.reschedule(it, dueMillis) }

    fun dismiss() = answer { engine.dismiss(it) }

    private fun answer(action: suspend (Long) -> TodoReminderEngine.Outcome) {
        val id = todoId.value
        viewModelScope.launch {
            val outcome = runCatching { action(id) }.getOrDefault(TodoReminderEngine.Outcome.NOT_ALLOWED)
            // If it did not go through, the alarm stays up: closing would drop it.
            if (outcome != TodoReminderEngine.Outcome.NOT_ALLOWED) answered.value = true
        }
    }

    fun toggleChecklistItem(index: Int) {
        val todo = uiState.value.todo ?: return
        val items = checklistFromJson(todo.checklistJson).toMutableList()
        if (index !in items.indices) return
        items[index] = items[index].copy(done = !items[index].done)
        viewModelScope.launch { repository.updateChecklistJson(todo.id, items.toChecklistJson()) }
    }
}
