package com.focusforceplus.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusforceplus.app.data.repository.FocusRepository
import com.focusforceplus.app.util.FocusAlarmHelper
import com.focusforceplus.app.util.FocusNotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fires at a session's scheduled time. Deliberately does NOT auto-start the session
 * (surprise-DND would be hostile); it posts a reminder with a "Start now" action and
 * re-arms next week's alarm.
 */
@AndroidEntryPoint
class FocusAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var focusRepository: FocusRepository
    @Inject lateinit var focusAlarmHelper: FocusAlarmHelper
    @Inject lateinit var notifHelper: FocusNotificationHelper

    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getLongExtra("sessionId", 0L).takeIf { it > 0L } ?: return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val session = focusRepository.getSessionById(sessionId).first() ?: return@launch
                if (!session.isActive) return@launch
                notifHelper.showNotification(
                    FocusNotificationHelper.NOTIFICATION_ID_FOCUS_SCHEDULED,
                    notifHelper.buildScheduledSessionNotification(session.id, session.name),
                )
                // Weekly repeat: re-arm all of this session's alarms.
                focusAlarmHelper.cancelSessionAlarms(session.id)
                focusAlarmHelper.scheduleSessionAlarms(session)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
