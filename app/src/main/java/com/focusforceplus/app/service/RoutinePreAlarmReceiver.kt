package com.focusforceplus.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusforceplus.app.data.repository.RoutineRepository
import com.focusforceplus.app.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RoutinePreAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var repository: RoutineRepository

    override fun onReceive(context: Context, intent: Intent) {
        val routineId   = intent.getLongExtra("routineId", 0L)
        if (routineId == 0L) return
        val routineName = intent.getStringExtra("routineName") ?: ""
        val startHour   = intent.getIntExtra("startHour", 0)
        val startMinute = intent.getIntExtra("startMinute", 0)

        // Show notification immediately with basic info.
        notificationHelper.showNotification(
            NotificationHelper.preAlarmNotificationId(routineId),
            notificationHelper.buildPreAlarmNotification(routineId, routineName, startHour, startMinute),
        )

        // Re-post with correct invincibleMode once we have the routine from DB.
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                repository.getRoutineById(routineId).first()?.let { routine ->
                    notificationHelper.showNotification(
                        NotificationHelper.preAlarmNotificationId(routineId),
                        notificationHelper.buildPreAlarmNotification(
                            routineId      = routineId,
                            routineName    = routine.name,
                            startHour      = startHour,
                            startMinute    = startMinute,
                            invincibleMode = routine.invincibleMode,
                        ),
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
