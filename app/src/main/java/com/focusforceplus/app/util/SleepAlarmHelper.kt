package com.focusforceplus.app.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.focusforceplus.app.MainActivity
import com.focusforceplus.app.data.repository.SleepSchedule
import com.focusforceplus.app.service.SleepReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Arms the three sleep-mode alarms: wind-down reminder, bedtime, and wake-up.
 * There is one schedule, so the request codes are fixed. Day filtering happens
 * in [SleepReceiver] (it re-arms silently on inactive days), which keeps the
 * scheduling here to a simple "next occurrence of this time of day".
 */
@Singleton
class SleepAlarmHelper @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        private const val RC_WIND_DOWN = 700_001
        private const val RC_BEDTIME   = 700_002
        private const val RC_WAKE      = 700_003
        private const val RC_SHOW      = 700_004
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleAll(schedule: SleepSchedule) {
        cancelAll()
        if (!schedule.enabled) return

        if (schedule.windDownMinutes > 0) {
            val windDownAt = ((schedule.bedtimeMinutes - schedule.windDownMinutes) + 24 * 60) % (24 * 60)
            scheduleExact(nextOccurrence(windDownAt), actionIntent(SleepReceiver.ACTION_WIND_DOWN, RC_WIND_DOWN))
        }
        // Bedtime and wake-up use setAlarmClock: maximum delivery reliability and
        // the BAL exemption the linked routine alarms need.
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(nextOccurrence(schedule.bedtimeMinutes), showIntent()),
            actionIntent(SleepReceiver.ACTION_BEDTIME, RC_BEDTIME),
        )
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(nextOccurrence(schedule.wakeMinutes), showIntent()),
            actionIntent(SleepReceiver.ACTION_WAKE, RC_WAKE),
        )
    }

    fun cancelAll() {
        alarmManager.cancel(actionIntent(SleepReceiver.ACTION_WIND_DOWN, RC_WIND_DOWN))
        alarmManager.cancel(actionIntent(SleepReceiver.ACTION_BEDTIME, RC_BEDTIME))
        alarmManager.cancel(actionIntent(SleepReceiver.ACTION_WAKE, RC_WAKE))
    }

    private fun scheduleExact(triggerAt: Long, operation: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        }
    }

    private fun actionIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, SleepReceiver::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun showIntent(): PendingIntent =
        PendingIntent.getActivity(
            context, RC_SHOW,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun nextOccurrence(minutesOfDay: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minutesOfDay / 60)
            set(Calendar.MINUTE, minutesOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }
}
