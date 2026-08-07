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
import com.focusforceplus.app.util.NotificationStyle.applyBrand
import com.focusforceplus.app.util.NotificationStyle.asLiveUpdate
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
        const val CHANNEL_ROUTINE_SESSION     = "routine_live"
        private const val RETIRED_CHANNEL_ROUTINE_SESSION = "routine_session"
        const val NOTIFICATION_ID_FOREGROUND  = 1001

        /** Sub-text on every routine notification, so a stack stays readable. */
        private const val MODULE = "Routine"

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
        private const val RC_SWIPE_ALARM    = 53_000
        private const val RC_FOREGROUND_TAP  = 60_000
        private const val RC_DONE_TASK        = 70_000
        private const val RC_TASK_END_TAP     = 90_000
        private const val RC_TASK_END_DONE    = 91_000
        private const val RC_INTERRUPTED_TAP  = 92_000
        private const val RC_ADD_TIME         = 93_000
        private const val RC_CANCEL_SESSION   = 94_000
        private const val RC_TASK_END_ADD     = 95_000

        private const val NOTIFICATION_ID_TASK_END_BASE = 950_000
        const val NOTIFICATION_ID_INTERRUPTED = 949_999

        fun alarmNotificationId(routineId: Long)    = (NOTIFICATION_ID_ALARM_BASE     + routineId).toInt()
        fun preAlarmNotificationId(routineId: Long) = (NOTIFICATION_ID_PRE_ALARM_BASE + routineId).toInt()
        fun taskEndNotificationId(routineId: Long)  = (NOTIFICATION_ID_TASK_END_BASE  + routineId).toInt()

        fun formatMillis(millis: Long): String {
            val cal = Calendar.getInstance().apply { timeInMillis = millis }
            return "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        }
    }

    fun createChannels() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ROUTINE,
                "Routine Alarms",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Routine start alarms and task time-ups"
                group = NotificationStyle.GROUP_ALARMS
                enableVibration(true)
                enableLights(true)
            }
        )
        // The live session sits in its own quiet channel: it updates constantly and
        // must never buzz, while the alarm channel above must always be loud.
        manager.createNotificationChannel(
            NotificationStyle.liveSessionChannel(
                CHANNEL_ROUTINE_SESSION,
                "Running routine",
                "Live progress of the routine you are currently doing",
            )
        )
        // An earlier build shipped this channel at IMPORTANCE_LOW, which made the
        // phone treat it as silent and drop it from the lock screen. A channel's
        // importance cannot be raised once it exists, so the old one is retired.
        runCatching { manager.deleteNotificationChannel(RETIRED_CHANNEL_ROUTINE_SESSION) }
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
            .applyBrand(MODULE)
            .setContentTitle("Time to start your routine!")
            .setContentText(routineName)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(tapIntent, true)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            // Android 14+ lets users swipe FGS notifications — bring the alarm back.
            .setDeleteIntent(alarmSwipedIntent(routineId, snoozeCount, (RC_SWIPE_ALARM + routineId).toInt()))
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
            .applyBrand(MODULE)
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
            .applyBrand(MODULE)
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

    /**
     * Live session notification: the routine drawn as a timeline, one segment per
     * task, so a glance shows where the user is and what is still ahead — the same
     * shape the active-routine screen has, but in the shade, on the lock screen
     * and (Android 16 / One UI) in the status-bar chip and the Now Bar.
     *
     * The system chronometer anchored at [taskEndWallMillis] renders the live
     * countdown (or the overtime count-up) without a notify() per second; the
     * service re-posts on a slow tick only to advance the timeline tracker.
     */
    fun buildOngoingNotification(
        routineId: Long,
        routineName: String,
        taskName: String,
        taskEndWallMillis: Long,
        overtime: Boolean,
        taskIndex: Int = 0,
        taskDurationsMinutes: List<Int> = emptyList(),
        elapsedSeconds: Int = 0,
        /** What the status-bar chip counts: time left in the step, or time over it. */
        chipSeconds: Int = 0,
        invincible: Boolean = false,
    ): Notification {
        val tapIntent  = mainActivityIntent(routineId, (RC_FOREGROUND_TAP + routineId).toInt())
        val doneIntent = sessionActionIntent(routineId, RoutineSessionReceiver.ACTION_COMPLETE_TASK,
                                             (RC_DONE_TASK + routineId).toInt())
        val addTimeIntent = sessionActionIntent(routineId, RoutineSessionReceiver.ACTION_ADD_TIME,
                                                (RC_ADD_TIME + routineId).toInt())

        val stepLabel = if (taskDurationsMinutes.isNotEmpty()) {
            "Step ${taskIndex + 1}/${taskDurationsMinutes.size} \u00b7 $taskName"
        } else {
            taskName
        }
        val body = if (overtime) "$stepLabel \u2014 overtime" else stepLabel

        val builder = NotificationCompat.Builder(context, CHANNEL_ROUTINE_SESSION)
            .applyBrand(MODULE)
            .setContentTitle(routineName)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(tapIntent)
            // Deliberately NOT setSilent(true): the channel is already soundless,
            // and a "silent" notification is hidden from the lock screen on phones
            // where "hide silent notifications" is on — which is where a running
            // routine is most useful.
            .setWhen(taskEndWallMillis)
            .setUsesChronometer(true)
            .setChronometerCountDown(!overtime)
            .addAction(0, "Done", doneIntent)
            .addAction(0, "+5 min", addTimeIntent)
            .asLiveUpdate(
                if (overtime) "+" + NotificationStyle.formatChip(chipSeconds)
                else NotificationStyle.formatChip(chipSeconds),
            )

        routineTimeline(taskDurationsMinutes, taskIndex, elapsedSeconds, overtime)
            ?.let { builder.setStyle(it) }

        // Cancelling an invincible routine is refused by the service anyway; not
        // offering the button keeps the notification honest about what it can do.
        if (!invincible) {
            builder.addAction(
                0, "Cancel",
                sessionActionIntent(routineId, RoutineSessionReceiver.ACTION_CANCEL,
                                    (RC_CANCEL_SESSION + routineId).toInt()),
            )
        }
        return builder.build()
    }

    /**
     * One segment per task, coloured done / running / still ahead, with a marker at
     * every task boundary. Null below Android 16, where the notification simply
     * stays a plain one.
     */
    private fun routineTimeline(
        taskDurationsMinutes: List<Int>,
        taskIndex: Int,
        elapsedSeconds: Int,
        overtime: Boolean,
    ): NotificationCompat.Style? {
        if (taskDurationsMinutes.isEmpty()) return null
        val segments = taskDurationsMinutes.mapIndexed { index, minutes ->
            NotificationStyle.TimelineSegment(
                length = (minutes * 60).coerceAtLeast(60),
                color = when {
                    index < taskIndex -> NotificationStyle.SEGMENT_DONE
                    index > taskIndex -> NotificationStyle.SEGMENT_AHEAD
                    overtime -> NotificationStyle.SEGMENT_OVERTIME
                    else -> NotificationStyle.SEGMENT_NOW
                },
            )
        }
        val total = segments.sumOf { it.length }
        // Markers at the task boundaries; the two outer ends need none.
        val points = segments
            .runningFold(0) { acc, seg -> acc + seg.length }
            .drop(1)
            .dropLast(1)
        return NotificationStyle.timeline(
            segments = segments,
            progress = elapsedSeconds.coerceIn(0, total),
            points = points,
        )
    }

    // ── Task-end wake-up ──────────────────────────────────────────────────────

    /**
     * Full-screen alert fired when a task's time runs out (and re-posted on each
     * overtime nag). Silent by itself — the routine service plays the alarm sound
     * and vibration, so re-posts don't stack system sounds on top.
     */
    fun buildTaskEndAlarmNotification(
        routineId: Long,
        routineName: String,
        taskName: String,
        overtimeMinutes: Int,
    ): Notification {
        val tapIntent  = mainActivityIntent(routineId, (RC_TASK_END_TAP + routineId).toInt())
        val doneIntent = sessionActionIntent(routineId, RoutineSessionReceiver.ACTION_COMPLETE_TASK,
                                             (RC_TASK_END_DONE + routineId).toInt())
        val addTimeIntent = sessionActionIntent(routineId, RoutineSessionReceiver.ACTION_ADD_TIME,
                                                (RC_TASK_END_ADD + routineId).toInt())
        val body = if (overtimeMinutes > 0) {
            "\"$taskName\" is $overtimeMinutes min over time — finish it or add time."
        } else {
            "Time is up for \"$taskName\"."
        }
        return NotificationCompat.Builder(context, CHANNEL_ROUTINE)
            .applyBrand(MODULE)
            .setContentTitle("Time is up — $routineName")
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(tapIntent, true)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Done", doneIntent)
            .addAction(0, "+5 min", addTimeIntent)
            .build()
    }

    /** Posted when a reboot (or forced stop) killed a running routine session. */
    fun buildSessionInterruptedNotification(routineName: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_ROUTINE)
            .applyBrand(MODULE)
            .setContentTitle("Routine interrupted")
            .setContentText("\"$routineName\" was interrupted by a device restart.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(mainActivityIntent(0L, RC_INTERRUPTED_TAP))
            .setAutoCancel(true)
            .build()

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

    private fun alarmSwipedIntent(routineId: Long, snoozeCount: Int, requestCode: Int): PendingIntent {
        val intent = Intent(context, RoutineDismissReceiver::class.java).apply {
            action = RoutineDismissReceiver.ACTION_ALARM_SWIPED
            putExtra("routineId", routineId)
            putExtra("snoozeCount", snoozeCount)
        }
        return PendingIntent.getBroadcast(
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
