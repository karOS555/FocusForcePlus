package com.focusforceplus.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.focusforceplus.app.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground service that keeps the process alive while a routine session is running.
 * The UI drives the timer; this service only manages the persistent notification.
 */
@AndroidEntryPoint
class RoutineForegroundService : Service() {

    @Inject lateinit var notificationHelper: NotificationHelper

    private var routineId: Long = 0L
    private var routineName: String = ""

    companion object {
        private const val ACTION_START  = "com.focusforceplus.app.ROUTINE_START"
        private const val ACTION_UPDATE = "com.focusforceplus.app.ROUTINE_UPDATE"
        private const val EXTRA_ROUTINE_ID   = "routineId"
        private const val EXTRA_ROUTINE_NAME = "routineName"
        private const val EXTRA_TASK_NAME    = "taskName"
        private const val EXTRA_REMAINING    = "remainingSeconds"

        fun start(
            context: Context,
            routineId: Long,
            routineName: String,
            taskName: String,
            initialDurationSeconds: Int,
        ) {
            val intent = Intent(context, RoutineForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ROUTINE_ID, routineId)
                putExtra(EXTRA_ROUTINE_NAME, routineName)
                putExtra(EXTRA_TASK_NAME, taskName)
                putExtra(EXTRA_REMAINING, initialDurationSeconds)
            }
            context.startForegroundService(intent)
        }

        fun update(
            context: Context,
            routineId: Long,
            taskName: String,
            remainingSeconds: Int,
        ) {
            val intent = Intent(context, RoutineForegroundService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_ROUTINE_ID, routineId)
                putExtra(EXTRA_TASK_NAME, taskName)
                putExtra(EXTRA_REMAINING, remainingSeconds)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RoutineForegroundService::class.java))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                routineId   = intent.getLongExtra(EXTRA_ROUTINE_ID, 0L)
                routineName = intent.getStringExtra(EXTRA_ROUTINE_NAME) ?: ""
                val taskName = intent.getStringExtra(EXTRA_TASK_NAME) ?: ""
                val remaining = intent.getIntExtra(EXTRA_REMAINING, 0)
                val notification = notificationHelper.buildOngoingNotification(
                    routineId, routineName, taskName, remainingSeconds = remaining,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NotificationHelper.NOTIFICATION_ID_FOREGROUND,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                    )
                } else {
                    startForeground(NotificationHelper.NOTIFICATION_ID_FOREGROUND, notification)
                }
            }
            ACTION_UPDATE -> {
                val id        = intent.getLongExtra(EXTRA_ROUTINE_ID, routineId)
                val taskName  = intent.getStringExtra(EXTRA_TASK_NAME) ?: ""
                val remaining = intent.getIntExtra(EXTRA_REMAINING, -1)
                val notification = notificationHelper.buildOngoingNotification(
                    id, routineName, taskName, remaining,
                )
                notificationHelper.showNotification(
                    NotificationHelper.NOTIFICATION_ID_FOREGROUND,
                    notification,
                )
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
}
