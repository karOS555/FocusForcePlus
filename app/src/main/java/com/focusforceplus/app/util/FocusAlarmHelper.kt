package com.focusforceplus.app.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.focusforceplus.app.data.db.entity.FocusSessionEntity
import com.focusforceplus.app.service.FocusAlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FocusAlarmHelper @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        /** English day keys as stored on focus sessions → Calendar.DAY_OF_WEEK. */
        val DAY_MAP = mapOf(
            "MO" to Calendar.MONDAY,
            "TU" to Calendar.TUESDAY,
            "WE" to Calendar.WEDNESDAY,
            "TH" to Calendar.THURSDAY,
            "FR" to Calendar.FRIDAY,
            "SA" to Calendar.SATURDAY,
            "SU" to Calendar.SUNDAY,
        )

        private const val RC_BASE = 800_000
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleSessionAlarms(session: FocusSessionEntity) {
        if (!session.isActive) return
        val days = session.scheduledDays?.split(",")?.filter { it.isNotBlank() } ?: return
        val hour = session.scheduledTimeHour ?: return
        val minute = session.scheduledTimeMinute ?: return

        days.forEachIndexed { index, day ->
            val calDay = DAY_MAP[day] ?: return@forEachIndexed
            val triggerAt = nextOccurrence(calDay, hour, minute)
            val op = alarmIntent(session.id, requestCode(session.id, index))
            // A missed focus reminder is not worth an AlarmClock icon in the status
            // bar — exact-while-idle is enough and needs no special treatment.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, op)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, op)
            }
        }
    }

    fun cancelSessionAlarms(sessionId: Long) {
        for (index in 0 until 7) {
            alarmManager.cancel(alarmIntent(sessionId, requestCode(sessionId, index)))
        }
    }

    fun rescheduleAll(sessions: List<FocusSessionEntity>) {
        sessions.forEach { scheduleSessionAlarms(it) }
    }

    private fun requestCode(sessionId: Long, dayIndex: Int) =
        (RC_BASE + sessionId * 10 + dayIndex).toInt()

    private fun alarmIntent(sessionId: Long, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, FocusAlarmReceiver::class.java).apply {
                putExtra("sessionId", sessionId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

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
