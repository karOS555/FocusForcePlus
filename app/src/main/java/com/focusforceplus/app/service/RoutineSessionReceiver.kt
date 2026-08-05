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
        }
    }

    companion object {
        const val ACTION_COMPLETE_TASK = "com.focusforceplus.app.SESSION_COMPLETE_TASK"
    }
}
