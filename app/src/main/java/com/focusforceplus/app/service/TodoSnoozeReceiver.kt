package com.focusforceplus.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusforceplus.app.data.repository.TodoRepository
import com.focusforceplus.app.util.TodoAlarmHelper
import com.focusforceplus.app.util.TodoNotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TodoSnoozeReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: TodoRepository
    @Inject lateinit var alarmHelper: TodoAlarmHelper
    @Inject lateinit var notifHelper: TodoNotificationHelper

    override fun onReceive(context: Context, intent: Intent) {
        val todoId        = intent.getLongExtra("todoId", 0L).takeIf { it > 0L } ?: return
        val snoozeMinutes = intent.getIntExtra("snoozeMinutes", 10)
        val currentCount  = intent.getIntExtra("snoozeCount", 0)

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val todo = repository.getTodoById(todoId) ?: return@launch
                if (todo.snoozeCount >= todo.maxSnoozeCount) return@launch

                val newCount      = todo.snoozeCount + 1
                val triggerMillis = System.currentTimeMillis() + snoozeMinutes * 60_000L

                repository.updateSnoozeCount(todoId, newCount)
                alarmHelper.scheduleTodoSnoozeAlarm(
                    todoId, todo.title, snoozeMinutes, newCount, todo.maxSnoozeCount, todo.priority,
                )

                // Transition FGS to snoozed notification so it stays pinned for Medium/High
                context.startForegroundService(
                    TodoAlarmForegroundService.startSnoozedIntent(
                        context, todoId, todo.title, triggerMillis, newCount, todo.maxSnoozeCount, todo.priority,
                    )
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
