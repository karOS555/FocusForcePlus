package com.focusforceplus.app.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.focusforceplus.app.MainActivity
import com.focusforceplus.app.R
import com.focusforceplus.app.service.RoutineDismissReceiver
import com.focusforceplus.app.service.RoutineRescheduleReceiver
import com.focusforceplus.app.service.RoutineSessionReceiver
import com.focusforceplus.app.ui.alarm.RoutineAlarmActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_ROUTINE             = "routine_channel"
        const val NOTIFICATION_ID_FOREGROUND  = 1001

        private const val NOTIFICATION_ID_ALARM_BASE     = 2000
        private const val NOTIFICATION_ID_PRE_ALARM_BASE = 3000

        // PendingIntent request-code bases (must all be distinct across all routines)
        private const val RC_ALARM_TAP      = 10_000
        private const val RC_PRE_ALARM_TAP  = 11_000
        private const val RC_SNOOZE_TAP     = 12_000
        private const val RC_START_ALARM    = 20_000
        private const val RC_START_PRE      = 21_000
        private const val RC_START_SNOOZE   = 22_000
        private const val RC_SNOOZE_30_NOTIF = 30_000
        private const val RC_SNOOZE_60_NOTIF = 40_000
        private const val RC_DISMISS_ALARM  = 50_000
        private const val RC_DISMISS_PRE    = 51_000
        private const val RC_DISMISS_SNOOZE = 52_000
        private const val RC_FOREGROUND_TAP  = 60_000
        private const val RC_DONE_TASK        = 70_000

        fun alarmNotificationId(routineId: Long)    = (NOTIFICATION_ID_ALARM_BASE     + routineId).toInt()
        fun preAlarmNotificationId(routineId: Long) = (NOTIFICATION_ID_PRE_ALARM_BASE + routineId).toInt()

        fun formatMillis(millis: Long): String {
            val cal = Calendar.getInstance().apply { timeInMillis = millis }
            return "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        }
    }

    fun createChannels() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ROUTINE,
            "Routine Alarms",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Routine start alarms and task reminders"
            enableVibration(true)
            enableLights(true)
        }
        manager.createNotificationChannel(channel)
    }

    // ── Full-screen alarm notification ────────────────────────────────────────

    fun buildAlarmNotification(
        routineId: Long,
        routineName: String,
        snoozeCount: Int = 0,
        maxSnoozeCount: Int = 2,
        invincibleMode: Boolean = false,
    ): Notification {
        val tapIntent   = alarmScreenIntent(routineId, routineName, snoozeCount, isPreAlarm = false,
                                            (RC_ALARM_TAP + routineId).toInt())
        val startIntent = alarmScreenIntent(routineId, routineName, snoozeCount, isPreAlarm = false,
                                            (RC_START_ALARM + routineId).toInt())

        return NotificationCompat.Builder(context, CHANNEL_ROUTINE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Time to start your routine!")
            .setContentText(routineName)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(tapIntent, true)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(0, "Start routine", startIntent)
            .applyDismissAction(routineId, invincibleMode, (RC_DISMISS_ALARM + routineId).toInt())
            .build()
    }

    // ── Pre-alarm reminder (15 min before) ────────────────────────────────────

    fun buildPreAlarmNotification(
        routineId: Long,
        routineName: String,
        startHour: Int,
        startMinute: Int,
        invincibleMode: Boolean = false,
    ): Notification {
        val timeStr   = "%02d:%02d".format(startHour, startMinute)
        // Tap and "Start now" open the closeable pre-alarm screen.
        val tapIntent = alarmScreenIntent(routineId, routineName, snoozeCount = 0, isPreAlarm = true,
                                          (RC_PRE_ALARM_TAP + routineId).toInt())
        val startNow  = alarmScreenIntent(routineId, routineName, snoozeCount = 0, isPreAlarm = false,
                                          (RC_START_PRE + routineId).toInt())

        return NotificationCompat.Builder(context, CHANNEL_ROUTINE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("\"$routineName\" starts at $timeStr")
            .setContentText("Your routine starts in 15 minutes.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(tapIntent)
            .setAutoCancel(false)   // keep notification after tap so it stays visible
            .addAction(0, "Start now", startNow)
            .addAction(0, "Dismiss",
                dismissBroadcastIntent(routineId, (RC_DISMISS_PRE + routineId).toInt()))
            .build()
    }

    // ── Snooze / reschedule pending notification ──────────────────────────────

    fun buildSnoozedNotification(
        routineId: Long,
        routineName: String,
        triggerMillis: Long,
        snoozeCount: Int,
        maxSnoozeCount: Int,
        invincibleMode: Boolean,
    ): Notification {
        val timeStr   = formatMillis(triggerMillis)
        val remaining = maxSnoozeCount - snoozeCount
        val tapIntent = alarmScreenIntent(routineId, routineName, snoozeCount, isPreAlarm = true,
                                          (RC_SNOOZE_TAP + routineId).toInt())
        val startNow  = alarmScreenIntent(routineId, routineName, snoozeCount, isPreAlarm = false,
                                          (RC_START_SNOOZE + routineId).toInt())
        val subText = if (remaining > 0) "$remaining snooze(s) remaining" else "No snoozes left"

        val builder = NotificationCompat.Builder(context, CHANNEL_ROUTINE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("\"$routineName\" snoozed")
            .setContentText("Starts at $timeStr \u00b7 $subText")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(0, "Start now", startNow)

        // Allow snoozing again from the notification as long as snoozes remain.
        if (remaining > 0) {
            builder.addAction(
                0, "Snooze 30m",
                snoozeIntent(routineId, 30, snoozeCount, (RC_SNOOZE_30_NOTIF + routineId).toInt()),
            )
        }

        return builder
            .applyDismissAction(routineId, invincibleMode, (RC_DISMISS_SNOOZE + routineId).toInt())
            .build()
    }

    // ── Foreground service notification ──────────────────────────────────────

    fun buildOngoingNotification(
        routineId: Long,
        routineName: String,
        taskName: String,
        remainingSeconds: Int,
    ): Notification {
        val tapIntent  = mainActivityIntent(routineId, (RC_FOREGROUND_TAP + routineId).toInt())
        val doneIntent = sessionActionIntent(routineId, RoutineSessionReceiver.ACTION_COMPLETE_TASK,
                                             (RC_DONE_TASK + routineId).toInt())
        val body = if (remainingSeconds >= 0) {
            val m = remainingSeconds / 60
            val s = remainingSeconds % 60
            "$taskName \u2014 %d:%02d remaining".format(m, s)
        } else {
            "$taskName \u2014 Overtime"
        }
        return NotificationCompat.Builder(context, CHANNEL_ROUTINE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(routineName)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setContentIntent(tapIntent)
            .setSilent(true)
            .addAction(0, "Done", doneIntent)
            .build()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    fun showNotification(id: Int, notification: Notification) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(id, notification)
    }

    fun cancelNotification(id: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(id)
    }

    // ── Private intent builders ───────────────────────────────────────────────

    fun alarmScreenIntent(
        routineId: Long,
        routineName: String,
        snoozeCount: Int,
        isPreAlarm: Boolean,
        requestCode: Int,
    ): PendingIntent {
        val intent = Intent(context, RoutineAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("routineId", routineId)
            putExtra("routineName", routineName)
            putExtra("snoozeCount", snoozeCount)
            putExtra("isPreAlarm", isPreAlarm)
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun mainActivityIntent(routineId: Long, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("routineId", routineId)
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun dismissBroadcastIntent(routineId: Long, requestCode: Int): PendingIntent {
        val intent = Intent(context, RoutineDismissReceiver::class.java).apply {
            putExtra("routineId", routineId)
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun snoozeIntent(routineId: Long, snoozeMinutes: Int, snoozeCount: Int, requestCode: Int): PendingIntent {
        val intent = Intent(context, RoutineRescheduleReceiver::class.java).apply {
            putExtra("routineId", routineId)
            putExtra("snoozeMinutes", snoozeMinutes)
            putExtra("snoozeCount", snoozeCount)
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun sessionActionIntent(routineId: Long, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, RoutineSessionReceiver::class.java).apply {
            this.action = action
            putExtra("routineId", routineId)
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun NotificationCompat.Builder.applyDismissAction(
        routineId: Long,
        invincibleMode: Boolean,
        requestCode: Int,
        label: String = "Cancel Routine",
    ): NotificationCompat.Builder {
        if (!invincibleMode) {
            addAction(0, label, dismissBroadcastIntent(routineId, requestCode))
        }
        return this
    }
}
