package com.focusforceplus.app.data.calendar

import com.focusforceplus.app.data.db.entity.TodoEntity
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.data.repository.TodoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps todos with a date as events in the calendar the user picked: added when a
 * todo gets a date, moved with it, removed when it is done or deleted. Watching the
 * table instead of every place that changes a todo means no path is forgotten.
 *
 * An event the user deletes in the calendar stays deleted: the todo is marked
 * [LEFT_OUT] and gets no new one until it is done or loses its date.
 *
 * ## What it may touch
 *
 * The calendar belongs to the user, and an event id is only a number that is
 * different on every phone. So an event is changed or deleted only when a todo
 * points at it **and** it carries FocusForce+'s marker. A todo restored from a
 * backup of another phone still has that phone's id, which here can be anybody's
 * dentist appointment; the marker is what keeps that one safe. For the same reason
 * nothing is tidied up on a guess: events of ours that no todo points at are left
 * alone, because another device with the same account may be mirroring its own
 * todos into the same calendar.
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

    /**
     * Picks the calendar todos go into, or none. The events in the calendar used
     * until now are removed first, and every todo starts over without a link, so a
     * new calendar gets all of them again.
     */
    suspend fun setTarget(calendarId: Long?) = withContext(NonCancellable) {
        mutex.withLock {
            if (settings.calendarTodoTarget.first() == calendarId) return@withLock
            removeOwnEvents(todos.getAllOnce())
            todos.clearCalendarEventIds()
            settings.saveCalendarTodoTarget(calendarId)
        }
    }

    /**
     * Runs [block], which replaces every todo at once (a backup being put back).
     * The events of the todos that go are removed once the new ones are in; those
     * come without a link and get their events on the next pass.
     */
    suspend fun <T> whileTodosAreReplaced(block: suspend () -> T): T = mutex.withLock {
        val before = todos.getAllOnce()
        val result = block()
        runCatching { removeOwnEvents(before) }
        result
    }

    private suspend fun removeOwnEvents(of: List<TodoEntity>) {
        if (!calendar.canWrite()) return
        val ids = of.mapNotNull { it.calendarEventId?.takeIf { id -> id > 0 } }
        val stored = calendar.storedEvents(ids) ?: return
        stored.filterValues { it.ours && !it.deleted }.keys.forEach { calendar.deleteEvent(it) }
    }

    private suspend fun sync(candidates: List<TodoEntity>, target: Long?) = mutex.withLock {
        if (target == null) {
            // Not mirroring. A link without a calendar comes from another phone: a
            // restored backup keeps the todos, but not this setting. Its id means
            // nothing here, so it is only forgotten and nothing is deleted.
            // Switching off in Settings removes the events itself, see setTarget.
            if (candidates.any { it.calendarEventId != null }) todos.clearCalendarEventIds()
            return@withLock
        }
        if (!calendar.canWrite()) return@withLock
        // A calendar that is gone took its events along (its account was removed).
        // That is not the user deleting them one by one: wait for another calendar.
        if (calendar.calendarExists(target) != true) return@withLock
        // If the calendar cannot be read right now, do nothing rather than guess.
        val stored = calendar.storedEvents(candidates.mapNotNull { it.calendarEventId?.takeIf { id -> id > 0 } })
            ?: return@withLock

        // Events a todo points at for good reason; one event is never given to two.
        val taken = candidates.mapNotNull { todo ->
            todo.calendarEventId?.takeIf { stored[it]?.let { event -> event.ours && !event.deleted } == true }
        }.toMutableSet()
        // Our events in this calendar, looked up only when a todo needs a place.
        var existing: Map<Long, CalendarRepository.StoredEvent>? = null

        suspend fun place(todo: TodoEntity, due: Long) {
            // Long overdue todos stay out; a calendar full of old ones helps nobody.
            if (due < System.currentTimeMillis() - 24 * 60 * 60_000L) {
                if (todo.calendarEventId != null) todos.setCalendarEventId(todo.id, null)
                return
            }
            val ours = existing ?: (calendar.todoEvents(target) ?: emptyMap()).also { existing = it }
            // The same todo may have its event here already: this phone's todos were
            // put back from a backup, or another device mirrors the same todo.
            val twin = ours.entries
                .firstOrNull { (id, event) -> id !in taken && event.title == todo.title && event.start == due }
                ?.key
            val id = twin ?: calendar.insertTodoEvent(target, todo.title, due)
            if (id != todo.calendarEventId) todos.setCalendarEventId(todo.id, id)
            id?.let { taken += it }
        }

        for (todo in candidates) {
            val due = todo.dueDateTime
            val wanted = due != null && !todo.isCompleted && todo.deletedAt == null
            val eventId = todo.calendarEventId
            val event = eventId?.let { stored[it] }
            when {
                !wanted -> {
                    if (eventId != null && event != null && event.ours && !event.deleted) calendar.deleteEvent(eventId)
                    if (eventId != null) todos.setCalendarEventId(todo.id, null)
                }
                eventId == LEFT_OUT -> Unit
                eventId == null -> place(todo, due!!)
                // The id points at something that is not ours: a leftover from
                // another phone. That event is not touched; the todo starts over.
                event != null && !event.ours -> place(todo, due!!)
                event == null || event.deleted -> todos.setCalendarEventId(todo.id, LEFT_OUT)
                event.calendarId != target -> {
                    calendar.deleteEvent(eventId)
                    taken -= eventId
                    place(todo, due!!)
                }
                event.title != todo.title || event.start != due ->
                    calendar.updateTodoEvent(eventId, target, todo.title, due!!)
            }
        }
    }

    companion object {
        /** The user removed this todo's event in the calendar: leave it out. */
        const val LEFT_OUT = -1L
    }
}
