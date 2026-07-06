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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FocusNotificationHelper @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_FOCUS = "focus_mode"
        const val NOTIFICATION_ID_FOCUS = 9001
        const val NOTIFICATION_ID_FOCUS_DONE = 9002
        const val NOTIFICATION_ID_FOCUS_SCHEDULED = 9003

        private const val RC_FOCUS_TAP = 900_001
        private const val RC_FOCUS_END = 900_002
        private const val RC_FOCUS_DONE_TAP = 900_003
        private const val RC_SCHEDULED_TAP = 900_004
        private const val RC_SCHEDULED_START = 900_005
    }

    fun createChannels() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_FOCUS, "Focus Mode", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Running focus sessions and session reminders"
                setSound(null, null)
            }
        )
    }

    /** Ongoing notification while a session runs. "End session" only when not invincible. */
    fun buildOngoingNotification(
        sessionName: String,
        remainingSeconds: Int,
        paused: Boolean,
        invincible: Boolean,
    ): Notification {
        val tap = mainActivityIntent(RC_FOCUS_TAP)
        val builder = NotificationCompat.Builder(context, CHANNEL_FOCUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (paused) "$sessionName (paused)" else sessionName)
            .setContentText(
                if (paused) "Paused - ${formatRemaining(remainingSeconds)} left"
                else "${formatRemaining(remainingSeconds)} remaining"
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(tap)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)

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
            .setSmallIcon(R.drawable.ic_notification)
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
            .setSmallIcon(R.drawable.ic_notification)
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
