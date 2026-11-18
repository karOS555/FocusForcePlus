package com.focusforceplus.app.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/** One calendar on the device, as the user's calendar app shows it. */
data class CalendarInfo(
    val id: Long,
    val name: String,
    val account: String,
    val color: Int,
    /** Events can be added to it. */
    val writable: Boolean,
)

/** One occurrence of an event (a repeating event has one per day it happens). */
data class CalendarEvent(
    val eventId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val calendarId: Long,
    val color: Int,
    val location: String?,
    /** Shows the user as busy: not marked free, not declined. */
    val busy: Boolean,
    /** One of the events FocusForce+ makes for todos (see [CalendarRepository.TODO_MARKER]). */
    val fromTodo: Boolean = false,
)

/**
 * The device calendar, through Android's calendar provider: nothing of it leaves
 * the device through FocusForce+. Every call degrades to "nothing" without the
 * calendar permission, so callers need no checks of their own.
 */
@Singleton
class CalendarRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun canRead(): Boolean = granted(Manifest.permission.READ_CALENDAR)

    fun canWrite(): Boolean = canRead() && granted(Manifest.permission.WRITE_CALENDAR)

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Calendars the user has switched on in their calendar app. */
    suspend fun calendars(): List<CalendarInfo> = withContext(Dispatchers.IO) {
        if (!canRead()) return@withContext emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.VISIBLE,
        )
        runCatching {
            context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        if (c.getInt(5) == 0) continue
                        add(
                            CalendarInfo(
                                id = c.getLong(0),
                                name = c.getString(1) ?: "Calendar",
                                account = c.getString(2).orEmpty(),
                                color = c.getInt(3),
                                writable = c.getInt(4) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                            )
                        )
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList()).sortedWith(compareBy({ it.account }, { it.name }))
    }

    /**
     * Occurrences that overlap [from]..[to], from [calendarIds] (null = every
     * visible calendar), earliest first. All-day events count for the local days
     * they cover, not the UTC days the provider stores them on.
     */
    suspend fun events(from: Long, to: Long, calendarIds: Set<Long>?): List<CalendarEvent> =
        withContext(Dispatchers.IO) { queryEvents(from, to, calendarIds) }

    /** The busy event going on right now, if any (all-day events do not count). */
    suspend fun busyNow(calendarIds: Set<Long>?): CalendarEvent? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        queryEvents(now, now + 1, calendarIds)
            .filter { !it.allDay && it.busy && it.begin <= now && it.end > now }
            .maxByOrNull { it.end }
    }

    private fun queryEvents(from: Long, to: Long, calendarIds: Set<Long>?): List<CalendarEvent> {
        if (!canRead() || calendarIds?.isEmpty() == true) return emptyList()
        // All-day events are stored in UTC; widen the window by a day on both sides
        // and sort them out by their local dates below.
        val day = 24 * 60 * 60_000L
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from - day)
            ContentUris.appendId(it, to + day)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.AVAILABILITY,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
            CalendarContract.Instances.VISIBLE,
            CalendarContract.Instances.DESCRIPTION,
        )
        val selection = buildString {
            append("${CalendarContract.Instances.VISIBLE} = 1")
            if (calendarIds != null) {
                append(" AND ${CalendarContract.Instances.CALENDAR_ID} IN (")
                append(calendarIds.joinToString(","))
                append(")")
            }
        }
        val events = runCatching {
            context.contentResolver.query(uri, projection, selection, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val allDay = c.getInt(4) == 1
                        var begin = c.getLong(2)
                        var end = c.getLong(3)
                        if (allDay) {
                            begin = utcDayToLocal(begin)
                            end = utcDayToLocal(end)
                        }
                        if (end <= from || begin >= to) continue
                        add(
                            CalendarEvent(
                                eventId = c.getLong(0),
                                title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(No title)",
                                begin = begin,
                                end = end,
                                allDay = allDay,
                                calendarId = c.getLong(5),
                                color = c.getInt(6),
                                location = c.getString(7)?.takeIf { it.isNotBlank() },
                                busy = c.getInt(8) != CalendarContract.Events.AVAILABILITY_FREE &&
                                    c.getInt(9) != CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED,
                                fromTodo = c.getString(11) == TODO_MARKER,
                            )
                        )
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
        return events.sortedWith(compareBy({ !it.allDay }, { it.begin }))
    }

    /** Midnight UTC of a day -> local midnight of the same calendar date. */
    private fun utcDayToLocal(utcMillis: Long): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
        return Calendar.getInstance().apply {
            clear()
            set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }

    /** Emits once at once and again whenever anything in the calendar changes. */
    fun changes(): Flow<Unit> = callbackFlow {
        trySend(Unit)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        val registered = runCatching {
            context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        }.isSuccess
        awaitClose { if (registered) context.contentResolver.unregisterContentObserver(observer) }
    }

    // ── Todos in a calendar ──────────────────────────────────────────────────

    /** What a todo's event in the calendar says now; null when it is gone. */
    data class StoredEvent(val calendarId: Long, val title: String, val start: Long, val deleted: Boolean)

    suspend fun storedEvents(ids: Collection<Long>): Map<Long, StoredEvent> = withContext(Dispatchers.IO) {
        if (!canRead() || ids.isEmpty()) return@withContext emptyMap()
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DELETED,
        )
        runCatching {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                "${CalendarContract.Events._ID} IN (${ids.joinToString(",")})",
                null,
                null,
            )?.use { c ->
                buildMap {
                    while (c.moveToNext()) {
                        put(c.getLong(0), StoredEvent(c.getLong(1), c.getString(2).orEmpty(), c.getLong(3), c.getInt(4) == 1))
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyMap())
    }

    /** Adds a half-hour event for a todo; it shows the user as free. */
    suspend fun insertTodoEvent(calendarId: Long, title: String, start: Long): Long? = withContext(Dispatchers.IO) {
        if (!canWrite()) return@withContext null
        runCatching {
            context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, todoValues(calendarId, title, start))
                ?.let { ContentUris.parseId(it) }
        }.getOrNull()
    }

    suspend fun updateTodoEvent(eventId: Long, calendarId: Long, title: String, start: Long): Boolean =
        withContext(Dispatchers.IO) {
            if (!canWrite()) return@withContext false
            runCatching {
                context.contentResolver.update(
                    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                    todoValues(calendarId, title, start),
                    null,
                    null,
                ) > 0
            }.getOrDefault(false)
        }

    suspend fun deleteEvent(eventId: Long): Boolean = withContext(Dispatchers.IO) {
        if (!canWrite()) return@withContext false
        runCatching {
            context.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), null, null) > 0
        }.getOrDefault(false)
    }

    private fun todoValues(calendarId: Long, title: String, start: Long) = ContentValues().apply {
        put(CalendarContract.Events.CALENDAR_ID, calendarId)
        put(CalendarContract.Events.TITLE, title)
        put(CalendarContract.Events.DESCRIPTION, TODO_MARKER)
        put(CalendarContract.Events.DTSTART, start)
        put(CalendarContract.Events.DTEND, start + TODO_EVENT_MINUTES * 60_000L)
        put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_FREE)
        put(CalendarContract.Events.HAS_ALARM, 0)
    }

    /** Events in [calendarId] that FocusForce+ made for todos, by their marker. */
    suspend fun todoEventIds(calendarId: Long): Set<Long> = withContext(Dispatchers.IO) {
        if (!canRead()) return@withContext emptySet()
        runCatching {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(CalendarContract.Events._ID),
                "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DESCRIPTION} = ? AND " +
                    "${CalendarContract.Events.DELETED} = 0",
                arrayOf(calendarId.toString(), TODO_MARKER),
                null,
            )?.use { c -> buildSet { while (c.moveToNext()) add(c.getLong(0)) } }.orEmpty()
        }.getOrDefault(emptySet())
    }

    companion object {
        const val TODO_EVENT_MINUTES = 30

        /** The description of every todo event; how FocusForce+ knows its own. */
        const val TODO_MARKER = "A todo in FocusForce+."
    }
}
