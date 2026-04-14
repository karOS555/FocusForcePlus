package com.focusforceplus.app.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.focusforceplus.app.data.db.entity.RoutineEntity
import com.focusforceplus.app.service.RoutineAlarmReceiver
import com.focusforceplus.app.service.RoutinePreAlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmHelper @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        /** German day abbreviations as stored in the database → Calendar.DAY_OF_WEEK. */
        val DAY_MAP = mapOf(
            "MO" to Calendar.MONDAY,
            "DI" to Calendar.TUESDAY,
            "MI" to Calendar.WEDNESDAY,
            "DO" to Calendar.THURSDAY,
            "FR" to Calendar.FRIDAY,
            "SA" to Calendar.SATURDAY,
            "SO" to Calendar.SUNDAY,
        )

        private const val PRE_ALARM_MINUTES  = 15L
        private const val PRE_ALARM_RC_OFFSET = 100_000
        private const val SNOOZE_RC_OFFSET    = 500_000
        private const val SHOW_RC_OFFSET      =  70_000
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleRoutineAlarms(routine: RoutineEntity) {
        if (!routine.isActive) return
        val days = routine.activeDays.split(",").filter(String::isNotBlank)
        days.forEachIndexed { index, day ->
            val calDay    = DAY_MAP[day] ?: return@forEachIndexed
            val triggerAt = nextOccurrence(calDay, routine.startTimeHour, routine.startTimeMinute)

            // ── Main alarm ────────────────────────────────────────────────────
            val mainOp = mainAlarmIntent(routine.id, routine.name, requestCode(routine.id, index))
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAt, showIntent(routine.id)),
                mainOp,
            )

            // ── 15-minute pre-alarm ───────────────────────────────────────────
            val preAlarmAt = triggerAt - PRE_ALARM_MINUTES * 60 * 1000
            if (preAlarmAt > System.currentTimeMillis() + 30_000) {
                val preOp = preAlarmIntent(
                    routine.id, routine.name,
                    routine.startTimeHour, routine.startTimeMinute,
                    preAlarmRequestCode(routine.id, index),
                )
                // Use exact Doze-safe alarm so the pre-alarm fires on time.
                // Android 13+: USE_EXACT_ALARM (auto-granted) satisfies canScheduleExactAlarms().
                // Android 12: fall back to inexact if SCHEDULE_EXACT_ALARM not yet granted.
                // Android <12: no permission needed at all.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && !alarmManager.canScheduleExactAlarms()
                ) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, preAlarmAt, preOp)
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, preAlarmAt, preOp)
                }
            }
        }
    }

    fun cancelRoutineAlarms(routineId: Long) {
        for (index in 0 until 7) {
            alarmManager.cancel(mainAlarmIntent(routineId, "", requestCode(routineId, index)))
            alarmManager.cancel(preAlarmIntent(routineId, "", 0, 0, preAlarmRequestCode(routineId, index)))
        }
        // Also cancel any pending snooze or reschedule alarm.
        alarmManager.cancel(snoozeAlarmIntent(routineId, "", (routineId + SNOOZE_RC_OFFSET).toInt()))
        alarmManager.cancel(snoozeAlarmIntent(routineId, "", (routineId + SNOOZE_RC_OFFSET + 1).toInt()))
    }

    fun rescheduleAll(routines: List<RoutineEntity>) {
        routines.filter { it.isActive }.forEach { scheduleRoutineAlarms(it) }
    }

    /** Sets a one-time alarm in [snoozeMinutes] minutes — used by [RoutineRescheduleReceiver]. */
    fun scheduleSnoozeAlarm(routineId: Long, routineName: String, snoozeMinutes: Int) {
        val triggerAt   = System.currentTimeMillis() + snoozeMinutes * 60_000L
        val requestCode = (routineId + SNOOZE_RC_OFFSET).toInt()
        val op          = snoozeAlarmIntent(routineId, routineName, requestCode)
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAt, showIntent(routineId)),
            op,
        )
    }

    /** Sets a one-time alarm at an absolute [triggerMillis] timestamp for reschedule. */
    fun scheduleRescheduleAlarm(routineId: Long, routineName: String, triggerMillis: Long) {
        val requestCode = (routineId + SNOOZE_RC_OFFSET + 1).toInt()
        val op = snoozeAlarmIntent(routineId, routineName, requestCode)
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerMillis, showIntent(routineId)),
            op,
        )
    }

    /** Cancels any pending one-time reschedule alarm set by [scheduleRescheduleAlarm]. */
    fun cancelRescheduleAlarm(routineId: Long) {
        val requestCode = (routineId + SNOOZE_RC_OFFSET + 1).toInt()
        alarmManager.cancel(snoozeAlarmIntent(routineId, "", requestCode))
    }

    // ── Request-code helpers ──────────────────────────────────────────────────

    private fun requestCode(routineId: Long, dayIndex: Int) =
        (routineId * 10 + dayIndex).toInt()

    private fun preAlarmRequestCode(routineId: Long, dayIndex: Int) =
        (routineId * 10 + dayIndex + PRE_ALARM_RC_OFFSET).toInt()

    // ── PendingIntent builders ────────────────────────────────────────────────

    private fun mainAlarmIntent(routineId: Long, routineName: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, RoutineAlarmReceiver::class.java).apply {
            putExtra("routineId", routineId)
            putExtra("routineName", routineName)
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun preAlarmIntent(
        routineId: Long, routineName: String,
        startHour: Int, startMinute: Int,
        requestCode: Int,
    ): PendingIntent {
        val intent = Intent(context, RoutinePreAlarmReceiver::class.java).apply {
            putExtra("routineId", routineId)
            putExtra("routineName", routineName)
            putExtra("startHour", startHour)
            putExtra("startMinute", startMinute)
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun snoozeAlarmIntent(routineId: Long, routineName: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, RoutineAlarmReceiver::class.java).apply {
            putExtra("routineId", routineId)
            putExtra("routineName", routineName)
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun showIntent(routineId: Long): PendingIntent {
        val intent = Intent(context, RoutineAlarmReceiver::class.java).apply {
            putExtra("routineId", routineId)
        }
        return PendingIntent.getBroadcast(
            context, (routineId + SHOW_RC_OFFSET).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // ── Time calculation ──────────────────────────────────────────────────────

    private fun nextOccurrence(dayOfWeek: Int, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        var diff = dayOfWeek - cal.get(Calendar.DAY_OF_WEEK)
        if (diff < 0 || (diff == 0 && System.currentTimeMillis() >= cal.timeInMillis)) {
            diff += 7
        }
        cal.add(Calendar.DAY_OF_YEAR, diff)
        return cal.timeInMillis
    }
}
