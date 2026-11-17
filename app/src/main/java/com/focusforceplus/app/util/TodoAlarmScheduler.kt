package com.focusforceplus.app.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.focusforceplus.app.service.TodoAlarmReceiver
import com.focusforceplus.app.service.TodoDigestReceiver
import com.focusforceplus.app.ui.alarm.TodoAlarmActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The alarm side of todo reminders.
 *
 * A todo has exactly **one** pending wake-up at any time: the next moment it
 * should make itself heard, whether that is its due time, the end of a snooze or
 * the next repeat. Scheduling a new one replaces the old one. Before this there
 * were separate due, snooze and cascade alarms per todo, and two of them firing
 * for the same todo is how one snooze could be spent twice.
 *
 * Every PendingIntent is told apart by its data URI instead of a request code
 * computed from the id, so ids can grow without two todos ever sharing an alarm.
 */
@Singleton
class TodoAlarmScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        const val MAX_DIGEST_SLOTS = 8
        const val EXTRA_TODO_ID = "todoId"
        const val EXTRA_ACTIVATION = "activation"
        const val EXTRA_DIGEST_SLOT = "digestSlot"
        const val EXTRA_DIGEST_HOUR = "digestHour"
        const val EXTRA_DIGEST_MINUTE = "digestMinute"
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // ── Per-todo wake-up ──────────────────────────────────────────────────────

    /** Sets (or moves) the one wake-up of this todo. */
    fun scheduleWake(todoId: Long, atMillis: Long) {
        setAlarmClock(atMillis, wakeIntent(todoId, activation = false), showIntent(todoId))
    }

    fun cancelWake(todoId: Long) {
        alarmManager.cancel(wakeIntent(todoId, activation = false))
    }

    /**
     * A wake-up started by a daily reminder time rather than by the todo's own
     * schedule. It lives next to the regular wake-up so that an edit or a resync in
     * the minutes between the reminder time and this todo's turn cannot cancel it.
     */
    fun scheduleActivation(todoId: Long, atMillis: Long) {
        setAlarmClock(atMillis, wakeIntent(todoId, activation = true), showIntent(todoId))
    }

    fun cancelActivation(todoId: Long) {
        alarmManager.cancel(wakeIntent(todoId, activation = true))
    }

    fun cancelAllFor(todoId: Long) {
        cancelWake(todoId)
        cancelActivation(todoId)
    }

    private fun setAlarmClock(atMillis: Long, operation: PendingIntent, show: PendingIntent) {
        // setAlarmClock is the one alarm type that is exact, wakes the device from
        // Doze, and lets the receiver start the alarm screen from the background.
        runCatching {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, show), operation)
        }.onFailure {
            // Exact alarms refused (Android 12 without the grant): better late than never.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
        }
    }

    private fun wakeIntent(todoId: Long, activation: Boolean): PendingIntent {
        val kind = if (activation) "activate" else "wake"
        val intent = Intent(context, TodoAlarmReceiver::class.java).apply {
            data = Uri.parse("focusforce://todo/$todoId/$kind")
            putExtra(EXTRA_TODO_ID, todoId)
            putExtra(EXTRA_ACTIVATION, activation)
        }
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** What the system opens when the user taps the upcoming alarm in the status area. */
    private fun showIntent(todoId: Long): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            TodoAlarmActivity.intent(context, todoId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    // ── Daily reminder times ──────────────────────────────────────────────────

    fun scheduleDigestAlarms(times: List<Pair<Int, Int>>) {
        cancelAllDigestAlarms()
        times.take(MAX_DIGEST_SLOTS).forEachIndexed { slot, (hour, minute) ->
            scheduleDigestSlot(slot, hour, minute)
        }
    }

    /** Arms one slot for its next occurrence (today if still ahead, else tomorrow). */
    fun scheduleDigestSlot(slot: Int, hour: Int, minute: Int) {
        if (slot !in 0 until MAX_DIGEST_SLOTS) return
        val triggerAt = nextOccurrenceOfTime(hour, minute)
        val operation = digestIntent(slot, hour, minute)
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        runCatching {
            if (exactAllowed) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            }
        }
    }

    fun cancelAllDigestAlarms() {
        for (slot in 0 until MAX_DIGEST_SLOTS) {
            alarmManager.cancel(digestIntent(slot, 0, 0))
        }
    }

    private fun digestIntent(slot: Int, hour: Int, minute: Int): PendingIntent {
        val intent = Intent(context, TodoDigestReceiver::class.java).apply {
            data = Uri.parse("focusforce://todo-digest/$slot")
            putExtra(EXTRA_DIGEST_SLOT, slot)
            putExtra(EXTRA_DIGEST_HOUR, hour)
            putExtra(EXTRA_DIGEST_MINUTE, minute)
        }
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun nextOccurrenceOfTime(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }
}
