package com.focusforceplus.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusforceplus.app.util.TodoAlarmScheduler
import com.focusforceplus.app.util.TodoReminderEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A todo's wake-up fired. What that means is decided by the engine, not here. */
@AndroidEntryPoint
class TodoAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var engine: TodoReminderEngine

    override fun onReceive(context: Context, intent: Intent) {
        val todoId = intent.getLongExtra(TodoAlarmScheduler.EXTRA_TODO_ID, 0L).takeIf { it > 0L } ?: return
        val activation = intent.getBooleanExtra(TodoAlarmScheduler.EXTRA_ACTIVATION, false)

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            try {
                engine.onWake(todoId, activation)
            } finally {
                pending.finish()
            }
        }
    }
}
