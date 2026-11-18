package com.focusforceplus.app.data.tasks

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Calendar
import java.util.TimeZone

/**
 * The tasks apps FocusForce+ can sync with. Both speak the OpenTasks contract;
 * Tasks.org ships a copy of the OpenTasks provider under its own name.
 */
enum class TaskApp(
    val label: String,
    val authority: String,
    val readPermission: String,
    val writePermission: String,
    /** Route value of its disclosure screen. */
    val disclosureType: String,
) {
    OPENTASKS("OpenTasks", "org.dmfs.tasks", "org.dmfs.permission.READ_TASKS", "org.dmfs.permission.WRITE_TASKS", "opentasks"),
    TASKS_ORG("Tasks.org", "org.tasks.opentasks", "org.tasks.permission.READ_TASKS", "org.tasks.permission.WRITE_TASKS", "tasksorg"),

    /** Debug builds only (app/src/debug FakeTaskProvider); release builds never find it. */
    TEST("Test tasks", "com.focusforceplus.app.debug.tasks", "", "", "testtasks");

    val permissions: Array<String> get() = arrayOf(readPermission, writePermission).filter { it.isNotEmpty() }.toTypedArray()

    fun isInstalled(context: Context): Boolean =
        runCatching { context.packageManager.resolveContentProvider(authority, 0) != null }.getOrDefault(false)

    fun isGranted(context: Context): Boolean = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        fun of(authority: String?): TaskApp? = entries.firstOrNull { it.authority == authority }
        fun ofDisclosure(type: String?): TaskApp? = entries.firstOrNull { it.disclosureType == type }
    }
}

/** A list in the tasks app. */
data class TaskListInfo(val id: Long, val name: String, val account: String, val color: Int)

/** A task as read from the provider, already in FocusForce+'s terms. */
data class RemoteRow(val task: RemoteTask, val allDay: Boolean)

/**
 * Reads and writes one tasks app's provider. Every call returns null (or false) on
 * any failure, so a caller can tell "nothing there" from "could not look".
 */
class TaskStore(private val context: Context, private val app: TaskApp) {

    private val listsUri: Uri = Uri.parse("content://${app.authority}/tasklists")
    private val tasksUri: Uri = Uri.parse("content://${app.authority}/tasks")

    fun lists(): List<TaskListInfo>? = runCatching {
        context.contentResolver.query(listsUri, arrayOf("_id", "list_name", "account_name", "list_color"), null, null, null)
            ?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(TaskListInfo(c.getLong(0), c.getString(1) ?: "Tasks", c.getString(2).orEmpty(), c.getInt(3)))
                    }
                }
            }
    }.getOrNull()

    fun tasks(listId: Long): List<RemoteRow>? = runCatching {
        val projection = arrayOf("_id", "title", "description", "due", "is_allday", "status", "priority")
        context.contentResolver.query(tasksUri, projection, "list_id = ?", arrayOf(listId.toString()), null)?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val allDay = !c.isNull(4) && c.getInt(4) == 1
                    val due = if (c.isNull(3)) null else c.getLong(3).let { if (allDay) allDayToLocal(it) else it }
                    val status = if (c.isNull(5)) 0 else c.getInt(5)
                    add(
                        RemoteRow(
                            RemoteTask(
                                id = c.getLong(0),
                                fields = SyncFields(
                                    title = c.getString(1).orEmpty(),
                                    due = due,
                                    // Completed or cancelled: either way, nothing left to do.
                                    done = status == STATUS_COMPLETED || status == STATUS_CANCELLED,
                                    priority = TaskSyncPlanner.fromRemotePriority(if (c.isNull(6)) 0 else c.getInt(6)),
                                    note = c.getString(2),
                                ).normalized(),
                            ),
                            allDay = allDay,
                        )
                    )
                }
            }
        }
    }.getOrNull()

    fun insert(listId: Long, fields: SyncFields): Long? = runCatching {
        val values = values(fields).apply { put("list_id", listId) }
        context.contentResolver.insert(tasksUri, values)?.let { ContentUris.parseId(it) }
    }.getOrNull()

    /** [allDay]: the task had a date without a time there; it keeps that while ours is 09:00. */
    fun update(taskId: Long, fields: SyncFields, allDay: Boolean = false): Boolean = runCatching {
        context.contentResolver.update(ContentUris.withAppendedId(tasksUri, taskId), values(fields, allDay), null, null) > 0
    }.getOrDefault(false)

    fun delete(taskId: Long): Boolean = runCatching {
        context.contentResolver.delete(ContentUris.withAppendedId(tasksUri, taskId), null, null) > 0
    }.getOrDefault(false)

    /** Emits whenever the tasks app's data changes, while someone listens. */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        val registered = runCatching {
            context.contentResolver.registerContentObserver(tasksUri, true, observer)
        }.isSuccess
        awaitClose { if (registered) context.contentResolver.unregisterContentObserver(observer) }
    }

    private fun values(fields: SyncFields, allDay: Boolean = false) = ContentValues().apply {
        val n = fields.normalized()
        put("title", n.title)
        if (n.note != null) put("description", n.note) else putNull("description")
        val dateOnly = n.due?.takeIf { allDay }?.let { localNineToUtcMidnight(it) }
        if (dateOnly != null) {
            put("due", dateOnly)
            put("tz", "UTC")
            put("is_allday", 1)
        } else if (n.due != null) {
            put("due", n.due)
            put("tz", TimeZone.getDefault().id)
            put("is_allday", 0)
        } else {
            putNull("due")
        }
        put("priority", TaskSyncPlanner.toRemotePriority(n.priority))
        if (n.done) {
            put("status", STATUS_COMPLETED)
            put("completed", System.currentTimeMillis())
        } else {
            put("status", STATUS_NEEDS_ACTION)
            putNull("completed")
        }
    }

    private companion object {
        const val STATUS_NEEDS_ACTION = 0
        const val STATUS_COMPLETED = 2
        const val STATUS_CANCELLED = 3

        /** The UTC midnight of the date, when [local] is 09:00 that day; else null. */
        fun localNineToUtcMidnight(local: Long): Long? {
            val c = Calendar.getInstance().apply { timeInMillis = local }
            if (c.get(Calendar.HOUR_OF_DAY) != 9 || c.get(Calendar.MINUTE) != 0) return null
            return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
            }.timeInMillis
        }

        /** A date without a time is kept at 09:00 on that local date. */
        fun allDayToLocal(utcMidnight: Long): Long {
            val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMidnight }
            return Calendar.getInstance().apply {
                clear()
                set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 9, 0)
            }.timeInMillis
        }
    }
}
