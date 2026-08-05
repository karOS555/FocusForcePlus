package com.focusforceplus.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusforceplus.app.data.repository.RoutineRepository
import com.focusforceplus.app.util.AlarmHelper
import com.focusforceplus.app.util.NotificationHelper
import com.focusforceplus.app.util.PendingRescheduleTracker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handles "Dismiss" taps from alarm and snooze notifications, and swipe-dismissals
 * of the ringing alarm notification ([ACTION_ALARM_SWIPED]): swiping an alarm away
 * is not a decision, so the notification simply comes back until the user acts on
 * it (Start, Snooze, Reschedule, or the explicit Cancel action).
 */
@AndroidEntryPoint
class RoutineDismissReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ALARM_SWIPED = "com.focusforceplus.app.ROUTINE_ALARM_SWIPED"
    }

    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var alarmHelper: AlarmHelper
    @Inject lateinit var rescheduleTracker: PendingRescheduleTracker
    @Inject lateinit var repository: RoutineRepository

    override fun onReceive(context: Context, intent: Intent) {
        val routineId = intent.getLongExtra("routineId", 0L)
        if (routineId == 0L) return

        if (intent.action == ACTION_ALARM_SWIPED) {
            val pendingResult = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    repository.getRoutineById(routineId).first()?.let { routine ->
                        notificationHelper.showNotification(
                            NotificationHelper.alarmNotificationId(routineId),
                            notificationHelper.buildAlarmNotification(
                                routineId      = routineId,
                                routineName    = routine.name,
                                snoozeCount    = intent.getIntExtra("snoozeCount", 0),
                                maxSnoozeCount = routine.maxSnoozeCount,
                                invincibleMode = routine.invincibleMode,
                            ),
                        )
                    }
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        // Cancel both the alarm and pre-alarm notifications.
        notificationHelper.cancelNotification(NotificationHelper.alarmNotificationId(routineId))
        notificationHelper.cancelNotification(NotificationHelper.preAlarmNotificationId(routineId))

        // Cancel any pending reschedule alarm and clear the tracker.
        alarmHelper.cancelRescheduleAlarm(routineId)
        rescheduleTracker.clear(routineId)
    }
}
