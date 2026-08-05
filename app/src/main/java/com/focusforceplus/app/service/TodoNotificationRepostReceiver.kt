package com.focusforceplus.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusforceplus.app.data.repository.TodoRepository
import com.focusforceplus.app.util.TodoNotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fired as the deleteIntent of persistent todo notifications. Android 14+ lets
 * users swipe foreground-service notifications away, which would silently kill
 * the "can't-miss" contract of medium/high todos — so a swipe simply brings the
 * notification back (high: the alarm itself, medium: the soft still-open nag).
 *
 * This only reacts to an explicit user swipe on our own notification and never
 * touches anything outside the app, so it stays inside the compliance rules
 * (no system-settings interference, notification is always actionable via
 * Done/Snooze/Reschedule).
 */
@AndroidEntryPoint
class TodoNotificationRepostReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SWIPED = "com.focusforceplus.app.TODO_NOTIF_SWIPED"

        const val EXTRA_KIND = "kind"
        const val KIND_ALARM = "alarm"
        const val KIND_SNOOZED = "snoozed"
        const val KIND_STILL_OPEN = "stillOpen"
    }

    @Inject lateinit var repository: TodoRepository
    @Inject lateinit var notifHelper: TodoNotificationHelper

    override fun onReceive(context: Context, intent: Intent) {
        val todoId = intent.getLongExtra("todoId", 0L).takeIf { it > 0L } ?: return
        val kind = intent.getStringExtra(EXTRA_KIND) ?: KIND_ALARM
        val triggerMillis = intent.getLongExtra("triggerMillis", 0L)

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val todo = repository.getTodoById(todoId) ?: return@launch
                if (todo.isCompleted || todo.priority == 0) return@launch

                // Prefer re-pinning through the FGS (keeps the notification anchored);
                // fall back to a plain notify if the OEM refuses the service start.
                val pinned = runCatching {
                    when {
                        todo.priority == 2 && kind == KIND_SNOOZED ->
                            context.startForegroundService(
                                TodoAlarmForegroundService.startSnoozedIntent(
                                    context, todoId, todo.title, triggerMillis,
                                    todo.snoozeCount, todo.maxSnoozeCount, todo.priority,
                                )
                            )
                        todo.priority == 2 ->
                            context.startForegroundService(
                                TodoAlarmForegroundService.startIntent(
                                    context, todoId, todo.title,
                                    todo.snoozeCount, todo.maxSnoozeCount, todo.priority,
                                )
                            )
                        else ->
                            context.startForegroundService(
                                TodoAlarmForegroundService.startDismissedIntent(context, todoId, todo.title)
                            )
                    }
                }.isSuccess

                if (!pinned) {
                    val notification = when {
                        todo.priority == 2 && kind == KIND_SNOOZED ->
                            notifHelper.buildTodoSnoozedNotification(
                                todoId, todo.title, triggerMillis,
                                todo.snoozeCount, todo.maxSnoozeCount, todo.priority,
                            )
                        todo.priority == 2 ->
                            notifHelper.buildTodoAlarmNotification(
                                todoId, todo.title, todo.snoozeCount, todo.maxSnoozeCount, todo.priority,
                            )
                        else ->
                            notifHelper.buildMediumDismissedNotification(todoId, todo.title)
                    }
                    notifHelper.showNotification(
                        TodoNotificationHelper.todoAlarmNotificationId(todoId),
                        notification,
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
