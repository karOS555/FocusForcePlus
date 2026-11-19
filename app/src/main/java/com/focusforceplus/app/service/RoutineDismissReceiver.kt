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
 * Handles "Dismiss" on the heads-up before the start, "Cancel Routine" on a ringing
 * or snoozed alarm, and swipe-dismissals of the ringing alarm notification
 * ([ACTION_ALARM_SWIPED]): swiping an alarm away is not a decision, so the
 * notification simply comes back until the user acts on it (Start, Snooze,
 * Reschedule, or the explicit Cancel action).
 *
 * An invincible routine has no Cancel action, and this receiver does not take its
 * word for that: whatever asks, an invincible run only loses its heads-up here.
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
                                iconKey = routine.iconKey,
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

        // The heads-up before the start can always go.
        notificationHelper.cancelNotification(NotificationHelper.preAlarmNotificationId(routineId))

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // An invincible run can be started, snoozed or moved, not skipped. The
                // heads-up's "Dismiss" comes through here as well, and it may still be
                // in the shade while the alarm rings or after the run was moved: it
                // must not take the alarm or the moved run with it.
                val invincible = repository.getRoutineById(routineId).first()?.invincibleMode == true
                if (invincible) return@launch

                notificationHelper.cancelNotification(NotificationHelper.alarmNotificationId(routineId))
                // Cancel any pending reschedule alarm and clear the tracker.
                alarmHelper.cancelRescheduleAlarm(routineId)
                rescheduleTracker.clear(routineId)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
