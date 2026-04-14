package com.focusforceplus.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusforceplus.app.data.repository.RoutineRepository
import com.focusforceplus.app.ui.alarm.RoutineAlarmActivity
import com.focusforceplus.app.util.AlarmEventBus
import com.focusforceplus.app.util.AlarmEvent
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

@AndroidEntryPoint
class RoutineAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var alarmHelper: AlarmHelper
    @Inject lateinit var repository: RoutineRepository
    @Inject lateinit var alarmEventBus: AlarmEventBus
    @Inject lateinit var rescheduleTracker: PendingRescheduleTracker

    override fun onReceive(context: Context, intent: Intent) {
        val routineId = intent.getLongExtra("routineId", 0L)
        if (routineId == 0L) return

        val routineName = intent.getStringExtra("routineName") ?: ""

        val alarmActivityIntent = Intent(context, RoutineAlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra("routineId", routineId)
            putExtra("routineName", routineName)
            putExtra("snoozeCount", 0)
            putExtra("isPreAlarm", false)
        }

        // Path A — Android 12+: setAlarmClock() grants a temporary BAL allowlist that lets the
        // receiver start activities directly. Try this first since it is the fastest path.
        runCatching { context.startActivity(alarmActivityIntent) }

        // Path B — Android 10/11: The foreground-service BAL exemption covers these versions.
        // AlarmLaunchService calls startForeground() + startActivity() from within the service.
        // On Android 12+ this is a no-op in practice (the activity is already running from Path A),
        // but having the service post the notification ensures it survives stopForeground(detach).
        context.startForegroundService(
            Intent(context, AlarmLaunchService::class.java).apply {
                putExtra("routineId", routineId)
                putExtra("routineName", routineName)
            }
        )

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                repository.getRoutineById(routineId).first()?.let { routine ->
                    rescheduleTracker.clear(routineId)

                    // Re-post notification with full routine settings (invincibleMode, etc.).
                    notificationHelper.showNotification(
                        NotificationHelper.alarmNotificationId(routineId),
                        notificationHelper.buildAlarmNotification(
                            routineId      = routineId,
                            routineName    = routine.name,
                            snoozeCount    = 0,
                            maxSnoozeCount = routine.maxSnoozeCount,
                            invincibleMode = routine.invincibleMode,
                        ),
                    )

                    // Deliver via bus if MainActivity is already in foreground.
                    alarmEventBus.emit(
                        AlarmEvent(
                            routineId          = routineId,
                            routineName        = routine.name,
                            invincibleMode     = routine.invincibleMode,
                            maxSnoozeCount     = routine.maxSnoozeCount,
                            maxRescheduleCount = routine.maxRescheduleCount,
                        )
                    )

                    alarmHelper.scheduleRoutineAlarms(routine)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
