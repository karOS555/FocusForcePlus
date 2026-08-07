package com.focusforceplus.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles notification button actions for the active-routine notifications.
 * Forwards the action to [RoutineForegroundService], which owns the session.
 */
class RoutineSessionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val routineId = intent.getLongExtra("routineId", 0L)
        if (routineId == 0L) return
        when (intent.action) {
            ACTION_COMPLETE_TASK -> RoutineForegroundService.completeTask(context)
            ACTION_ADD_TIME -> RoutineForegroundService.addTime(context, ADD_TIME_MINUTES)
            // The service refuses this while the routine is invincible, so the
            // action is safe to expose even if a stale notification still shows it.
            ACTION_CANCEL -> RoutineForegroundService.cancel(context)
        }
    }

    companion object {
        const val ACTION_COMPLETE_TASK = "com.focusforceplus.app.SESSION_COMPLETE_TASK"
        const val ACTION_ADD_TIME      = "com.focusforceplus.app.SESSION_ADD_TIME"
        const val ACTION_CANCEL        = "com.focusforceplus.app.SESSION_CANCEL"

        /** Matches the "+5 min" label on the notification action. */
        private const val ADD_TIME_MINUTES = 5
    }
}
