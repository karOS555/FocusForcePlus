package com.focusforceplus.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Safety net for the routine session timer. [RoutineForegroundService] schedules an
 * exact alarm at each task's end; when it fires, this receiver pokes the service.
 * If the service is alive it simply double-checks the task end (no-op when the
 * ticker already handled it). If the process was killed, this start re-creates the
 * service, which restores the session from [RoutineSessionStore] and fires the
 * wake-up the user would otherwise never have gotten.
 */
class RoutineTaskEndReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        runCatching {
            context.startForegroundService(
                Intent(context, RoutineForegroundService::class.java).apply {
                    action = RoutineForegroundService.ACTION_TASK_END_CHECK
                }
            )
        }
    }
}
