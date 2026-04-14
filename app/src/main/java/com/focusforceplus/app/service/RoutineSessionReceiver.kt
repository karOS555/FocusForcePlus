package com.focusforceplus.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusforceplus.app.util.RoutineSessionAction
import com.focusforceplus.app.util.RoutineSessionBus
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Handles notification button actions for the active-routine foreground notification.
 * Forwards the action to [RoutineSessionBus] so the running ViewModel can react.
 */
@AndroidEntryPoint
class RoutineSessionReceiver : BroadcastReceiver() {

    @Inject lateinit var sessionBus: RoutineSessionBus

    override fun onReceive(context: Context, intent: Intent) {
        val routineId = intent.getLongExtra("routineId", 0L)
        if (routineId == 0L) return
        when (intent.action) {
            ACTION_COMPLETE_TASK -> sessionBus.send(RoutineSessionAction.CompleteTask(routineId))
        }
    }

    companion object {
        const val ACTION_COMPLETE_TASK = "com.focusforceplus.app.SESSION_COMPLETE_TASK"
    }
}
