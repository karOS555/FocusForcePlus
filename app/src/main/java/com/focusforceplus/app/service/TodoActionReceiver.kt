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

/**
 * Buttons on todo notifications, and the swipe.
 *
 * Swiping a medium or high todo away is not an answer, so the notification simply
 * comes back in its current state. It never comes back louder: a swipe re-posts
 * silently, and there is always a button that does deal with the todo.
 */
@AndroidEntryPoint
class TodoActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_DONE = "com.focusforceplus.app.TODO_DONE"
        const val ACTION_SNOOZE = "com.focusforceplus.app.TODO_SNOOZE"
        const val ACTION_DISMISS = "com.focusforceplus.app.TODO_DISMISS"
        const val ACTION_SWIPED = "com.focusforceplus.app.TODO_NOTIF_SWIPED"
        const val EXTRA_MINUTES = "minutes"
    }

    @Inject lateinit var engine: TodoReminderEngine

    override fun onReceive(context: Context, intent: Intent) {
        val todoId = intent.getLongExtra(TodoAlarmScheduler.EXTRA_TODO_ID, 0L).takeIf { it > 0L } ?: return
        val action = intent.action ?: return
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 10)

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                when (action) {
                    ACTION_DONE -> engine.complete(todoId)
                    ACTION_SNOOZE -> engine.snooze(todoId, minutes)
                    ACTION_DISMISS -> engine.dismiss(todoId)
                    ACTION_SWIPED -> engine.sync(todoId)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
