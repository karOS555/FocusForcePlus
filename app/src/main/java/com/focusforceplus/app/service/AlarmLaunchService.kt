package com.focusforceplus.app.service

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.focusforceplus.app.ui.alarm.RoutineAlarmActivity
import com.focusforceplus.app.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Short-lived foreground service whose only job is to start [RoutineAlarmActivity].
 *
 * Why a service? On Android 10/11, BroadcastReceivers cannot start activities while the screen
 * is on (BAL restriction). A *foreground* service is exempt from this restriction on all
 * supported API levels, and on Android 12+ it inherits the setAlarmClock() BAL exemption from
 * the receiver that started it.
 *
 * The service posts the alarm notification as its foreground notification, then immediately
 * detaches the notification (so it stays visible) and stops itself. The receiver's async
 * coroutine later re-posts the same notification with full routine settings (invincibleMode, etc.).
 */
@AndroidEntryPoint
class AlarmLaunchService : Service() {

    @Inject lateinit var notificationHelper: NotificationHelper

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val routineId   = intent?.getLongExtra("routineId", 0L) ?: 0L
        val routineName = intent?.getStringExtra("routineName") ?: ""

        // Must call startForeground() within 5 seconds of startForegroundService().
        val notifId = NotificationHelper.alarmNotificationId(routineId)
        startForeground(
            notifId,
            notificationHelper.buildAlarmNotification(routineId, routineName),
        )

        // Start the full-screen alarm activity. Works because a foreground service is allowed
        // to start activities from background (Android 10+ BAL exception).
        startActivity(
            Intent(this, RoutineAlarmActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra("routineId", routineId)
                putExtra("routineName", routineName)
                putExtra("snoozeCount", 0)
                putExtra("isPreAlarm", false)
            }
        )

        // Detach (keep) the foreground notification, then stop the service.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(false) // false = keep the notification
        }
        stopSelf(startId)

        return START_NOT_STICKY
    }
}
