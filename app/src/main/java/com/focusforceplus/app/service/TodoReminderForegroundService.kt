package com.focusforceplus.app.service

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
class TodoReminderForegroundService : Service() {

    @Inject lateinit var notifHelper: TodoNotificationHelper

    companion object {
        const val ACTION_START = "com.focusforceplus.app.REMINDER_FS_START"
        const val ACTION_STOP  = "com.focusforceplus.app.REMINDER_FS_STOP"

        fun startIntent(context: Context, count: Int): Intent =
            Intent(context, TodoReminderForegroundService::class.java).apply {
                action = ACTION_START
                putExtra("count", count)
            }

        fun stopIntent(context: Context): Intent =
            Intent(context, TodoReminderForegroundService::class.java).apply {
                action = ACTION_STOP
            }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val count = intent.getIntExtra("count", 1)
                val notif = notifHelper.buildOverdueGroupSummary(count)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        TodoNotificationHelper.NOTIFICATION_ID_OVERDUE,
                        notif,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                    )
                } else {
                    startForeground(TodoNotificationHelper.NOTIFICATION_ID_OVERDUE, notif)
                }
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
}
