package com.focusforceplus.app.data.calendar

import com.focusforceplus.app.data.db.entity.TodoEntity
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.data.repository.TodoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps todos with a date as events in the calendar the user picked: added when a
 * todo gets a date, moved with it, removed when it is done or deleted. Watching the
 * table instead of every place that changes a todo means no path is forgotten.
 *
 * An event the user deletes in the calendar stays deleted: the todo is marked
 * [LEFT_OUT] and gets no new one until it is done or loses its date.
 */
@Singleton
class CalendarTodoMirror @Inject constructor(
    private val calendar: CalendarRepository,
    private val todos: TodoRepository,
    private val settings: SettingsRepository,
) {
    private val mutex = Mutex()

    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(todos.observeCalendarCandidates(), settings.calendarTodoTarget, ::Pair)
                .debounce(1_500)
                .collectLatest { (candidates, target) -> runCatching { sync(candidates, target) } }
        }
    }

    private suspend fun sync(candidates: List<TodoEntity>, target: Long?) = mutex.withLock {
        if (!calendar.canWrite()) return@withLock
        val stored = calendar.storedEvents(candidates.mapNotNull { it.calendarEventId?.takeIf { id -> id > 0 } })
        // Every event this pass leaves linked to a todo; the rest of ours are leftovers.
        val kept = mutableSetOf<Long>()
        for (todo in candidates) {
            val due = todo.dueDateTime
            val wanted = target != null && due != null && !todo.isCompleted && todo.deletedAt == null
            val eventId = todo.calendarEventId
            when {
                !wanted -> {
                    if (eventId != null && eventId > 0) calendar.deleteEvent(eventId)
                    if (eventId != null) todos.setCalendarEventId(todo.id, null)
                }
                eventId == LEFT_OUT -> Unit
                eventId == null -> {
                    // Long overdue todos stay out; a calendar full of old ones helps nobody.
                    if (due!! >= System.currentTimeMillis() - 24 * 60 * 60_000L) {
                        calendar.insertTodoEvent(target!!, todo.title, due)?.let {
                            todos.setCalendarEventId(todo.id, it)
                            kept += it
                        }
                    }
                }
                else -> {
                    val event = stored[eventId]
                    when {
                        event == null || event.deleted -> todos.setCalendarEventId(todo.id, LEFT_OUT)
                        event.calendarId != target -> {
                            calendar.deleteEvent(eventId)
                            val moved = calendar.insertTodoEvent(target!!, todo.title, due!!)
                            todos.setCalendarEventId(todo.id, moved)
                            moved?.let { kept += it }
                        }
                        else -> {
                            kept += eventId
                            if (event.title != todo.title || event.start != due) {
                                calendar.updateTodoEvent(eventId, target!!, todo.title, due!!)
                            }
                        }
                    }
                }
            }
        }
        // Todo events nothing points to any more (app data cleared, a backup put
        // back): they would show twice. Only events with FocusForce+'s marker.
        if (target != null) {
            calendar.todoEventIds(target).filter { it !in kept }.forEach { calendar.deleteEvent(it) }
        }
    }

    companion object {
        /** The user removed this todo's event in the calendar: leave it out. */
        const val LEFT_OUT = -1L
    }
}
