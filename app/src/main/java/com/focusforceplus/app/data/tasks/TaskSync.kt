package com.focusforceplus.app.data.tasks

import android.content.Context
import com.focusforceplus.app.data.db.entity.TodoEntity
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.data.repository.TaskSyncTarget
import com.focusforceplus.app.data.repository.TodoRepository
import com.focusforceplus.app.util.TodoReminderEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** How the last sync went, for the settings page. */
data class TaskSyncStatus(
    val at: Long? = null,
    val message: String? = null,
    val ok: Boolean = true,
)

/**
 * Keeps todos and one list of a tasks app (OpenTasks or Tasks.org) in step, both
 * ways. Runs after changes on either side while the app is alive, at app start,
 * and from a periodic worker; see [TaskSyncPlanner] for the rules.
 */
@Singleton
class TaskSync @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val todos: TodoRepository,
    private val engine: TodoReminderEngine,
    private val settings: SettingsRepository,
) {
    private val mutex = Mutex()
    private val _status = MutableStateFlow(TaskSyncStatus())
    val status: StateFlow<TaskSyncStatus> = _status.asStateFlow()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope) {
        scope.launch {
            settings.taskSyncTarget.flatMapLatest { target ->
                val app = TaskApp.of(target?.authority)
                if (target == null || app == null) emptyFlow()
                else merge(todos.observeEverything().combine(settings.taskSyncTarget) { _, _ -> }, TaskStore(context, app).changes())
                    .onStart { emit(Unit) }
            }.debounce(3_000).collect { runCatching { syncNow() } }
        }
    }

    /** One full two-way pass. Safe to call any time; does nothing when not set up. */
    suspend fun syncNow(): TaskSyncStatus = withContext(Dispatchers.IO) {
        mutex.withLock {
            val target = settings.taskSyncTarget.first() ?: return@withLock TaskSyncStatus().also { _status.value = it }
            val result = runCatching { pass(target) }.getOrElse { TaskSyncStatus(System.currentTimeMillis(), "Sync failed: ${it.javaClass.simpleName}", ok = false) }
            _status.value = result
            result
        }
    }

    private suspend fun pass(target: TaskSyncTarget): TaskSyncStatus {
        val now = System.currentTimeMillis()
        val app = TaskApp.of(target.authority) ?: return TaskSyncStatus(now, "Unknown tasks app", ok = false)
        if (!app.isInstalled(context)) return TaskSyncStatus(now, "${app.label} is not installed", ok = false)
        if (!app.isGranted(context)) return TaskSyncStatus(now, "Access to ${app.label} is off", ok = false)
        val store = TaskStore(context, app)
        // Never act on a list that cannot be read or is gone: that would look like
        // every task was deleted.
        val lists = store.lists() ?: return TaskSyncStatus(now, "${app.label} could not be read", ok = false)
        if (lists.none { it.id == target.listId }) return TaskSyncStatus(now, "The list is gone in ${app.label}", ok = false)
        val rows = store.tasks(target.listId) ?: return TaskSyncStatus(now, "${app.label} could not be read", ok = false)
        val allDay = rows.filter { it.allDay }.map { it.task.id }.toSet()
        val there = rows.associate { it.task.id to it.task.fields }

        val all = todos.getAllOnce()
        val local = all.filter { it.taskSyncId != null || (!it.isCompleted && it.deletedAt == null) }.map { it.toLocalTask() }
        var plan = TaskSyncPlanner.plan(local, rows.map { it.task }, settings.taskSyncSeen.first())

        // A list that suddenly looks empty is more likely a sync hiccup there than
        // a decision: do not trash half of the todos because of it.
        val linked = local.count { it.remoteId != null && !it.inTrash }
        val trashing = plan.count { it is SyncAction.TrashLocal }
        var held = false
        if (linked >= 3 && trashing * 2 > linked) {
            plan = plan.filterNot { it is SyncAction.TrashLocal }
            held = true
        }

        val seen = rows.map { it.task.id }.toMutableSet()
        var pushed = 0
        var pulled = 0
        for (action in plan) {
            runCatching {
                when (action) {
                    is SyncAction.PushNew -> store.insert(target.listId, action.fields)?.let { id ->
                        seen += id
                        todos.setTaskSync(action.localId, id, action.fields.fingerprint())
                        pushed++
                    }
                    is SyncAction.PushUpdate -> if (
                        store.update(action.remoteId, action.fields, there[action.remoteId], action.remoteId in allDay)
                    ) {
                        todos.setTaskSync(action.localId, action.remoteId, action.fields.fingerprint())
                        pushed++
                    }
                    is SyncAction.PullNew -> {
                        pullNew(action.remoteId, action.fields, now)
                        pulled++
                    }
                    is SyncAction.PullUpdate -> {
                        pullUpdate(action.localId, action.fields)
                        todos.setTaskSync(action.localId, action.remoteId, action.fields.fingerprint())
                        pulled++
                    }
                    is SyncAction.DeleteRemote -> if (store.delete(action.remoteId)) {
                        todos.setTaskSync(action.localId, null, null)
                        pushed++
                    }
                    is SyncAction.TrashLocal -> {
                        engine.moveToTrash(action.localId)
                        todos.setTaskSync(action.localId, null, null)
                        pulled++
                    }
                    is SyncAction.Unlink -> todos.setTaskSync(action.localId, null, null)
                    is SyncAction.Link -> todos.setTaskSync(action.localId, action.remoteId, action.fingerprint)
                }
            }
        }
        settings.saveTaskSyncSeen(seen)
        val message = when {
            held -> "Many tasks disappeared from the list at once, so nothing was deleted here. " +
                "If that was on purpose, delete those todos here too."
            pushed + pulled == 0 -> "Everything is in step"
            else -> listOfNotNull(
                pushed.takeIf { it > 0 }?.let { "$it sent" },
                pulled.takeIf { it > 0 }?.let { "$it received" },
            ).joinToString(", ")
        }
        return TaskSyncStatus(now, message, ok = !held)
    }

    private suspend fun pullNew(remoteId: Long, fields: SyncFields, now: Long) {
        val due = fields.due
        val id = engine.save(
            TodoEntity(
                title = fields.normalized().title,
                description = fields.note,
                dueDateTime = due,
                priority = fields.priority,
                createdAt = now,
                // Something long overdue from the other app does not ring the moment it arrives.
                nagCount = if (due != null && due < now) TodoEntity.NAG_DISMISSED else 0,
                taskSyncId = remoteId,
                taskSyncHash = fields.fingerprint(),
            )
        )
        if (fields.done) engine.complete(id)
    }

    private suspend fun pullUpdate(localId: Long, fields: SyncFields) {
        val todo = todos.getTodoById(localId) ?: return
        val changed = todo.copy(
            title = fields.normalized().title,
            description = fields.note,
            dueDateTime = fields.due,
            priority = fields.priority,
        )
        if (changed != todo) engine.save(changed)
        when {
            fields.done && !todo.isCompleted -> engine.complete(localId)
            !fields.done && todo.isCompleted -> engine.reopen(localId)
        }
    }

    /** Stops syncing: forgets every link, leaves both sides as they are. */
    suspend fun disconnect() = mutex.withLock {
        todos.clearTaskSync()
        settings.saveTaskSyncSeen(emptySet())
        settings.saveTaskSyncTarget(null)
        _status.value = TaskSyncStatus()
    }

    /** Switches to another list: links to the old one are forgotten first. */
    suspend fun connect(target: TaskSyncTarget) {
        mutex.withLock {
            // Picking the list that is connected already changes nothing; forgetting
            // its links would only make the next pass match everything again by name.
            if (settings.taskSyncTarget.first() != target) {
                todos.clearTaskSync()
                settings.saveTaskSyncSeen(emptySet())
                settings.saveTaskSyncTarget(target)
            }
        }
        syncNow()
    }

    /**
     * Runs [block], which replaces every todo at once (a backup being put back), with
     * no sync pass in between. The todos that come in carry no links, so afterwards
     * the list of tasks seen before is forgotten too. The next pass then finds each
     * open todo's task by title and time and links them again; with the old list
     * kept, those tasks would count as known but unlinked, and every todo would be
     * sent as a second task.
     */
    suspend fun <T> whileTodosAreReplaced(block: suspend () -> T): T = mutex.withLock {
        val result = block()
        settings.saveTaskSyncSeen(emptySet())
        result
    }

    private fun TodoEntity.toLocalTask() = LocalTask(
        id = id,
        remoteId = taskSyncId,
        synced = taskSyncHash,
        fields = SyncFields(title, dueDateTime, isCompleted, priority, description).normalized(),
        inTrash = deletedAt != null,
    )
}
