package com.focusforceplus.app.util

import android.content.Context
import com.focusforceplus.app.compliance.ComplianceLimits
import com.focusforceplus.app.compliance.GuardAction
import com.focusforceplus.app.data.db.entity.TodoEntity
import com.focusforceplus.app.data.model.checklistFromJson
import com.focusforceplus.app.data.model.toChecklistJson
import com.focusforceplus.app.data.repository.NavRepository
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.data.repository.TodoRepository
import com.focusforceplus.app.service.TodoAlarmService
import com.focusforceplus.app.ui.alarm.TodoAlarmActivity
import com.focusforceplus.app.ui.navigation.AppModule
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single place that decides what a todo is doing right now.
 *
 * Every way of touching a todo ends up here: the list, the editor, the alarm
 * screen, a button on a notification, an alarm going off, a reboot. Each of them
 * changes the stored todo and then calls [apply], which looks at that stored state
 * and makes the one pending wake-up and the one notification match it.
 *
 * That is the whole design, and it is what replaced a set of code paths that each
 * updated alarms and notifications by hand. With those, the alarm screen and a
 * notification button could both act on the same alarm from stale copies of the
 * todo, and a single snooze could be spent twice. Here an action reads the todo
 * inside a lock, checks that it still applies, and does nothing if another surface
 * was faster.
 *
 * ## How persistent a todo is
 *
 * - **Low**: one ordinary notification when it is due. Nothing more.
 * - **Medium**: comes up on screen without sound, then stays as a notification that
 *   cannot be swiped away, and comes back a couple of times.
 * - **High**: rings, stays, and rings again every few minutes.
 *
 * "Again" is bounded by [com.focusforceplus.app.compliance.NagPolicy]: after its
 * repeats a todo rests as a notification until the next daily reminder time starts
 * a new round. Nothing here can ring without end, and the todo itself can always
 * be edited, moved or deleted from the list.
 */
@Singleton
class TodoReminderEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: TodoRepository,
    private val settings: SettingsRepository,
    private val navRepository: NavRepository,
    private val scheduler: TodoAlarmScheduler,
    private val notifications: TodoNotifications,
) {
    enum class Outcome { OK, NOT_FOUND, NOT_ALLOWED }

    private companion object {
        const val CATCH_UP_MILLIS = TodoReminderRules.CATCH_UP_MILLIS
        const val MINUTE = TodoReminderRules.MINUTE
    }

    private val mutex = Mutex()

    /**
     * Missed wake-ups that are waiting for their turn: todo id to the moment it will
     * ring. Several todos that came due while nothing could ring (phone off, module
     * off) take turns a few minutes apart, like at a daily reminder time, instead of
     * all ringing at once. Only kept in memory: after a restart [syncAll] deals the
     * turns out again.
     */
    private val catchUpTurns = HashMap<Long, Long>()

    private fun now() = System.currentTimeMillis()

    private suspend fun todosOn(): Boolean =
        runCatching { navRepository.isModuleOn(AppModule.TODOS) }.getOrDefault(true)

    // ── State, derived from the stored todo ───────────────────────────────────

    fun isSnoozed(todo: TodoEntity, now: Long = now()): Boolean = TodoReminderRules.isSnoozed(todo, now)

    /**
     * True when the todo is asking for an answer right now: it is past due, or it
     * has no due date and a daily reminder has brought it up.
     */
    fun isActionable(todo: TodoEntity, now: Long = now()): Boolean {
        if (todo.isCompleted || todo.isInTrash || isSnoozed(todo, now)) return false
        val due = todo.dueDateTime
        return if (due != null) due <= now else todo.lastAlarmAt != null
    }

    fun canSnooze(todo: TodoEntity, now: Long = now()): Boolean =
        isActionable(todo, now) && todo.snoozeCount < todo.maxSnoozeCount

    fun canReschedule(todo: TodoEntity): Boolean =
        !todo.isCompleted && !todo.isInTrash &&
            (todo.priority != 2 || todo.rescheduleCount < todo.maxRescheduleCount)

    // ── Saving from the editor ────────────────────────────────────────────────

    /**
     * Inserts or updates a todo from the editor and returns its id. Moving the due
     * time or changing the priority starts a fresh occurrence: counters and any
     * running snooze belong to the old one.
     */
    suspend fun save(todo: TodoEntity): Long = mutex.withLock {
        val now = now()
        val existing = if (todo.id != 0L) repository.getTodoById(todo.id) else null
        val id: Long
        if (existing == null) {
            // New. Or its row was deleted for good while the editor was open: then it
            // starts over as a new todo, without the old row's event and synced task.
            val fresh = if (todo.id == 0L) todo else todo.unlinked().copy(id = 0L)
            id = repository.insertTodo(TodoReminderRules.quietIfOverdue(fresh, now))
        } else {
            // Only what an editor shows comes from [todo]. Everything else is read
            // from the stored row here, inside the lock: an editor that was open for
            // a while would otherwise write back an old "not done" or an old place in
            // the trash, or drop the link to a calendar event or synced task that
            // was made in the meantime.
            val edited = existing.copy(
                title = todo.title,
                description = todo.description,
                iconKey = todo.iconKey,
                dueDateTime = todo.dueDateTime,
                priority = todo.priority,
                maxSnoozeCount = todo.maxSnoozeCount,
                maxRescheduleCount = todo.maxRescheduleCount,
                isRecurring = todo.isRecurring,
                recurringPattern = todo.recurringPattern,
                checklistJson = todo.checklistJson,
                guards = todo.guards,
                listId = todo.listId,
            )
            val freshOccurrence = existing.dueDateTime != todo.dueDateTime ||
                existing.priority != todo.priority
            val merged = if (freshOccurrence) {
                TodoReminderRules.quietIfOverdue(
                    edited.copy(
                        snoozeCount = 0,
                        rescheduleCount = 0,
                        snoozedUntil = null,
                        nagCount = 0,
                        lastAlarmAt = null,
                    ),
                    now,
                )
            } else {
                edited
            }
            repository.updateTodo(merged)
            id = existing.id
        }
        repository.getTodoById(id)?.let { apply(it, now) }
        refreshSummary()
        id
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    suspend fun complete(todoId: Long): Outcome = act(todoId) { todo, now ->
        if (todo.isCompleted) return@act Outcome.OK
        val done = todo.copy(isCompleted = true, completedAt = now, snoozedUntil = null)
        repository.updateTodo(done)
        apply(done, now)

        val pattern = todo.recurringPattern
        val due = todo.dueDateTime
        if (todo.isRecurring && pattern != null && due != null) {
            val next = todo.unlinked().copy(
                id = 0L,
                dueDateTime = nextOccurrenceMillis(due, pattern),
                isCompleted = false,
                completedAt = null,
                snoozeCount = 0,
                rescheduleCount = 0,
                postponedTo = null,
                snoozedUntil = null,
                nagCount = 0,
                lastAlarmAt = null,
                // The next round starts with an empty checklist, not a ticked one.
                checklistJson = checklistFromJson(todo.checklistJson)
                    .takeIf { it.isNotEmpty() }
                    ?.map { it.copy(done = false) }
                    ?.toChecklistJson(),
            )
            val nextId = repository.insertTodo(next)
            apply(next.copy(id = nextId), now)
        }
        Outcome.OK
    }

    /** Puts a completed todo back on the open list. */
    suspend fun reopen(todoId: Long): Outcome = act(todoId) { todo, now ->
        if (!todo.isCompleted) return@act Outcome.OK
        // Bringing a todo back should not set off an alarm on the spot. An overdue
        // one waits quietly for the next daily reminder, and one without a date
        // starts over as if it were new.
        val overdue = todo.dueDateTime != null && todo.dueDateTime <= now
        val reopened = todo.copy(
            isCompleted = false,
            completedAt = null,
            snoozeCount = 0,
            rescheduleCount = 0,
            snoozedUntil = null,
            nagCount = if (overdue) TodoEntity.NAG_DISMISSED else 0,
            lastAlarmAt = if (todo.dueDateTime == null) null else todo.lastAlarmAt,
        )
        repository.updateTodo(reopened)
        apply(reopened, now)
        Outcome.OK
    }

    suspend fun snooze(todoId: Long, minutes: Int): Outcome = act(todoId) { todo, now ->
        // Already snoozed means another surface was faster. That is not an error,
        // and above all it must not cost a second snooze.
        if (isSnoozed(todo, now)) return@act Outcome.OK
        if (!canSnooze(todo, now)) return@act Outcome.NOT_ALLOWED
        val snoozed = todo.copy(
            snoozeCount = todo.snoozeCount + 1,
            snoozedUntil = now + minutes.coerceIn(1, 24 * 60) * MINUTE,
            nagCount = 0,
        )
        repository.updateTodo(snoozed)
        apply(snoozed, now)
        Outcome.OK
    }

    suspend fun reschedule(todoId: Long, newDueMillis: Long): Outcome = act(todoId) { todo, now ->
        if (!canReschedule(todo) || newDueMillis <= now) return@act Outcome.NOT_ALLOWED
        val moved = todo.copy(
            dueDateTime = newDueMillis,
            postponedTo = newDueMillis,
            rescheduleCount = todo.rescheduleCount + 1,
            snoozeCount = 0,
            snoozedUntil = null,
            nagCount = 0,
            lastAlarmAt = null,
        )
        repository.updateTodo(moved)
        apply(moved, now)
        Outcome.OK
    }

    /**
     * "Not now" without a time: the todo stays open and stays visible, but stops
     * coming back until the next daily reminder. High priority has no dismiss.
     */
    suspend fun dismiss(todoId: Long): Outcome = act(todoId) { todo, now ->
        if (todo.priority == 2) return@act Outcome.NOT_ALLOWED
        val quiet = todo.copy(nagCount = TodoEntity.NAG_DISMISSED, snoozedUntil = null)
        repository.updateTodo(quiet)
        // A daily reminder that is going through the todos right now may still have
        // this one's turn ahead. Dismissed means that turn is skipped as well.
        scheduler.cancelActivation(todo.id)
        apply(quiet, now)
        if (todo.priority == 0) notifications.cancel(todo.id)
        Outcome.OK
    }

    suspend fun moveToTrash(todoId: Long): Outcome = act(todoId) { todo, now ->
        repository.moveToTrash(todo.id)
        apply(todo.copy(deletedAt = now), now)
        Outcome.OK
    }

    suspend fun restoreFromTrash(todoId: Long): Outcome = act(todoId) { todo, now ->
        repository.restoreFromTrash(todo.id)
        // Like reopening: an overdue todo comes back quietly and waits for the next
        // daily reminder instead of ringing the moment it is restored.
        val overdue = !todo.isCompleted && todo.dueDateTime != null && todo.dueDateTime <= now
        val restored = todo.copy(
            deletedAt = null,
            snoozedUntil = null,
            nagCount = if (overdue) TodoEntity.NAG_DISMISSED else todo.nagCount,
        )
        repository.updateTodo(restored)
        apply(restored, now)
        Outcome.OK
    }

    suspend fun deleteForever(todoId: Long) = mutex.withLock {
        clear(todoId)
        repository.deleteForever(todoId)
        refreshSummary()
    }

    private suspend fun act(
        todoId: Long,
        block: suspend (todo: TodoEntity, now: Long) -> Outcome,
    ): Outcome = mutex.withLock {
        val todo = repository.getTodoById(todoId) ?: run {
            clear(todoId)
            return@withLock Outcome.NOT_FOUND
        }
        val outcome = block(todo, now())
        refreshSummary()
        outcome
    }

    // ── Events ────────────────────────────────────────────────────────────────

    /**
     * The todo's wake-up fired. [activation] marks a wake-up started by a daily
     * reminder time, which opens a new round of repeats.
     */
    suspend fun onWake(todoId: Long, activation: Boolean) = mutex.withLock {
        val now = now()
        val stored = repository.getTodoById(todoId)
        if (stored == null || !todosOn() || stored.isCompleted || stored.isInTrash) {
            clear(todoId)
            refreshSummary()
            return@withLock
        }
        // A wake-up that is no longer right (the todo was snoozed or moved in the
        // meantime) only puts the correct one back.
        if (isSnoozed(stored, now) || (stored.dueDateTime ?: 0L) > now + 1_000L) {
            apply(stored, now)
            return@withLock
        }
        val todo = if (activation) stored.copy(nagCount = 0) else stored
        if (!activation && todo.priority >= 1 && todo.lastAlarmAt != null) {
            val policy = settings.nagPolicy(todo.priority).first()
            val spent = todo.nagCount == TodoEntity.NAG_DISMISSED || todo.nagCount > policy.maxRepeats
            if (spent) {
                apply(todo, now)
                refreshSummary()
                return@withLock
            }
        }
        ring(todo, now)
        refreshSummary()
    }

    /** The alarm service stopped ringing without an answer. */
    suspend fun onRingEnded(todoId: Long) = mutex.withLock {
        repository.getTodoById(todoId)?.let { apply(it) } ?: clear(todoId)
        refreshSummary()
    }

    /**
     * A daily reminder time. Open medium and high todos that are overdue or have
     * no date each get their turn, a few minutes apart so they never all ring at
     * once; low-priority ones are listed in one quiet overview.
     */
    suspend fun onDailyReminder(slot: Int, hour: Int, minute: Int) = mutex.withLock {
        scheduler.scheduleDigestSlot(slot, hour, minute)
        if (!todosOn()) return@withLock
        val now = now()
        val open = repository.getOpenTodosOnce().filter { !isSnoozed(it, now) }
        fun isDue(todo: TodoEntity) = todo.dueDateTime == null || todo.dueDateTime <= now

        val loud = open
            .filter { it.priority >= 1 && isDue(it) }
            .sortedWith(
                compareByDescending<TodoEntity> { it.priority }
                    .thenBy { it.dueDateTime ?: Long.MAX_VALUE }
                    .thenBy { it.createdAt }
            )
        val gap = settings.undatedAlarmGapMinutes.first().coerceAtLeast(1) * MINUTE
        loud.forEachIndexed { index, todo ->
            scheduler.scheduleActivation(todo.id, now + CATCH_UP_MILLIS + index * gap)
        }

        val quiet = open.filter { it.priority == 0 && isDue(it) }
        if (quiet.isNotEmpty()) notifications.postDaily(quiet.map { it.title }) else notifications.cancelDaily()
    }

    // ── Bringing everything in line ───────────────────────────────────────────

    suspend fun sync(todoId: Long) = mutex.withLock {
        repository.getTodoById(todoId)?.let { apply(it) } ?: clear(todoId)
        refreshSummary()
    }

    /** After a reboot, an update, a clock change, or switching the module back on. */
    suspend fun syncAll() = mutex.withLock {
        val all = repository.getAllActiveOnce()
        retireLegacy(all)
        catchUpTurns.clear()
        if (!todosOn()) {
            all.forEach { clear(it.id) }
            scheduler.cancelAllDigestAlarms()
            notifications.cancelSummary()
            notifications.cancelDaily()
            return@withLock
        }
        val now = now()
        // Wake-ups that were missed take turns, the most important one first.
        val gap = settings.undatedAlarmGapMinutes.first().coerceAtLeast(1) * MINUTE
        catchUpTurns.putAll(TodoReminderRules.catchUpTurns(all, now, gap))
        all.forEach { apply(it, now) }
        scheduler.scheduleDigestAlarms(settings.digestTimes.first())
        refreshSummary()
    }

    /**
     * Once after the update from 0.9.3 or older: its notifications and alarms used
     * other ids, and an update leaves both in place, so an old alarm would fire
     * next to the new one. Only once, because the old notification ids were
     * computed from the todo id and run into other notifications as ids grow.
     */
    private suspend fun retireLegacy(todos: List<TodoEntity>) {
        if (runCatching { settings.legacyTodoAlarmsRetired.first() }.getOrDefault(true)) return
        val ids = todos.map { it.id }
        runCatching { notifications.cancelLegacy(ids) }
        runCatching { scheduler.cancelLegacyAlarms(ids) }
        runCatching { settings.markLegacyTodoAlarmsRetired() }
    }

    /** The todo module was switched off: nothing of it may fire any more. */
    suspend fun pauseAll() = mutex.withLock {
        repository.getAllActiveOnce().forEach { clear(it.id) }
        scheduler.cancelAllDigestAlarms()
        notifications.cancelSummary()
        notifications.cancelDaily()
    }

    /** Housekeeping: empties old trash and, if the user asked for it, old done todos. */
    suspend fun cleanUp() {
        val now = now()
        val retention = settings.trashRetentionDays.first()
            .coerceIn(1, ComplianceLimits.MAX_TRASH_RETENTION_DAYS)
        repository.purgeTrashedBefore(now - retention * 24L * 60L * MINUTE)
        val autoDeleteDays = settings.autoDeleteDays.first()
        if (autoDeleteDays > 0) {
            repository.trashCompletedBefore(now - autoDeleteDays * 24L * 60L * MINUTE)
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private suspend fun ring(todo: TodoEntity, now: Long) {
        val rung = todo.copy(
            lastAlarmAt = now,
            nagCount = if (todo.nagCount < 0) 1 else todo.nagCount + 1,
            snoozedUntil = null,
        )
        repository.updateTodo(rung)
        catchUpTurns.remove(rung.id)

        if (rung.priority == 0) {
            val doneDirect = settings.guardSettings.first().forTodo(rung, GuardAction.DONE).isNone
            notifications.post(rung.id, notifications.lowDue(rung, doneDirect))
            scheduler.cancelWake(rung.id)
            return
        }

        // The service owns the sound and keeps the notification up while it rings.
        // Starting it can be refused by the system; the alarm must not depend on it.
        val serviceStarted = runCatching {
            context.startForegroundService(TodoAlarmService.ringIntent(context, rung))
        }.isSuccess
        if (!serviceStarted) notifications.post(rung.id, notifications.ringing(rung))

        // This runs inside the alarm broadcast, which is what allows opening the
        // alarm screen from the background. If the screen is already up with another
        // todo, this one waits its turn there instead of replacing it.
        if (TodoAlarmActivity.claimScreen(rung.id)) {
            runCatching { context.startActivity(TodoAlarmActivity.intent(context, rung.id)) }
        }

        nextWake(rung, now)?.let { scheduler.scheduleWake(rung.id, it) } ?: scheduler.cancelWake(rung.id)
    }

    /** Makes the wake-up and the notification of one todo match its stored state. */
    private suspend fun apply(todo: TodoEntity, now: Long = now()) {
        if (!todosOn() || todo.isCompleted || todo.isInTrash) {
            clear(todo.id)
            return
        }
        val wake = nextWake(todo, now)
        if (wake != null) scheduler.scheduleWake(todo.id, wake) else scheduler.cancelWake(todo.id)

        when {
            isSnoozed(todo, now) -> {
                TodoAlarmService.stop(context, todo.id)
                if (todo.priority >= 1) notifications.post(todo.id, notifications.snoozed(todo))
                else notifications.cancel(todo.id)
            }
            isActionable(todo, now) -> {
                // A ringing todo keeps its ringing notification; the service hands
                // over to the waiting one when it stops.
                if (todo.priority >= 1 && !TodoAlarmService.isRinging(todo.id)) {
                    val guards = settings.guardSettings.first()
                    notifications.post(
                        todo.id,
                        notifications.waiting(
                            todo = todo,
                            nextRingAt = wake,
                            doneDirect = guards.forTodo(todo, GuardAction.DONE).isNone,
                            snoozeDirect = guards.forTodo(todo, GuardAction.SNOOZE).isNone,
                        ),
                    )
                }
            }
            else -> {
                TodoAlarmService.stop(context, todo.id)
                notifications.cancel(todo.id)
            }
        }
    }

    /** When this todo should next make itself heard, or null if it should not. */
    private suspend fun nextWake(todo: TodoEntity, now: Long): Long? {
        val policy = if (todo.priority >= 1) settings.nagPolicy(todo.priority).first() else null
        return TodoReminderRules.nextWake(todo, now, policy, catchUpTurns[todo.id])
    }

    private fun clear(todoId: Long) {
        catchUpTurns.remove(todoId)
        scheduler.cancelAllFor(todoId)
        TodoAlarmService.stop(context, todoId)
        notifications.cancel(todoId)
    }

    /**
     * The header the medium and high todo notifications collapse under.
     *
     * It stays for as long as one of them is showing. Android removes a group's
     * notifications together with its header, so taking the header away when a
     * single todo is left would take that todo's notification with it. With one
     * todo the system shows just that notification, without the header.
     */
    private suspend fun refreshSummary() {
        if (!todosOn()) {
            notifications.cancelSummary()
            return
        }
        val now = now()
        val showing = repository.getOpenTodosOnce().count {
            it.priority >= 1 && (isActionable(it, now) || isSnoozed(it, now))
        }
        if (showing >= 1) notifications.postSummary(showing) else notifications.cancelSummary()
    }
}
