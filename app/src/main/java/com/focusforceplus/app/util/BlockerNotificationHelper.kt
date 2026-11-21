package com.focusforceplus.app.util

import com.focusforceplus.app.ui.navigation.AppModule
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.focusforceplus.app.MainActivity
import com.focusforceplus.app.util.NotificationStyle.applyBrand
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlockerNotificationHelper @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_BLOCKER = "app_blocker"
        const val NOTIFICATION_ID_SERVICE_LOST = 7001
        const val NOTIFICATION_ID_SHORTS_LOST = 7002
        private const val RC_SERVICE_LOST_TAP = 700_001
        private const val RC_SHORTS_LOST_TAP = 700_002
    }

    fun createChannels() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_BLOCKER,
                "App Blocker",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Status updates from the app blocker"
                group = NotificationStyle.GROUP_REMINDERS
            }
        )
    }

    /**
     * Informational notification for when the accessibility service is disconnected
     * while blocking rules exist. Policy constraints (Golden Rules #2/#3): purely
     * informative wording, no pressure, no action that pushes the user back into
     * system settings, and never any auto-restart of the service.
     */
    fun showServiceLostNotification() {
        val tapIntent = PendingIntent.getActivity(
            context, RC_SERVICE_LOST_TAP,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_BLOCKER)
            .applyBrand(context, AppModule.BLOCKER)
            .setContentTitle("App blocking is paused")
            .setContentText("The accessibility service was turned off, so blocked apps are no longer covered.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "The accessibility service was turned off, so blocked apps are no " +
                        "longer covered. Your blocks are unchanged. Turn the service back " +
                        "on from the Blocker tab whenever you want blocking back."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID_SERVICE_LOST, notification)
    }

    /**
     * The same for the short-video service: it was switched off while Reels or Shorts
     * were meant to be left. Informative only, like the one above.
     */
    fun showShortVideoServiceLostNotification() {
        val tapIntent = PendingIntent.getActivity(
            context, RC_SHORTS_LOST_TAP,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = "The short-video service was turned off, so Reels and Shorts stay open again."
        val notification = NotificationCompat.Builder(context, CHANNEL_BLOCKER)
            .applyBrand(context, AppModule.BLOCKER)
            .setContentTitle("Short videos are no longer left")
            .setContentText(text)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$text Your settings are unchanged. Turn the service back on from the " +
                        "Blocker tab whenever you want it back."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID_SHORTS_LOST, notification)
    }
}
