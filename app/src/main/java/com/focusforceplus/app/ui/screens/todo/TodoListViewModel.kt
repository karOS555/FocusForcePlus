package com.focusforceplus.app.ui.screens.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.data.db.entity.TodoEntity
import com.focusforceplus.app.data.repository.TodoRepository
import com.focusforceplus.app.util.TodoAlarmHelper
import com.focusforceplus.app.util.TodoNotificationHelper
import com.focusforceplus.app.util.nextOccurrenceMillis
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

enum class SortOrder(val label: String) {
    DEFAULT("Smart (overdue first)"),
    PRIORITY("Priority"),
    DUE_DATE("Due date"),
    CREATED_AT("Date created"),
    TITLE("Title A–Z"),
}

data class TodoListUiState(
    val openTodos: List<TodoEntity> = emptyList(),
    val completedTodos: List<TodoEntity> = emptyList(),
    val searchQuery: String = "",
    val filterPriority: Int? = null,
    val sortOrder: SortOrder = SortOrder.DEFAULT,
    val deletedTodo: TodoEntity? = null,
)

@HiltViewModel
class TodoListViewModel @Inject constructor(
    private val repository: TodoRepository,
    private val alarmHelper: TodoAlarmHelper,
    private val notifHelper: TodoNotificationHelper,
) : ViewModel() {

    private val _searchQuery    = MutableStateFlow("")
    private val _filterPriority = MutableStateFlow<Int?>(null)
    private val _sortOrder      = MutableStateFlow(SortOrder.DEFAULT)
    private val _deletedTodo    = MutableStateFlow<TodoEntity?>(null)

    val uiState: StateFlow<TodoListUiState> = combine(
        repository.getAllTodos(),
        _searchQuery,
        _filterPriority,
        _sortOrder,
        _deletedTodo,
    ) { todos, query, priority, sort, deleted ->
        val now = System.currentTimeMillis()

        fun List<TodoEntity>.applyFilters(): List<TodoEntity> {
            var list = this
            if (query.isNotBlank()) {
                list = list.filter {
                    it.title.contains(query, ignoreCase = true) ||
                    it.description?.contains(query, ignoreCase = true) == true
                }
            }
            if (priority != null) list = list.filter { it.priority == priority }
            return list
        }

        val open = todos
            .filter { !it.isCompleted }
            .applyFilters()
            .let { list ->
                when (sort) {
                    SortOrder.DEFAULT -> list.sortedWith(compareBy(
                        { when {
                            it.dueDateTime != null && it.dueDateTime < now -> 0
                            it.dueDateTime != null -> 1
                            else -> 2
                        }},
                        { it.dueDateTime ?: Long.MAX_VALUE },
                        { -it.priority },
                    ))
                    SortOrder.PRIORITY  -> list.sortedByDescending { it.priority }
                    SortOrder.DUE_DATE  -> list.sortedWith(compareBy(
                        { it.dueDateTime == null },
                        { it.dueDateTime ?: Long.MAX_VALUE },
                    ))
                    SortOrder.CREATED_AT -> list.sortedByDescending { it.createdAt }
                    SortOrder.TITLE      -> list.sortedBy { it.title.lowercase() }
                }
            }

        val completed = todos
            .filter { it.isCompleted }
            .applyFilters()
            .sortedByDescending { it.completedAt }

        TodoListUiState(
            openTodos      = open,
            completedTodos = completed,
            searchQuery    = query,
            filterPriority = priority,
            sortOrder      = sort,
            deletedTodo    = deleted,
        )
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000),
        initialValue = TodoListUiState(),
    )

    fun setSearchQuery(query: String)       = _searchQuery.update { query }
    fun setFilterPriority(priority: Int?)   = _filterPriority.update { priority }
    fun setSortOrder(order: SortOrder)      = _sortOrder.update { order }
    fun clearDeletedTodo()                  = _deletedTodo.update { null }

    fun toggleComplete(todo: TodoEntity) {
        viewModelScope.launch {
            if (todo.isCompleted) {
                val reopened = todo.copy(
                    isCompleted     = false,
                    completedAt     = null,
                    snoozeCount     = 0,
                    rescheduleCount = 0,
                )
                repository.updateTodo(reopened)
                val due = todo.dueDateTime
                val now = System.currentTimeMillis()
                when {
                    due != null && due > now -> alarmHelper.scheduleTodoAlarm(reopened)
                    due != null && due <= now -> {
                        // Overdue — repost digest notification so it reappears immediately (D fix)
                        notifHelper.showNotification(
                            TodoNotificationHelper.todoDigestNotificationId(todo.id),
                            notifHelper.buildPerTodoReminderNotification(
                                todo.id, todo.title, todo.priority, isOverdue = true,
                            ),
                        )
                    }
                }
            } else {
                repository.markCompleted(todo.id, System.currentTimeMillis())
                alarmHelper.cancelTodoAlarm(todo.id)
                alarmHelper.cancelTodoSnoozeAlarm(todo.id)
                notifHelper.cancelNotification(TodoNotificationHelper.todoDigestNotificationId(todo.id))
                scheduleNextOccurrenceIfRecurring(todo)
            }
        }
    }

    fun deleteTodo(todo: TodoEntity) {
        viewModelScope.launch {
            alarmHelper.cancelTodoAlarm(todo.id)
            alarmHelper.cancelTodoSnoozeAlarm(todo.id)
            notifHelper.cancelNotification(TodoNotificationHelper.todoAlarmNotificationId(todo.id))
            notifHelper.cancelNotification(TodoNotificationHelper.todoDigestNotificationId(todo.id))
            repository.deleteTodo(todo)
            _deletedTodo.value = todo
        }
    }

    fun undoDelete(todo: TodoEntity) {
        viewModelScope.launch {
            val newId    = repository.insertTodo(todo.copy(id = 0L))
            val restored = todo.copy(id = newId)
            if (!restored.isCompleted &&
                restored.dueDateTime != null &&
                restored.dueDateTime > System.currentTimeMillis()
            ) {
                alarmHelper.scheduleTodoAlarm(restored)
            }
            _deletedTodo.value = null
        }
    }

    private suspend fun scheduleNextOccurrenceIfRecurring(todo: TodoEntity) {
        if (!todo.isRecurring || todo.recurringPattern == null || todo.dueDateTime == null) return
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
