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
import com.focusforceplus.app.service.TodoDismissReceiver
import com.focusforceplus.app.service.TodoSnoozeReceiver
import com.focusforceplus.app.ui.alarm.TodoAlarmActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TodoNotificationHelper @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_TODO_URGENT  = "todo_urgent"
        const val CHANNEL_TODO_DIGEST  = "todo_digest"
        const val CHANNEL_TODO_OVERDUE = "todo_overdue"
        const val CHANNEL_TODO_MEDIUM  = "todo_medium"

        const val NOTIFICATION_ID_DIGEST  = 6001
        const val NOTIFICATION_ID_OVERDUE = 6002

        private const val NOTIF_TODO_ALARM_BASE  = 4_000
        private const val NOTIF_TODO_DIGEST_BASE = 8_000

        // PendingIntent request code bases — all globally unique, per-todo via + todoId
        private const val RC_ALARM_TAP              = 400_000  // tap on active alarm notification
        private const val RC_DONE                   = 410_000  // Done from active alarm notif (Medium)
        private const val RC_SNOOZE_5               = 420_000  // Snooze 5m from active alarm (High)
        private const val RC_SNOOZE_10              = 421_000  // Snooze 10m from active alarm (Medium)
        private const val RC_DISMISS                = 430_000  // Dismiss from active alarm notif (Medium)
        private const val RC_SNOOZE_TAP             = 440_000  // tap on snoozed notification
        private const val RC_SNOOZE_AGAIN           = 450_000  // Snooze again from snoozed notif
        private const val RC_DISMISS_SNOOZE         = 460_000  // Dismiss from snoozed notif (Medium)
        private const val RC_MEDIUM_TAP             = 470_000  // tap on Medium dismissed notification
        private const val RC_MEDIUM_DONE            = 480_000  // Done from Medium dismissed notification
        private const val RC_MEDIUM_SNOOZE_DONE     = 490_000  // Done from Medium snoozed notification
        private const val RC_DIGEST_TAP             = 500_001
        private const val RC_OVERDUE_TAP            = 500_002
        private const val RC_DIGEST_TODO_TAP        = 510_000  // tap on per-todo overdue notification
        private const val RC_DIGEST_TODO_DONE       = 520_000  // Done from per-todo overdue notification
        private const val RC_RESCHEDULE_ALARM_NOTIF = 530_000  // Reschedule from active High alarm notif
        private const val RC_DIGEST_TODO_SNOOZE     = 540_000  // Snooze from per-todo digest notification
        private const val RC_DIGEST_TODO_DISMISS    = 550_000  // Dismiss from per-todo digest notification

        private const val OVERDUE_GROUP = "com.focusforceplus.app.OVERDUE_GROUP"

        fun todoAlarmNotificationId(todoId: Long)  = (NOTIF_TODO_ALARM_BASE  + todoId).toInt()
        fun todoDigestNotificationId(todoId: Long) = (NOTIF_TODO_DIGEST_BASE + todoId).toInt()
    }

    fun createChannels() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_TODO_URGENT, "Todo Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Full-screen alarm for high-priority scheduled todos"
                enableVibration(true)
                enableLights(true)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_TODO_MEDIUM, "Todo Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Silent alarm screen for medium-priority todos"
                enableVibration(false)
                setSound(null, null)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_TODO_DIGEST, "Todo Digest", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Daily digest of open and overdue todos"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_TODO_OVERDUE, "Overdue Todos", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Persistent notification for overdue todos"
                enableVibration(false)
            }
        )
    }

    // ── Full-screen alarm notification (High + Medium) ────────────────────────
    //
    // HIGH:  no Done action (requires 2nd confirmation on alarm screen).
    //        Shows Snooze if snoozes remain, Reschedule always (opens alarm screen).
    //        Ongoing + non-dismissable.
    //
    // MEDIUM: Done (direct), Snooze if snoozes remain, Dismiss.
    //         Ongoing + non-dismissable.
    //
    // LOW:   Done (direct), auto-cancel (dismissable).

    fun buildTodoAlarmNotification(
        todoId: Long,
        title: String,
        snoozeCount: Int,
        maxSnoozeCount: Int,
        priority: Int = 1,
    ): Notification {
        val channel     = if (priority == 2) CHANNEL_TODO_URGENT else CHANNEL_TODO_MEDIUM
        val alarmIntent = alarmActivityIntent(todoId, title, snoozeCount, priority, (RC_ALARM_TAP + todoId).toInt())
        val remaining   = maxSnoozeCount - snoozeCount

        return when (priority) {
            // ── High ─────────────────────────────────────────────────────────
            2 -> {
                val builder = NotificationCompat.Builder(context, channel)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("\u26a0\ufe0f Todo alarm")
                    .setContentText(title)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setFullScreenIntent(alarmIntent, true)
                    .setContentIntent(alarmIntent)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    // No Done — must confirm on alarm screen
                    .addAction(0, "Open alarm", alarmIntent)

                if (remaining > 0) {
                    builder.addAction(
                        0, "Snooze 5m ($remaining left)",
                        snoozeBroadcastIntent(todoId, 5, snoozeCount, priority, (RC_SNOOZE_5 + todoId).toInt()),
                    )
                }
                // Reschedule always available — opens alarm screen where limit is enforced
                builder.addAction(
                    0, "Reschedule",
                    alarmActivityIntent(todoId, title, snoozeCount, priority, (RC_RESCHEDULE_ALARM_NOTIF + todoId).toInt()),
                )
                builder.build()
            }

            // ── Medium ───────────────────────────────────────────────────────
            1 -> {
                val doneIntent    = doneBroadcastIntent(todoId, (RC_DONE + todoId).toInt())
                val dismissIntent = dismissBroadcastIntent(todoId, (RC_DISMISS + todoId).toInt())

                val builder = NotificationCompat.Builder(context, channel)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("Todo reminder")
                    .setContentText(title)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setFullScreenIntent(alarmIntent, true)
                    .setContentIntent(alarmIntent)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .addAction(0, "Done \u2714", doneIntent)

                if (remaining > 0) {
                    builder.addAction(
                        0, "Snooze 10m",
                        snoozeBroadcastIntent(todoId, 10, snoozeCount, priority, (RC_SNOOZE_10 + todoId).toInt()),
                    )
                }
                builder.addAction(0, "Dismiss", dismissIntent)
                builder.build()
            }

            // ── Low ──────────────────────────────────────────────────────────
            else -> {
                val doneIntent = doneBroadcastIntent(todoId, (RC_DONE + todoId).toInt())
                NotificationCompat.Builder(context, CHANNEL_TODO_DIGEST)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("Reminder: $title")
                    .setContentText("Tap to open your todos")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setContentIntent(mainActivityIntent(RC_OVERDUE_TAP))
                    .setAutoCancel(true)
                    .addAction(0, "Done \u2714", doneIntent)
                    .build()
            }
        }
    }

    // ── Low priority notification (no alarm screen) ──────────────────────────

    fun buildLowPriorityNotification(todoId: Long, title: String): Notification {
        val tapIntent  = mainActivityIntent(RC_OVERDUE_TAP)
        val doneIntent = doneBroadcastIntent(todoId, (RC_DONE + todoId).toInt())

        return NotificationCompat.Builder(context, CHANNEL_TODO_DIGEST)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Reminder: $title")
            .setContentText("Tap to open your todos")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .addAction(0, "Done \u2714", doneIntent)
            .build()
    }

    // ── Snoozed notification ──────────────────────────────────────────────────
    //
    // HIGH:  Snooze again (if snoozes remain). Content tap re-opens alarm screen.
    //        Ongoing. No Done (must confirm on alarm screen when it re-fires).
    //
    // MEDIUM: Done (direct), Snooze again (if remain), Dismiss.
    //         Ongoing.

    fun buildTodoSnoozedNotification(
        todoId: Long,
        title: String,
        triggerMillis: Long,
        snoozeCount: Int,
        maxSnoozeCount: Int,
        priority: Int = 1,
    ): Notification {
        val remaining = maxSnoozeCount - snoozeCount
        val timeStr   = formatTime(triggerMillis)
        val tapIntent = alarmActivityIntent(todoId, title, snoozeCount, priority, (RC_SNOOZE_TAP + todoId).toInt())
        val channel   = if (priority == 2) CHANNEL_TODO_URGENT else CHANNEL_TODO_MEDIUM

        return when (priority) {
            // ── High snoozed ─────────────────────────────────────────────────
            2 -> {
                val builder = NotificationCompat.Builder(context, channel)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("\"$title\" snoozed")
                    .setContentText("Re-fires at $timeStr \u00b7 $remaining snooze(s) left")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setContentIntent(tapIntent)
                    .setOngoing(true)
                    .setAutoCancel(false)

                if (remaining > 0) {
                    builder.addAction(
                        0, "Snooze again ($remaining left)",
                        snoozeBroadcastIntent(todoId, 10, snoozeCount, priority, (RC_SNOOZE_AGAIN + todoId).toInt()),
                    )
                }
                builder.build()
            }

            // ── Medium snoozed ───────────────────────────────────────────────
            else -> {
                val doneIntent    = doneBroadcastIntent(todoId, (RC_MEDIUM_SNOOZE_DONE + todoId).toInt())
                val dismissIntent = dismissBroadcastIntent(todoId, (RC_DISMISS_SNOOZE + todoId).toInt())

                val builder = NotificationCompat.Builder(context, channel)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("\"$title\" snoozed")
                    .setContentText("Re-fires at $timeStr")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setContentIntent(tapIntent)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .addAction(0, "Done \u2714", doneIntent)

                if (remaining > 0) {
                    builder.addAction(
                        0, "Snooze 30m",
                        snoozeBroadcastIntent(todoId, 30, snoozeCount, priority, (RC_SNOOZE_AGAIN + todoId).toInt()),
                    )
                }
                builder.addAction(0, "Dismiss", dismissIntent)
                builder.build()
            }
        }
    }

    // ── Medium dismissed: soft ongoing reminder ───────────────────────────────
    // Tap → alarm screen (re-shows the medium alarm for snooze/reschedule).
    // Done action completes the todo directly.

    fun buildMediumDismissedNotification(todoId: Long, title: String): Notification {
        val tapIntent  = alarmActivityIntent(todoId, title, 0, 1, (RC_MEDIUM_TAP + todoId).toInt())
        val doneIntent = doneBroadcastIntent(todoId, (RC_MEDIUM_DONE + todoId).toInt())

        return NotificationCompat.Builder(context, CHANNEL_TODO_MEDIUM)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText("Still open \u2014 tap to snooze or reschedule")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(0, "Done \u2714", doneIntent)
            .build()
    }

    // ── Per-todo overdue / digest notification ────────────────────────────────
    //
    // These fire after the initial alarm window has passed. The todo is overdue or
    // undated. Direct Done is acceptable here since there is no active alarm screen.

    fun buildPerTodoReminderNotification(
        todoId: Long,
        title: String,
        priority: Int,
        isOverdue: Boolean,
    ): Notification {
        val tapIntent     = mainActivityIntent((RC_DIGEST_TODO_TAP     + todoId).toInt())
        val doneIntent    = doneBroadcastIntent(todoId, (RC_DIGEST_TODO_DONE    + todoId).toInt())
        val snoozeIntent  = snoozeBroadcastIntent(todoId, 30, 0, priority, (RC_DIGEST_TODO_SNOOZE  + todoId).toInt())
        val dismissIntent = dismissBroadcastIntent(todoId, (RC_DIGEST_TODO_DISMISS + todoId).toInt())
        val contentTitle = when {
            isOverdue && priority == 2 -> "\u26a0\ufe0f Overdue: $title"
            isOverdue                  -> "Overdue: $title"
            else                       -> title
        }
        return when (priority) {
            2 -> NotificationCompat.Builder(context, CHANNEL_TODO_URGENT)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(contentTitle)
                .setContentText("High priority \u2014 still open")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(tapIntent)
                .setAutoCancel(true)
                .setGroup(OVERDUE_GROUP)
                .addAction(0, "Done \u2714", doneIntent)
                .addAction(0, "Snooze 30m", snoozeIntent)
                .addAction(0, "Dismiss", dismissIntent)
                .build()
            1 -> NotificationCompat.Builder(context, CHANNEL_TODO_MEDIUM)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(contentTitle)
                .setContentText(if (isOverdue) "Medium priority \u2014 still open" else "Open task")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(tapIntent)
                .setAutoCancel(true)
                .setGroup(OVERDUE_GROUP)
                .addAction(0, "Done \u2714", doneIntent)
                .addAction(0, "Snooze 30m", snoozeIntent)
                .addAction(0, "Dismiss", dismissIntent)
                .build()
            else -> NotificationCompat.Builder(context, CHANNEL_TODO_DIGEST)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(contentTitle)
                .setContentText(if (isOverdue) "Overdue task" else "Open task")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(tapIntent)
                .setAutoCancel(true)
                .setGroup(OVERDUE_GROUP)
                .addAction(0, "Done \u2714", doneIntent)
                .build()
        }
    }

    // ── Overdue group summary (FGS anchor) ────────────────────────────────────

    fun buildOverdueGroupSummary(count: Int): Notification {
        val tapIntent = mainActivityIntent(RC_OVERDUE_TAP)
        return NotificationCompat.Builder(context, CHANNEL_TODO_OVERDUE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$count todo(s) need attention")
            .setContentText("Tap to view your todos")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setGroup(OVERDUE_GROUP)
            .setGroupSummary(true)
            .build()
    }

    // ── Digest (3x daily) ─────────────────────────────────────────────────────

    fun buildDigestNotification(
        undatedCount: Int,
        overdueCount: Int,
        items: List<String>,
    ): Notification {
        val tapIntent = mainActivityIntent(RC_DIGEST_TAP)
        val title = buildString {
            if (overdueCount > 0) append("$overdueCount overdue")
            if (overdueCount > 0 && undatedCount > 0) append(", ")
            if (undatedCount > 0) append("$undatedCount open")
            append(" todo(s)")
        }
        val bigText = items.take(7).joinToString("\n\u2022 ", prefix = "\u2022 ")

        return NotificationCompat.Builder(context, CHANNEL_TODO_DIGEST)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(items.firstOrNull() ?: "Tap to view your todos")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()
    }

    // ── Overdue (ongoing, persistent) ─────────────────────────────────────────

    fun buildOverdueNotification(overdueCount: Int, titles: List<String>): Notification {
        val tapIntent = mainActivityIntent(RC_OVERDUE_TAP)
        val preview   = titles.take(3).joinToString(", ")

        return NotificationCompat.Builder(context, CHANNEL_TODO_OVERDUE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$overdueCount overdue todo(s)")
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                titles.take(5).joinToString("\n\u2022 ", prefix = "\u2022 ")
            ))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }

    // ── General helpers ───────────────────────────────────────────────────────

    fun showNotification(id: Int, notification: Notification) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(id, notification)
    }

    fun cancelNotification(id: Int) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(id)
    }

    // ── PendingIntent builders ────────────────────────────────────────────────

    fun alarmActivityIntent(
        todoId: Long,
        title: String,
        snoozeCount: Int,
        priority: Int,
        requestCode: Int,
    ): PendingIntent {
        val intent = Intent(context, TodoAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("todoId", todoId)
            putExtra("todoTitle", title)
            putExtra("snoozeCount", snoozeCount)
            putExtra("priority", priority)
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun snoozeBroadcastIntent(
        todoId: Long,
        snoozeMinutes: Int,
        currentCount: Int,
        priority: Int,
        requestCode: Int,
    ): PendingIntent {
        val intent = Intent(context, TodoSnoozeReceiver::class.java).apply {
            putExtra("todoId", todoId)
            putExtra("snoozeMinutes", snoozeMinutes)
            putExtra("snoozeCount", currentCount)
            putExtra("priority", priority)
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun doneBroadcastIntent(todoId: Long, requestCode: Int): PendingIntent {
        val intent = Intent(context, TodoDismissReceiver::class.java).apply {
            action = TodoDismissReceiver.ACTION_DONE
            putExtra("todoId", todoId)
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun dismissBroadcastIntent(todoId: Long, requestCode: Int): PendingIntent {
        val intent = Intent(context, TodoDismissReceiver::class.java).apply {
            action = TodoDismissReceiver.ACTION_DISMISS
            putExtra("todoId", todoId)
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun mainActivityIntent(requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

    private fun formatTime(millis: Long): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
            .format(timeFormatter)
}
