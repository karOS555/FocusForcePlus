package com.focusforceplus.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusforceplus.app.util.AlarmRearmer
import com.focusforceplus.app.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Re-arms all alarms whenever the system may have invalidated them: device boot,
 * app update, and wall-clock/timezone changes (which shift every RTC alarm's
 * target). Re-arming is idempotent, so over-triggering is harmless.
 *
 * Routine sessions: an app update kills the foreground service but the session
 * snapshot survives — poke the service so it restores seamlessly. A reboot
 * invalidates the session; clear the snapshot and say so honestly (compliance:
 * never pretend a lock survived when it didn't).
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var alarmRearmer: AlarmRearmer
    @Inject lateinit var routineSessionStore: RoutineSessionStore
    @Inject lateinit var notificationHelper: NotificationHelper

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED_ACTIONS) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                alarmRearmer.rearmAll()

                when (action) {
                    Intent.ACTION_BOOT_COMPLETED -> {
                        routineSessionStore.load()?.let { interrupted ->
                            routineSessionStore.clear()
                            runCatching {
                                notificationHelper.showNotification(
                                    NotificationHelper.NOTIFICATION_ID_INTERRUPTED,
                                    notificationHelper.buildSessionInterruptedNotification(
                                        interrupted.routineName
                                    ),
                                )
                            }
                        }
                    }
                    Intent.ACTION_MY_PACKAGE_REPLACED -> {
                        if (routineSessionStore.load() != null) {
                            runCatching {
                                context.startForegroundService(
                                    Intent(context, RoutineForegroundService::class.java).apply {
                                        this.action = RoutineForegroundService.ACTION_TASK_END_CHECK
                                    }
                                )
                            }
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
