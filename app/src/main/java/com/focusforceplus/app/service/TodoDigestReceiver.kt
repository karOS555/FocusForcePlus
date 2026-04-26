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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TodoDigestReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: TodoRepository
    @Inject lateinit var alarmHelper: TodoAlarmHelper
    @Inject lateinit var notifHelper: TodoNotificationHelper

    override fun onReceive(context: Context, intent: Intent) {
        val slot   = intent.getIntExtra("digestSlot", 0)
        val hour   = intent.getIntExtra("digestHour", 8)
        val minute = intent.getIntExtra("digestMinute", 0)

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val now   = System.currentTimeMillis()
                val todos = repository.getAllTodos().first()
                val open  = todos.filter { !it.isCompleted }

                val overdue = open.filter { it.dueDateTime != null && it.dueDateTime < now }
                val undated = open.filter { it.dueDateTime == null }

                // Post one notification per todo
                for (todo in overdue) {
                    notifHelper.showNotification(
                        TodoNotificationHelper.todoDigestNotificationId(todo.id),
                        notifHelper.buildPerTodoReminderNotification(todo.id, todo.title, todo.priority, isOverdue = true),
                    )
                }
                for (todo in undated) {
                    notifHelper.showNotification(
                        TodoNotificationHelper.todoDigestNotificationId(todo.id),
                        notifHelper.buildPerTodoReminderNotification(todo.id, todo.title, todo.priority, isOverdue = false),
                    )
                }

                // Anchor FGS: keeps Medium/High group notifications stable
                val pinnedCount = (overdue + undated).count { it.priority > 0 }
                if (pinnedCount > 0) {
                    context.startForegroundService(
                        TodoReminderForegroundService.startIntent(context, pinnedCount)
                    )
                } else {
                    notifHelper.cancelNotification(TodoNotificationHelper.NOTIFICATION_ID_OVERDUE)
                    runCatching {
                        context.startService(TodoReminderForegroundService.stopIntent(context))
                    }
                }

                // Re-schedule this digest slot for the same time tomorrow.
                alarmHelper.scheduleNextDigestSlotAlarm(slot, hour, minute)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
