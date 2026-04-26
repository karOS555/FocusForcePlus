package com.focusforceplus.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusforceplus.app.data.repository.TodoRepository
import com.focusforceplus.app.util.TodoAlarmHelper
import com.focusforceplus.app.util.TodoNotificationHelper
import com.focusforceplus.app.util.nextOccurrenceMillis
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TodoDismissReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_DONE    = "com.focusforceplus.app.TODO_DONE"
        const val ACTION_DISMISS = "com.focusforceplus.app.TODO_DISMISS"
    }

    @Inject lateinit var repository: TodoRepository
    @Inject lateinit var alarmHelper: TodoAlarmHelper
    @Inject lateinit var notifHelper: TodoNotificationHelper

    override fun onReceive(context: Context, intent: Intent) {
        val todoId = intent.getLongExtra("todoId", 0L).takeIf { it > 0L } ?: return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_DONE -> {
                        val todo = repository.getTodoById(todoId)
                        repository.markCompleted(todoId, System.currentTimeMillis())
                        alarmHelper.cancelTodoAlarm(todoId)
                        alarmHelper.cancelTodoSnoozeAlarm(todoId)
                        runCatching {
                            context.startService(TodoAlarmForegroundService.stopIntent(context))
                        }
                        if (todo != null && todo.isRecurring && todo.recurringPattern != null && todo.dueDateTime != null) {
                            val nextDue = nextOccurrenceMillis(todo.dueDateTime, todo.recurringPattern)
                            val next = todo.copy(
                                id              = 0L,
                                dueDateTime     = nextDue,
                                isCompleted     = false,
                                completedAt     = null,
                                snoozeCount     = 0,
                                rescheduleCount = 0,
                                postponedTo     = null,
                            )
                            val newId = repository.insertTodo(next)
                            alarmHelper.scheduleTodoAlarm(next.copy(id = newId))
                        }
                    }
                    ACTION_DISMISS -> {
                        alarmHelper.cancelTodoSnoozeAlarm(todoId)
                    }
                }
                notifHelper.cancelNotification(TodoNotificationHelper.todoAlarmNotificationId(todoId))
                notifHelper.cancelNotification(TodoNotificationHelper.todoDigestNotificationId(todoId))
            } finally {
                pendingResult.finish()
            }
        }
    }
}
