package com.focusforceplus.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusforceplus.app.data.repository.FocusRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handles focus notification actions: "End session" from the ongoing notification
 * and "Start now" from a scheduled-session reminder.
 */
@AndroidEntryPoint
class FocusSessionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_END_SESSION = "com.focusforceplus.app.FOCUS_END_SESSION"
        const val ACTION_START_SCHEDULED = "com.focusforceplus.app.FOCUS_START_SCHEDULED"
    }

    @Inject lateinit var focusRepository: FocusRepository
    @Inject lateinit var registry: FocusSessionRegistry

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_END_SESSION -> {
                // The service refuses this for invincible sessions on its own.
                FocusForegroundService.end(context)
            }
            ACTION_START_SCHEDULED -> {
                val sessionId = intent.getLongExtra("sessionId", 0L).takeIf { it > 0L } ?: return
                if (registry.activeSession.value != null) return // one session at a time
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        val session = focusRepository.getSessionById(sessionId).first() ?: return@launch
                        FocusForegroundService.start(
                            context = context,
                            sessionId = session.id,
                            name = session.name,
                            durationMinutes = session.durationMinutes,
                            enableDnd = session.enableDnd,
                            blockNotifications = session.blockNotifications,
                            blocksApps = session.appBlockerEnabled,
                            invincible = session.invincibleMode,
                            type = session.type,
                            blockedGroupsCsv = session.blockedGroupsCsv,
                        )
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
