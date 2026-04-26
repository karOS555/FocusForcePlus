package com.focusforceplus.app.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.focusforceplus.app.data.db.entity.TodoEntity
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.service.TodoAlarmReceiver
import com.focusforceplus.app.service.TodoDigestReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TodoAlarmHelper @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notifHelper: TodoNotificationHelper,
) {
    companion object {
        private const val RC_ALARM_BASE  = 200_000
        private const val RC_SHOW_BASE   = 201_000
        private const val RC_SNOOZE_BASE = 210_000

        // Three fixed slots — slot index is stable, hour/minute can change via settings
        val DIGEST_SLOT_RCS = listOf(300_001, 300_002, 300_003)
        const val MAX_DIGEST_SLOTS = 3
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // ── Scheduled-todo alarm ──────────────────────────────────────────────────

    fun scheduleTodoAlarm(todo: TodoEntity) {
        val dueAt = todo.dueDateTime ?: return
        if (dueAt <= System.currentTimeMillis()) return

        val alarmIntent = alarmIntent(
            todo.id, todo.title, todo.snoozeCount, todo.maxSnoozeCount,
            todo.priority, todo.rescheduleCount, todo.maxRescheduleCount,
            (RC_ALARM_BASE + todo.id).toInt(),
        )
        val showIntent = notifHelper.alarmActivityIntent(
            todo.id, todo.title, 0, todo.priority,
            (RC_SHOW_BASE + todo.id).toInt(),
        )
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(dueAt, showIntent),
            alarmIntent,
        )
    }

    fun cancelTodoAlarm(todoId: Long) {
        alarmManager.cancel(
            alarmIntent(todoId, "", 0, 2, 1, 0, 1, (RC_ALARM_BASE + todoId).toInt())
        )
    }

    // ── Snooze alarm ──────────────────────────────────────────────────────────

    fun scheduleTodoSnoozeAlarm(
        todoId: Long,
        title: String,
        snoozeMinutes: Int,
        newSnoozeCount: Int,
        maxSnoozeCount: Int,
        priority: Int,
    ) {
        val triggerAt   = System.currentTimeMillis() + snoozeMinutes * 60_000L
        val alarmIntent = alarmIntent(
            todoId, title, newSnoozeCount, maxSnoozeCount,
            priority, 0, 1, (RC_SNOOZE_BASE + todoId).toInt(),
        )
        val showIntent = notifHelper.alarmActivityIntent(
            todoId, title, newSnoozeCount, priority,
            (RC_SHOW_BASE + todoId).toInt(),
        )
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAt, showIntent),
            alarmIntent,
        )
    }

    fun cancelTodoSnoozeAlarm(todoId: Long) {
        alarmManager.cancel(
            alarmIntent(todoId, "", 0, 2, 1, 0, 1, (RC_SNOOZE_BASE + todoId).toInt())
        )
    }

    // ── Digest alarms — slot-based ────────────────────────────────────────────
    //
    // Up to MAX_DIGEST_SLOTS slots, each identified by a fixed RC.
    // Times are user-configurable; this function replaces all scheduled slots.

    fun scheduleDigestAlarms(times: List<Pair<Int, Int>>) {
        cancelAllDigestAlarms()
        times.take(MAX_DIGEST_SLOTS).forEachIndexed { slot, (hour, minute) ->
            scheduleNextDigestSlotAlarm(slot, hour, minute, fromNow = false)
        }
    }

    fun scheduleNextDigestSlotAlarm(slot: Int, hour: Int, minute: Int, fromNow: Boolean = true) {
        if (slot !in DIGEST_SLOT_RCS.indices) return
        val triggerAt   = nextOccurrenceOfTime(hour, minute)
        val requestCode = DIGEST_SLOT_RCS[slot]
        val intent = PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, TodoDigestReceiver::class.java).apply {
                putExtra("digestSlot", slot)
                putExtra("digestHour", hour)
                putExtra("digestMinute", minute)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
        }
    }

    fun cancelAllDigestAlarms() {
        DIGEST_SLOT_RCS.forEach { rc ->
            alarmManager.cancel(
                PendingIntent.getBroadcast(
                    context, rc,
                    Intent(context, TodoDigestReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
        }
    }

    // ── Bulk reschedule (used after reboot) ───────────────────────────────────

    fun rescheduleAll(todos: List<TodoEntity>, digestTimes: List<Pair<Int, Int>>) {
        val now = System.currentTimeMillis()
        todos.filter { !it.isCompleted && it.dueDateTime != null && it.dueDateTime > now }
            .forEach { scheduleTodoAlarm(it) }
        scheduleDigestAlarms(digestTimes)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun alarmIntent(
        todoId: Long,
        title: String,
        snoozeCount: Int,
        maxSnoozeCount: Int,
        priority: Int,
        rescheduleCount: Int,
        maxRescheduleCount: Int,
        requestCode: Int,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, TodoAlarmReceiver::class.java).apply {
                putExtra("todoId", todoId)
                putExtra("todoTitle", title)
                putExtra("snoozeCount", snoozeCount)
                putExtra("maxSnoozeCount", maxSnoozeCount)
                putExtra("priority", priority)
                putExtra("rescheduleCount", rescheduleCount)
                putExtra("maxRescheduleCount", maxRescheduleCount)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun nextOccurrenceOfTime(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }
}
