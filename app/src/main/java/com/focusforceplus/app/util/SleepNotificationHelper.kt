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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepNotificationHelper @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_SLEEP = "sleep_channel"
        const val NOTIFICATION_ID_WIND_DOWN = 970_001
        const val NOTIFICATION_ID_SLEEP_ACTIVE = 970_002

        private const val RC_TAP = 710_000
    }

    fun createChannels() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SLEEP, "Sleep Mode", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Wind-down reminders and sleep mode status"
            }
        )
    }

    fun buildWindDownNotification(bedtimeText: String, minutesLeft: Int): Notification =
        NotificationCompat.Builder(context, CHANNEL_SLEEP)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Bedtime at $bedtimeText")
            .setContentText("Wind down — $minutesLeft minutes until sleep mode starts.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(tapIntent())
            .setAutoCancel(true)
            .build()

    fun buildSleepActiveNotification(wakeText: String, blocking: Boolean): Notification =
        NotificationCompat.Builder(context, CHANNEL_SLEEP)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Sleep mode until $wakeText")
            .setContentText(
                if (blocking) "Distracting apps are blocked. Sleep well."
                else "Sleep well."
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(tapIntent())
            .setOngoing(true)
            .setSilent(true)
            .setAutoCancel(false)
            .build()

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
