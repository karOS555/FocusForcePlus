package com.focusforceplus.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusforceplus.app.ui.alarm.TodoAlarmActivity
import com.focusforceplus.app.util.TodoNotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TodoAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var notifHelper: TodoNotificationHelper

    override fun onReceive(context: Context, intent: Intent) {
        val todoId           = intent.getLongExtra("todoId", 0L).takeIf { it > 0L } ?: return
        val title            = intent.getStringExtra("todoTitle") ?: ""
        val snoozeCount      = intent.getIntExtra("snoozeCount", 0)
        val maxSnooze        = intent.getIntExtra("maxSnoozeCount", 2)
        val priority         = intent.getIntExtra("priority", 1)

        when (priority) {
            0 -> {
                // Low: simple dismissable notification only, no alarm screen
                notifHelper.showNotification(
                    TodoNotificationHelper.todoAlarmNotificationId(todoId),
                    notifHelper.buildLowPriorityNotification(todoId, title),
                )
            }
            else -> {
                // Medium (1) or High (2): launch full-screen alarm activity
                // setAlarmClock() grants a BAL (background activity launch) exemption on all API levels.
                runCatching {
                    context.startActivity(
                        Intent(context, TodoAlarmActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            putExtra("todoId", todoId)
                            putExtra("todoTitle", title)
                            putExtra("snoozeCount", snoozeCount)
                            putExtra("priority", priority)
                        }
                    )
                }
                // Both High (2) and Medium (1) use a foreground service so the
                // alarm notification cannot be swiped away on any Android version.
                context.startForegroundService(
                    TodoAlarmForegroundService.startIntent(
                        context, todoId, title, snoozeCount, maxSnooze, priority,
                    )
                )
            }
        }
    }
}
