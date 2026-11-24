package com.focusforceplus.app.util

import com.focusforceplus.app.ui.navigation.AppModule
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.focusforceplus.app.MainActivity
import com.focusforceplus.app.data.db.entity.TodoEntity
import com.focusforceplus.app.service.TodoActionReceiver
import com.focusforceplus.app.ui.alarm.TodoAlarmActivity
import com.focusforceplus.app.ui.common.RoutineIcons
import com.focusforceplus.app.util.NotificationStyle.applyBrand
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds and posts every todo notification.
 *
 * There is exactly one notification per todo, under one id, and it only changes
 * what it says: ringing, still open, or snoozed. Before, a single todo could own an
 * alarm notification, a daily reminder and a share of an "overdue" summary at the
 * same time, which is what made the shade hard to read.
 *
 * None of these are tied to a running service except while an alarm is actually
 * ringing. A notification posted this way stays in the shade when the system kills
 * the app's process, which some phones do every night; one that belongs to a
 * foreground service disappears with it.
 */
@Singleton
class TodoNotifications @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        /** High priority, ringing. Silent as a channel: the alarm service makes the sound. */
        const val CHANNEL_RING = "todo_ring"
        /** Medium priority, alerting: shows on top, makes no sound. */
        const val CHANNEL_MEDIUM = "todo_medium"
        /** Everything that just sits there: still open, snoozed. */
        const val CHANNEL_OPEN = "todo_open"
        /** Low priority and the daily overview: an ordinary notification. */
        const val CHANNEL_DIGEST = "todo_digest"

        private val RETIRED_CHANNELS = listOf("todo_urgent", "todo_overdue")

        private const val GROUP_KEY = "com.focusforceplus.app.TODOS"
        private const val ID_BASE = 1_000_000
        const val ID_SUMMARY = 990_001
        const val ID_DAILY = 990_002

        private const val MODULE = "Todo"
        private const val COLOR_HIGH = 0xFFF87171.toInt()
        private const val COLOR_MEDIUM = 0xFFFBBF24.toInt()

        fun notificationId(todoId: Long): Int = ID_BASE + (todoId % 1_000_000).toInt()
    }

    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createChannels() {
        RETIRED_CHANNELS.forEach { runCatching { manager.deleteNotificationChannel(it) } }
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RING, "Todo alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "High-priority todos that are due. The alarm sound comes from the app itself."
                group = NotificationStyle.GROUP_ALARMS
                setSound(null, null)
                enableVibration(false)
                enableLights(true)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_MEDIUM, "Todo reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Medium-priority todos that are due. Shown on top, without sound."
                group = NotificationStyle.GROUP_ALARMS
                enableVibration(false)
                setSound(null, null)
            }
        )
        manager.createNotificationChannel(
            // Default importance with the sound removed: quiet, but not "silent" in
            // Android's sense, which some phones hide from the lock screen.
            NotificationChannel(CHANNEL_OPEN, "Open todos", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Todos that are still waiting for you, and snoozed ones."
                group = NotificationStyle.GROUP_REMINDERS
                setSound(null, null)
                enableVibration(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DIGEST, "Low priority and daily overview", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Low-priority todos and the overview at your daily reminder times."
                group = NotificationStyle.GROUP_REMINDERS
            }
        )
    }

    // ── States ────────────────────────────────────────────────────────────────

    /**
     * The todo is ringing right now.
     *
     * [onScreen] is true while the alarm screen itself is in front. The notification
     * then goes out on the quiet channel without a full-screen intent, because a
     * heads-up banner on top of the app's own alarm screen only covers it up and
     * offers a second set of buttons for the same alarm. As soon as the screen is
     * not showing, the loud version takes over.
     */
    fun ringing(todo: TodoEntity, onScreen: Boolean = false): Notification {
        val high = todo.priority == 2
        val open = openIntent(todo.id)
        val channel = when {
            onScreen -> CHANNEL_OPEN
            high -> CHANNEL_RING
            else -> CHANNEL_MEDIUM
        }
        val builder = base(todo, channel)
            .setContentTitle(todo.title)
            .setContentText(if (high) "High priority \u00B7 due now" else "Due now")
            .setCategory(if (high) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setOngoing(true)
            .setAutoCancel(false)
            .setDeleteIntent(actionIntent(todo.id, TodoActionReceiver.ACTION_SWIPED))
            .addAction(0, "Open", open)
        if (onScreen) {
            builder.setPriority(NotificationCompat.PRIORITY_DEFAULT).setOnlyAlertOnce(true)
        } else {
            builder
                .setPriority(if (high) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
                .setFullScreenIntent(open, true)
        }
        return builder.build()
    }

    /**
     * The todo is due and unanswered, but not making noise at the moment.
     * [nextRingAt] is when it will ring again, if it will. [doneDirect] and
     * [snoozeDirect] say whether those actions work straight from the notification
     * or have a guard in front of them and need the alarm screen.
     */
    fun waiting(
        todo: TodoEntity,
        nextRingAt: Long?,
        doneDirect: Boolean,
        snoozeDirect: Boolean,
    ): Notification {
        val open = openIntent(todo.id)
        val since = todo.dueDateTime ?: todo.lastAlarmAt
        val text = buildString {
            append(if (todo.dueDateTime != null) "Due since " else "Open since ")
            append(since?.let(::formatMoment) ?: "today")
            when {
                nextRingAt != null -> append(" · rings again ${formatClock(nextRingAt)}")
                // Dismissed, reopened, restored, or already overdue when it came in:
                // in every case it now waits for the next daily reminder.
                todo.nagCount == TodoEntity.NAG_DISMISSED -> append(" · quiet until the next daily reminder")
            }
        }
        val builder = base(todo, CHANNEL_OPEN)
            .setContentTitle(todo.title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setDeleteIntent(actionIntent(todo.id, TodoActionReceiver.ACTION_SWIPED))

        if (doneDirect) {
            builder.addAction(0, "Done", actionIntent(todo.id, TodoActionReceiver.ACTION_DONE))
        }
        if (snoozeDirect && todo.snoozeCount < todo.maxSnoozeCount) {
            builder.addAction(
                0, "Snooze 10 min",
                actionIntent(todo.id, TodoActionReceiver.ACTION_SNOOZE, minutes = 10),
            )
        }
        builder.addAction(0, "Open", open)
        return builder.build()
    }

    fun snoozed(todo: TodoEntity): Notification {
        val until = todo.snoozedUntil ?: System.currentTimeMillis()
        val left = (todo.maxSnoozeCount - todo.snoozeCount).coerceAtLeast(0)
        val open = openIntent(todo.id)
        return base(todo, CHANNEL_OPEN)
            .setContentTitle(todo.title)
            .setContentText(
                "Snoozed until ${formatClock(until)}" +
                    if (todo.priority == 2) " · $left snooze(s) left" else ""
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setWhen(until)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setDeleteIntent(actionIntent(todo.id, TodoActionReceiver.ACTION_SWIPED))
            .addAction(0, "Open", open)
            .build()
    }

    /**
     * Low priority: one ordinary notification, swiped away freely. Done works with a
     * tap, unless this todo has a speed bump in front of it ([doneDirect] is false):
     * then the button opens the todo, which is where that speed bump is.
     *
     * It stands on its own, outside the group of the medium and high todos. That
     * group's header goes away with the last of them, and Android removes
     * everything in a group together with its header.
     */
    fun lowDue(todo: TodoEntity, doneDirect: Boolean): Notification {
        val builder = base(todo, CHANNEL_DIGEST, grouped = false)
            .setContentTitle(todo.title)
            .setContentText(if (todo.dueDateTime != null) "Due ${formatMoment(todo.dueDateTime)}" else "Open todo")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(listIntent())
            .setAutoCancel(true)
        if (doneDirect) {
            builder.addAction(0, "Done", actionIntent(todo.id, TodoActionReceiver.ACTION_DONE))
        } else {
            builder.addAction(0, "Open", openIntent(todo.id))
        }
        return builder.build()
    }

    /**
     * Header that the medium and high todo notifications collapse under. It has no
     * badge: that belongs to the todos below it.
     */
    fun summary(count: Int): Notification =
        NotificationCompat.Builder(context, CHANNEL_OPEN)
            .applyBrand(context, AppModule.TODOS, MODULE, badge = false)
            .setContentTitle(if (count == 1) "1 todo needs you" else "$count todos need you")
            .setContentIntent(listIntent())
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()

    /** The overview of low-priority todos at a daily reminder time. */
    fun dailyOverview(titles: List<String>): Notification {
        val lines = titles.take(8).joinToString("\n")
        return NotificationCompat.Builder(context, CHANNEL_DIGEST)
            .applyBrand(context, AppModule.TODOS, MODULE)
            // What is open goes first; how many there are is the smaller line.
            .setContentTitle(
                when (titles.size) {
                    1 -> titles.first()
                    2 -> "${titles[0]} and ${titles[1]}"
                    else -> "${titles.first()} and ${titles.size - 1} more"
                }
            )
            .setContentText(if (titles.size == 1) "Open todo" else "${titles.size} open todos")
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(listIntent())
            .setAutoCancel(true)
            .build()
    }

    // ── Posting ───────────────────────────────────────────────────────────────

    fun post(todoId: Long, notification: Notification) {
        runCatching { manager.notify(notificationId(todoId), notification) }
    }

    fun cancel(todoId: Long) {
        manager.cancel(notificationId(todoId))
    }

    fun postSummary(count: Int) {
        runCatching { manager.notify(ID_SUMMARY, summary(count)) }
    }

    fun cancelSummary() = manager.cancel(ID_SUMMARY)

    fun postDaily(titles: List<String>) {
        runCatching { manager.notify(ID_DAILY, dailyOverview(titles)) }
    }

    fun cancelDaily() = manager.cancel(ID_DAILY)

    /**
     * Removes notifications posted under the ids of 0.9.3 and older. Those ids were
     * computed from the todo id and overlap other notifications of the app, so the
     * engine calls this once after the update and never again.
     */
    fun cancelLegacy(todoIds: Collection<Long>) {
        manager.cancel(6001)
        manager.cancel(6002)
        todoIds.forEach { id ->
            manager.cancel((4_000 + id).toInt())
            manager.cancel((8_000 + id).toInt())
        }
    }

    // ── Pieces ────────────────────────────────────────────────────────────────

    private fun base(todo: TodoEntity, channel: String, grouped: Boolean = true): NotificationCompat.Builder {
        val color = when (todo.priority) {
            2 -> COLOR_HIGH
            1 -> COLOR_MEDIUM
            else -> NotificationStyle.ACCENT
        }
        val builder = NotificationCompat.Builder(context, channel)
            .applyBrand(context, AppModule.TODOS, MODULE, RoutineIcons.find(todo.iconKey))
            .setColor(color)
        if (grouped) {
            builder.setGroup(GROUP_KEY).setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
        }
        if (!todo.description.isNullOrBlank()) {
            builder.setStyle(
                NotificationCompat.BigTextStyle().setSummaryText(todo.description.take(80))
            )
        }
        return builder
    }

    private fun openIntent(todoId: Long): PendingIntent =
        PendingIntent.getActivity(
            context, 1,
            TodoAlarmActivity.intent(context, todoId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun actionIntent(todoId: Long, action: String, minutes: Int = 0): PendingIntent {
        val intent = Intent(context, TodoActionReceiver::class.java).apply {
            this.action = action
            data = Uri.parse("focusforce://todo/$todoId/$action/$minutes")
            putExtra(TodoAlarmScheduler.EXTRA_TODO_ID, todoId)
            putExtra(TodoActionReceiver.EXTRA_MINUTES, minutes)
        }
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun listIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_ROUTE, "todos")
            data = Uri.parse("focusforce://open/todos")
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private val clock = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private val dayAndClock = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.ENGLISH)
    private val dateAndClock = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.ENGLISH)

    private fun local(millis: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())

    private fun formatClock(millis: Long): String = local(millis).format(clock)

    /** "14:00" today, "Mon 14:00" within a week, "Nov 3, 14:00" beyond that. */
    private fun formatMoment(millis: Long): String {
        val moment = local(millis)
        val today = LocalDate.now()
        val days = java.time.temporal.ChronoUnit.DAYS.between(moment.toLocalDate(), today)
        return when {
            days == 0L -> moment.format(clock)
            days in -6..6 -> moment.format(dayAndClock)
            else -> moment.format(dateAndClock)
        }
    }
}
