package com.focusforceplus.app.ui.screens.todo

import com.focusforceplus.app.data.model.toChecklistJson
import com.focusforceplus.app.data.model.checklistFromJson
import kotlinx.coroutines.flow.first
import com.focusforceplus.app.compliance.clampTodoLimits
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.compliance.Guard
import com.focusforceplus.app.compliance.GuardAction
import com.focusforceplus.app.compliance.GuardSettings
import com.focusforceplus.app.data.db.entity.TodoEntity
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.data.repository.TodoRepository
import com.focusforceplus.app.util.TodoReminderEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class SortOrder(val label: String) {
    DEFAULT("By due date, grouped"),
    PRIORITY("Priority"),
    CREATED_AT("Newest first"),
    TITLE("Title A to Z"),
}

enum class TodoTab { OPEN, DONE, TRASH }

/** A labelled block of the open list: Overdue, Today, Later, No date. */
data class TodoGroup(
    val label: String?,
    val overdue: Boolean,
    val todos: List<TodoEntity>,
)

data class TodoListUiState(
    val loading: Boolean = true,
    val openGroups: List<TodoGroup> = emptyList(),
    val openCount: Int = 0,
    val done: List<TodoEntity> = emptyList(),
    val doneCount: Int = 0,
    val trashed: List<TodoEntity> = emptyList(),
    val searchQuery: String = "",
    val filterPriority: Int? = null,
    val sortOrder: SortOrder = SortOrder.DEFAULT,
    val retentionDays: Int = SettingsRepository.DEFAULT_TRASH_RETENTION_DAYS,
    val guards: GuardSettings = GuardSettings(),
    /** The todo that was just moved to the trash, for the undo bar. */
    val justTrashed: TodoEntity? = null,
) {
    val hasFilters: Boolean get() = searchQuery.isNotBlank() || filterPriority != null
}

private data class Filters(
    val query: String = "",
    val priority: Int? = null,
    val sort: SortOrder = SortOrder.DEFAULT,
)

@HiltViewModel
class TodoListViewModel @Inject constructor(
    repository: TodoRepository,
    private val settings: SettingsRepository,
    private val engine: TodoReminderEngine,
) : ViewModel() {

    private val filters = MutableStateFlow(Filters())
    private val justTrashed = MutableStateFlow<TodoEntity?>(null)

    val uiState: StateFlow<TodoListUiState> = combine(
        repository.getAllTodos(),
        repository.getTrashedTodos(),
        filters,
        combine(settings.trashRetentionDays, settings.guardSettings, ::Pair),
        justTrashed,
    ) { todos, trashed, f, (retention, guards), undo ->
        fun List<TodoEntity>.filtered(): List<TodoEntity> = filter { todo ->
            (f.priority == null || todo.priority == f.priority) &&
                (f.query.isBlank() ||
                    todo.title.contains(f.query, ignoreCase = true) ||
                    todo.description?.contains(f.query, ignoreCase = true) == true)
        }

        val open = todos.filter { !it.isCompleted }.filtered()
        val done = todos.filter { it.isCompleted }.filtered().sortedByDescending { it.completedAt }

        TodoListUiState(
            loading = false,
            openGroups = group(open, f.sort),
            openCount = open.size,
            done = done,
            doneCount = done.size,
            trashed = trashed.filtered(),
            searchQuery = f.query,
            filterPriority = f.priority,
            sortOrder = f.sort,
            retentionDays = retention,
            guards = guards,
            justTrashed = undo,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodoListUiState())

    private fun group(open: List<TodoEntity>, sort: SortOrder): List<TodoGroup> {
        if (open.isEmpty()) return emptyList()
        if (sort != SortOrder.DEFAULT) {
            val sorted = when (sort) {
                SortOrder.PRIORITY -> open.sortedWith(
                    compareByDescending<TodoEntity> { it.priority }.thenBy { it.dueDateTime ?: Long.MAX_VALUE }
                )
                SortOrder.CREATED_AT -> open.sortedByDescending { it.createdAt }
                else -> open.sortedBy { it.title.lowercase() }
            }
            return listOf(TodoGroup(label = null, overdue = false, todos = sorted))
        }
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        fun dayOf(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val byUrgency = compareBy<TodoEntity> { it.dueDateTime ?: Long.MAX_VALUE }.thenByDescending { it.priority }

        val overdue = open.filter { it.dueDateTime != null && it.dueDateTime < now }.sortedWith(byUrgency)
        val dueToday = open.filter {
            it.dueDateTime != null && it.dueDateTime >= now && dayOf(it.dueDateTime) == today
        }.sortedWith(byUrgency)
        val later = open.filter {
            it.dueDateTime != null && it.dueDateTime >= now && dayOf(it.dueDateTime) != today
        }.sortedWith(byUrgency)
        val undated = open.filter { it.dueDateTime == null }
            .sortedWith(compareByDescending<TodoEntity> { it.priority }.thenByDescending { it.createdAt })

        return listOfNotNull(
            overdue.takeIf { it.isNotEmpty() }?.let { TodoGroup("Overdue", true, it) },
            dueToday.takeIf { it.isNotEmpty() }?.let { TodoGroup("Today", false, it) },
            later.takeIf { it.isNotEmpty() }?.let { TodoGroup("Later", false, it) },
            undated.takeIf { it.isNotEmpty() }?.let { TodoGroup("No date", false, it) },
        )
    }

    fun setSearchQuery(query: String) = filters.update { it.copy(query = query) }

    /** From an empty search: the typed text becomes a todo, and the search clears. */
    fun quickAdd(title: String) {
        val clean = title.trim().take(200)
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val priority = settings.defaultPriority.first()
            val limits = clampTodoLimits(priority, 2, 1)
            engine.save(
                TodoEntity(
                    title = clean,
                    priority = priority,
                    maxSnoozeCount = limits.maxSnoozeCount,
                    maxRescheduleCount = limits.maxRescheduleCount,
                    createdAt = System.currentTimeMillis(),
                )
            )
            filters.update { it.copy(query = "") }
        }
    }
    fun setFilterPriority(priority: Int?) = filters.update { it.copy(priority = priority) }
    fun setSortOrder(order: SortOrder) = filters.update { it.copy(sort = order) }

    /** The speed bump a tap on this todo's ring has to pass; none means it completes at once. */
    fun doneGuardFor(todo: TodoEntity): Guard = uiState.value.guards.forTodo(todo, GuardAction.DONE)

    fun complete(todo: TodoEntity) {
        viewModelScope.launch { engine.complete(todo.id) }
    }

    fun reopen(todo: TodoEntity) {
        viewModelScope.launch { engine.reopen(todo.id) }
    }

    /** A fresh copy: nothing ticked or snoozed, and no due time in the past. */
    fun duplicate(todo: TodoEntity, onDone: (Long) -> Unit) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = engine.save(
                todo.unlinked().copy(
                    id = 0L,
                    title = "${todo.title} (copy)".take(200),
                    isCompleted = false,
                    completedAt = null,
                    snoozeCount = 0,
                    rescheduleCount = 0,
                    postponedTo = null,
                    snoozedUntil = null,
                    nagCount = 0,
                    lastAlarmAt = null,
                    deletedAt = null,
                    dueDateTime = todo.dueDateTime?.takeIf { it > now },
                    createdAt = now,
                    checklistJson = todo.checklistJson?.let { json ->
                        checklistFromJson(json).map { it.copy(done = false) }.toChecklistJson()
                    },
                )
            )
            onDone(id)
        }
    }

    fun moveToTrash(todo: TodoEntity) {
        viewModelScope.launch {
            engine.moveToTrash(todo.id)
            justTrashed.value = todo
        }
    }

    fun undoTrash() {
        val todo = justTrashed.value ?: return
        justTrashed.value = null
        viewModelScope.launch { engine.restoreFromTrash(todo.id) }
    }

    fun clearUndo() {
        justTrashed.value = null
    }

    fun restore(todo: TodoEntity) {
        viewModelScope.launch { engine.restoreFromTrash(todo.id) }
    }

    fun deleteForever(todo: TodoEntity) {
        viewModelScope.launch { engine.deleteForever(todo.id) }
    }

    fun emptyTrash() {
        val all = uiState.value.trashed
        viewModelScope.launch { all.forEach { engine.deleteForever(it.id) } }
    }
}
