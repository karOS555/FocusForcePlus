package com.focusforceplus.app.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.focusforceplus.app.MainActivity
import com.focusforceplus.app.util.NotificationStyle.applyBrand
import com.focusforceplus.app.util.NotificationStyle.asLiveUpdate
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepNotificationHelper @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_SLEEP = "sleep_channel"

        /** Quiet-but-not-silent channel for the running night — see NotificationStyle. */
        const val CHANNEL_SLEEP_LIVE = "sleep_live"
        const val NOTIFICATION_ID_WIND_DOWN = 970_001
        const val NOTIFICATION_ID_SLEEP_ACTIVE = 970_002

        private const val RC_TAP = 710_000

        private const val MODULE = "Sleep"
    }

    fun createChannels() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SLEEP, "Sleep Mode", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Wind-down reminders"
                group = NotificationStyle.GROUP_REMINDERS
            }
        )
        manager.createNotificationChannel(
            NotificationStyle.liveSessionChannel(
                CHANNEL_SLEEP_LIVE,
                "Sleep mode running",
                "How much of the night is left, until wake-up",
            )
        )
    }

    /**
     * Wind-down heads-up. It carries a countdown timeline of the minutes left
     * before bedtime, which is a lot easier to act on than a number in a sentence.
     */
    fun buildWindDownNotification(bedtimeText: String, minutesLeft: Int): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_SLEEP)
            .applyBrand(MODULE)
            .setContentTitle("Bedtime at $bedtimeText")
            .setContentText("Wind down — $minutesLeft minutes until sleep mode starts.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(tapIntent())
            .setAutoCancel(true)
            .setWhen(System.currentTimeMillis() + minutesLeft * 60_000L)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
        return builder.build()
    }

    /**
     * The night itself as a timeline: how much of it is behind you and how much is
     * left until the wake-up time. Promoted to a Live Update so a glance at the
     * lock screen or the Now Bar answers "how long can I still sleep".
     */
    fun buildSleepActiveNotification(
        wakeText: String,
        blocking: Boolean,
        elapsedMinutes: Int = 0,
        totalMinutes: Int = 0,
    ): Notification {
        val total = totalMinutes.coerceAtLeast(1)
        val elapsed = elapsedMinutes.coerceIn(0, total)
        val remaining = total - elapsed

        val builder = NotificationCompat.Builder(context, CHANNEL_SLEEP_LIVE)
            .applyBrand(MODULE)
            .setContentTitle("Sleep mode until $wakeText")
            .setContentText(
                buildString {
                    if (totalMinutes > 0) {
                        append(NotificationStyle.formatDuration(remaining * 60))
                        append(" until wake-up")
                    } else {
                        append("Sleep well")
                    }
                    if (blocking) append(" · distracting apps blocked")
                }
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(tapIntent())
            .setAutoCancel(false)
            .asLiveUpdate(if (totalMinutes > 0) NotificationStyle.formatChip(remaining * 60) else null)

        if (totalMinutes > 0) {
            // The system chronometer keeps "until wake-up" ticking through the whole
            // night without this notification ever being re-posted.
            builder
                .setWhen(System.currentTimeMillis() + remaining * 60_000L)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
            NotificationStyle.timeline(
                segments = listOf(
                    NotificationStyle.TimelineSegment(elapsed.coerceAtLeast(1), NotificationStyle.SEGMENT_DONE),
                    NotificationStyle.TimelineSegment(remaining.coerceAtLeast(1), NotificationStyle.SEGMENT_AHEAD),
                ),
                progress = elapsed,
            )?.let { builder.setStyle(it) }
        }
        return builder.build()
    }

    fun showNotification(id: Int, notification: Notification) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(id, notification)
    }

    fun cancelNotification(id: Int) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(id)
    }

    private fun tapIntent(): PendingIntent =
        PendingIntent.getActivity(
            context, RC_TAP,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
