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
import com.focusforceplus.app.service.FocusSessionReceiver
import com.focusforceplus.app.util.NotificationStyle.applyBrand
import com.focusforceplus.app.util.NotificationStyle.asLiveUpdate
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FocusNotificationHelper @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_FOCUS = "focus_mode"

        /** Quiet-but-not-silent channel for the running session — see NotificationStyle. */
        const val CHANNEL_FOCUS_LIVE = "focus_live"
        const val NOTIFICATION_ID_FOCUS = 9001
        const val NOTIFICATION_ID_FOCUS_DONE = 9002
        const val NOTIFICATION_ID_FOCUS_SCHEDULED = 9003

        private const val RC_FOCUS_TAP = 900_001
        private const val RC_FOCUS_END = 900_002
        private const val RC_FOCUS_DONE_TAP = 900_003
        private const val RC_SCHEDULED_TAP = 900_004
        private const val RC_SCHEDULED_START = 900_005

        private const val MODULE = "Focus"
    }

    fun createChannels() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_FOCUS, "Focus Mode", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Session reminders and completion"
                group = NotificationStyle.GROUP_REMINDERS
                setSound(null, null)
            }
        )
        manager.createNotificationChannel(
            NotificationStyle.liveSessionChannel(
                CHANNEL_FOCUS_LIVE,
                "Running focus session",
                "Live countdown of the focus session you are in",
            )
        )
    }

    /**
     * Ongoing notification while a session runs, drawn as a progress timeline:
     * the part already focused, the part still to go, and a marker at the halfway
     * point. On Android 16 / One UI it is promoted to a Live Update, so the
     * remaining time also sits in the status bar chip and the Now Bar.
     *
     * The countdown itself renders via the system chronometer; the service
     * re-posts on a slow tick to advance the tracker.
     */
    fun buildOngoingNotification(
        sessionName: String,
        remainingSeconds: Int,
        paused: Boolean,
        invincible: Boolean,
        totalSeconds: Int = 0,
    ): Notification {
        val tap = mainActivityIntent(RC_FOCUS_TAP)
        val total = totalSeconds.coerceAtLeast(remainingSeconds).coerceAtLeast(1)
        val elapsed = (total - remainingSeconds).coerceIn(0, total)

        val builder = NotificationCompat.Builder(context, CHANNEL_FOCUS_LIVE)
            .applyBrand(MODULE)
            .setContentTitle(if (paused) "$sessionName (paused)" else sessionName)
            .setContentText(
                if (paused) "Paused - ${NotificationStyle.formatDuration(remainingSeconds)} left"
                else "${NotificationStyle.formatDuration(remainingSeconds)} of focus left"
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(tap)
            .setAutoCancel(false)
            .asLiveUpdate(NotificationStyle.formatChip(remainingSeconds))

        // Two segments — done and to go — plus a marker at halfway, which is the
        // one milestone a single block of deep work actually has.
        NotificationStyle.timeline(
            segments = listOf(
                NotificationStyle.TimelineSegment(
                    elapsed.coerceAtLeast(1),
                    if (paused) NotificationStyle.SEGMENT_AHEAD else NotificationStyle.SEGMENT_DONE,
                ),
                NotificationStyle.TimelineSegment(
                    (total - elapsed).coerceAtLeast(1),
                    NotificationStyle.SEGMENT_AHEAD,
                ),
            ),
            progress = elapsed,
            points = listOf(total / 2),
        )?.let { builder.setStyle(it) }

        if (!paused) {
            builder
                .setWhen(System.currentTimeMillis() + remainingSeconds * 1_000L)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        }

        if (!invincible) {
            val endIntent = PendingIntent.getBroadcast(
                context, RC_FOCUS_END,
                Intent(context, FocusSessionReceiver::class.java).apply {
                    action = FocusSessionReceiver.ACTION_END_SESSION
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, "End session", endIntent)
        }
        return builder.build()
    }

    fun buildCompletionNotification(sessionName: String, focusedMinutes: Int): Notification =
        NotificationCompat.Builder(context, CHANNEL_FOCUS)
            .applyBrand(MODULE)
            .setContentTitle("Focus session complete")
            .setContentText("\"$sessionName\" done - $focusedMinutes minutes of focus. Well done!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(mainActivityIntent(RC_FOCUS_DONE_TAP))
            .setAutoCancel(true)
            .build()

    /** Reminder fired by [com.focusforceplus.app.service.FocusAlarmReceiver] for scheduled sessions. */
    fun buildScheduledSessionNotification(sessionId: Long, sessionName: String): Notification {
        val startIntent = PendingIntent.getBroadcast(
            context, RC_SCHEDULED_START + sessionId.toInt(),
            Intent(context, FocusSessionReceiver::class.java).apply {
                action = FocusSessionReceiver.ACTION_START_SCHEDULED
                putExtra("sessionId", sessionId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_FOCUS)
            .applyBrand(MODULE)
            .setContentTitle("Time to focus")
            .setContentText("\"$sessionName\" is scheduled for now.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(mainActivityIntent(RC_SCHEDULED_TAP))
            .setAutoCancel(true)
            .addAction(0, "Start now", startIntent)
            .build()
    }

    fun showNotification(id: Int, notification: Notification) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(id, notification)
    }

    fun cancelNotification(id: Int) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(id)
    }

    private fun mainActivityIntent(requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context, requestCode,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun formatRemaining(totalSeconds: Int): String {
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return "%d:%02d".format(m, s)
    }
}
