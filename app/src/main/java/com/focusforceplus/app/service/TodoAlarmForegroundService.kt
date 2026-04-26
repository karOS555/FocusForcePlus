package com.focusforceplus.app.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.focusforceplus.app.util.TodoNotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TodoAlarmForegroundService : Service() {

    @Inject lateinit var notifHelper: TodoNotificationHelper

    companion object {
        const val ACTION_START           = "com.focusforceplus.app.TODO_ALARM_FS_START"
        const val ACTION_START_SNOOZED   = "com.focusforceplus.app.TODO_ALARM_FS_START_SNOOZED"
        const val ACTION_START_DISMISSED = "com.focusforceplus.app.TODO_ALARM_FS_START_DISMISSED"
        const val ACTION_STOP            = "com.focusforceplus.app.TODO_ALARM_FS_STOP"

        fun startIntent(
            context: Context,
            todoId: Long,
            title: String,
            snoozeCount: Int,
            maxSnoozeCount: Int,
            priority: Int = 2,
        ): Intent = Intent(context, TodoAlarmForegroundService::class.java).apply {
            action = ACTION_START
            putExtra("todoId", todoId)
            putExtra("todoTitle", title)
            putExtra("snoozeCount", snoozeCount)
            putExtra("maxSnoozeCount", maxSnoozeCount)
            putExtra("priority", priority)
        }

        fun startSnoozedIntent(
            context: Context,
            todoId: Long,
            title: String,
            triggerMillis: Long,
            snoozeCount: Int,
            maxSnoozeCount: Int,
            priority: Int,
        ): Intent = Intent(context, TodoAlarmForegroundService::class.java).apply {
            action = ACTION_START_SNOOZED
            putExtra("todoId", todoId)
            putExtra("todoTitle", title)
            putExtra("triggerMillis", triggerMillis)
            putExtra("snoozeCount", snoozeCount)
            putExtra("maxSnoozeCount", maxSnoozeCount)
            putExtra("priority", priority)
        }

        fun startDismissedIntent(
            context: Context,
            todoId: Long,
            title: String,
        ): Intent = Intent(context, TodoAlarmForegroundService::class.java).apply {
            action = ACTION_START_DISMISSED
            putExtra("todoId", todoId)
            putExtra("todoTitle", title)
        }

        fun stopIntent(context: Context): Intent =
            Intent(context, TodoAlarmForegroundService::class.java).apply {
                action = ACTION_STOP
            }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val todoId         = intent.getLongExtra("todoId", 0L)
                val title          = intent.getStringExtra("todoTitle") ?: ""
                val snoozeCount    = intent.getIntExtra("snoozeCount", 0)
                val maxSnoozeCount = intent.getIntExtra("maxSnoozeCount", 2)
                val priority       = intent.getIntExtra("priority", 2)
                pin(
                    TodoNotificationHelper.todoAlarmNotificationId(todoId),
                    notifHelper.buildTodoAlarmNotification(todoId, title, snoozeCount, maxSnoozeCount, priority),
                )
            }
            ACTION_START_SNOOZED -> {
                val todoId         = intent.getLongExtra("todoId", 0L)
                val title          = intent.getStringExtra("todoTitle") ?: ""
                val triggerMillis  = intent.getLongExtra("triggerMillis", 0L)
                val snoozeCount    = intent.getIntExtra("snoozeCount", 0)
                val maxSnoozeCount = intent.getIntExtra("maxSnoozeCount", 2)
                val priority       = intent.getIntExtra("priority", 1)
                pin(
                    TodoNotificationHelper.todoAlarmNotificationId(todoId),
                    notifHelper.buildTodoSnoozedNotification(todoId, title, triggerMillis, snoozeCount, maxSnoozeCount, priority),
                )
            }
            ACTION_START_DISMISSED -> {
                val todoId = intent.getLongExtra("todoId", 0L)
                val title  = intent.getStringExtra("todoTitle") ?: ""
                pin(
                    TodoNotificationHelper.todoAlarmNotificationId(todoId),
                    notifHelper.buildMediumDismissedNotification(todoId, title),
                )
            }
            ACTION_STOP -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun pin(notifId: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(notifId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(notifId, notification)
        }
    }
}
