package com.focusforceplus.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusforceplus.app.util.AlarmHelper
import com.focusforceplus.app.util.NotificationHelper
import com.focusforceplus.app.util.PendingRescheduleTracker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Handles "Dismiss" taps from alarm and snooze notifications. */
@AndroidEntryPoint
class RoutineDismissReceiver : BroadcastReceiver() {

    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var alarmHelper: AlarmHelper
    @Inject lateinit var rescheduleTracker: PendingRescheduleTracker

    override fun onReceive(context: Context, intent: Intent) {
        val routineId = intent.getLongExtra("routineId", 0L)
        if (routineId == 0L) return

        // Cancel both the alarm and pre-alarm notifications.
        notificationHelper.cancelNotification(NotificationHelper.alarmNotificationId(routineId))
        notificationHelper.cancelNotification(NotificationHelper.preAlarmNotificationId(routineId))

        // Cancel any pending reschedule alarm and clear the tracker.
        alarmHelper.cancelRescheduleAlarm(routineId)
        rescheduleTracker.clear(routineId)
    }
}
