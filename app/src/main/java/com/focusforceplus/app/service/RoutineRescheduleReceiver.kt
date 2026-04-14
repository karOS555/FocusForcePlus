package com.focusforceplus.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusforceplus.app.data.repository.RoutineRepository
import com.focusforceplus.app.util.AlarmHelper
import com.focusforceplus.app.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RoutineRescheduleReceiver : BroadcastReceiver() {

    @Inject lateinit var alarmHelper: AlarmHelper
    @Inject lateinit var repository: RoutineRepository
    @Inject lateinit var notificationHelper: NotificationHelper

    override fun onReceive(context: Context, intent: Intent) {
        val routineId     = intent.getLongExtra("routineId", 0L)
        val snoozeMinutes = intent.getIntExtra("snoozeMinutes", 30)
        val snoozeCount   = intent.getIntExtra("snoozeCount", 0)
        if (routineId == 0L) return

        // Dismiss any existing alarm or pre-alarm notification immediately.
        notificationHelper.cancelNotification(NotificationHelper.preAlarmNotificationId(routineId))
        notificationHelper.cancelNotification(NotificationHelper.alarmNotificationId(routineId))

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val routine = repository.getRoutineById(routineId).first() ?: return@launch
                val newSnoozeCount = snoozeCount + 1
                val triggerMillis  = System.currentTimeMillis() + snoozeMinutes * 60_000L

                // Cancel today's alarm; reschedule weekly alarms (next occurrence = next week).
                alarmHelper.cancelRoutineAlarms(routineId)
                alarmHelper.scheduleRoutineAlarms(routine)
                // Set a one-time snooze alarm that fires in snoozeMinutes.
                alarmHelper.scheduleSnoozeAlarm(routineId, routine.name, snoozeMinutes)

                // Show a snoozed notification with the new trigger time and updated count.
                notificationHelper.showNotification(
                    NotificationHelper.alarmNotificationId(routineId),
                    notificationHelper.buildSnoozedNotification(
                        routineId      = routineId,
                        routineName    = routine.name,
                        triggerMillis  = triggerMillis,
                        snoozeCount    = newSnoozeCount,
                        maxSnoozeCount = routine.maxSnoozeCount,
                        invincibleMode = routine.invincibleMode,
                    ),
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
