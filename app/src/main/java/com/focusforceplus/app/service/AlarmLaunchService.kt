package com.focusforceplus.app.service

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.focusforceplus.app.data.repository.RoutineRepository
import com.focusforceplus.app.ui.alarm.RoutineAlarmActivity
import com.focusforceplus.app.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
 * detaches the notification (so it stays visible) and stops itself. That first notification
 * goes up before the routine is read, so it is the cautious one; the one with the routine's
 * own settings follows a moment later.
 */
@AndroidEntryPoint
class AlarmLaunchService : Service() {

    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var repository: RoutineRepository

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val routineId   = intent?.getLongExtra("routineId", 0L) ?: 0L
        val routineName = intent?.getStringExtra("routineName") ?: ""

        // Must call startForeground() within 5 seconds of startForegroundService(), so
        // this goes up before the routine is read. Until then it leaves out "Cancel
        // Routine", as an invincible routine's notification does: an action the
        // routine may not allow must not be offered, not even for a moment.
        val notifId = NotificationHelper.alarmNotificationId(routineId)
        startForeground(
            notifId,
            notificationHelper.buildAlarmNotification(routineId, routineName, invincibleMode = true),
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

        // The receiver posts the notification with the routine's settings too, but it
        // can get there before the one above, which then replaced it for good: every
        // routine, invincible or not, ended up with the same default notification.
        // This one always comes after it.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val routine = repository.getRoutineById(routineId).first() ?: return@launch
            // Lets the notification above reach the shade first. If it is gone by
            // then, the alarm has been answered and there is nothing to bring back.
            delay(SETTLE_MILLIS)
            if (!notificationHelper.isShowing(notifId)) return@launch
            notificationHelper.showNotification(
                notifId,
                notificationHelper.buildAlarmNotification(
                    routineId      = routineId,
                    routineName    = routine.name,
                    iconKey        = routine.iconKey,
                    snoozeCount    = 0,
                    maxSnoozeCount = routine.maxSnoozeCount,
                    invincibleMode = routine.invincibleMode,
                ),
            )
        }

        return START_NOT_STICKY
    }

    private companion object {
        const val SETTLE_MILLIS = 300L
    }
}
