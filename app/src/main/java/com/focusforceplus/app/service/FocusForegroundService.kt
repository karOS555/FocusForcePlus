package com.focusforceplus.app.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.focusforceplus.app.data.db.entity.FocusCompletionEntity
import com.focusforceplus.app.data.repository.FocusRepository
import com.focusforceplus.app.util.FocusNotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns a running focus session end-to-end: the countdown, the ongoing notification,
 * Do Not Disturb, and the [FocusSessionRegistry] state. The service (not the UI)
 * drives the timer so the session survives the app being swiped away, and DND is
 * always restored — in [onDestroy] at the latest.
 *
 * DND mapping: "Enable DND" = priority-only filter; "Block notifications" upgrades
 * it to total silence. Both need the user-granted notification-policy access and
 * degrade gracefully to "no DND" when it is missing.
 */
@AndroidEntryPoint
class FocusForegroundService : Service() {

    @Inject lateinit var notifHelper: FocusNotificationHelper
    @Inject lateinit var registry: FocusSessionRegistry
    @Inject lateinit var focusRepository: FocusRepository
    /** App-lifetime scope: the history write must survive this service stopping. */
    @Inject lateinit var applicationScope: CoroutineScope

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tickerJob: Job? = null

    private var sessionId: Long = 0L
    private var sessionName: String = ""
    private var sessionType: String = "CUSTOM"
    private var totalSeconds: Int = 0
    private var remainingSeconds: Int = 0
    private var invincible: Boolean = false
    private var blocksApps: Boolean = false
    private var blockedGroups: Set<String>? = null
    private var dndApplied: Boolean = false
    private var previousInterruptionFilter: Int = NotificationManager.INTERRUPTION_FILTER_ALL
    private var paused: Boolean = false
    private var historyRecorded: Boolean = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                sessionId = intent.getLongExtra(EXTRA_SESSION_ID, 0L)
                sessionName = intent.getStringExtra(EXTRA_SESSION_NAME) ?: "Focus"
                sessionType = intent.getStringExtra(EXTRA_SESSION_TYPE) ?: "CUSTOM"
                totalSeconds = intent.getIntExtra(EXTRA_DURATION_MINUTES, 25) * 60
                remainingSeconds = totalSeconds
                invincible = intent.getBooleanExtra(EXTRA_INVINCIBLE, false)
                blocksApps = intent.getBooleanExtra(EXTRA_BLOCKS_APPS, false)
                blockedGroups = intent.getStringExtra(EXTRA_BLOCKED_GROUPS)
                    ?.split(',')
                    ?.filter { it.isNotBlank() }
                    ?.toSet()
                    ?.takeIf { it.isNotEmpty() }
                paused = false
                historyRecorded = false

                applyDnd(
                    enableDnd = intent.getBooleanExtra(EXTRA_ENABLE_DND, false),
                    blockNotifications = intent.getBooleanExtra(EXTRA_BLOCK_NOTIFICATIONS, false),
                )
                publishRegistry()
                startForegroundWithNotification()
                startTicker()
            }
            ACTION_PAUSE -> if (!invincible && !paused) {
                paused = true
                tickerJob?.cancel()
                publishRegistry()
                updateNotification()
            }
            ACTION_RESUME -> if (paused) {
                paused = false
                publishRegistry()
                updateNotification()
                startTicker()
            }
            ACTION_END -> {
                // Invincible sessions have no early exit — the timer is the natural end.
                if (!invincible) finishSession(completed = false)
            }
        }
        return START_NOT_STICKY
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (remainingSeconds > 0) {
                delay(1_000L)
                remainingSeconds--
                if (remainingSeconds % 5 == 0 || remainingSeconds <= 10) updateNotification()
                if (remainingSeconds % 15 == 0) publishRegistry()
            }
            finishSession(completed = true)
        }
    }

    private fun publishRegistry() {
        registry.markActive(
            ActiveFocusSession(
                sessionId = sessionId,
                name = sessionName,
                endsAtMillis = System.currentTimeMillis() + remainingSeconds * 1_000L,
                blocksApps = blocksApps,
                invincible = invincible,
                pausedRemainingSeconds = if (paused) remainingSeconds else null,
                totalSeconds = totalSeconds,
                blockedGroups = blockedGroups,
            )
        )
    }

    private fun startForegroundWithNotification() {
        val notification = notifHelper.buildOngoingNotification(
            sessionName, remainingSeconds, paused, invincible,
        )
        // specialUse type exists from API 34; below that the manifest type applies.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                FocusNotificationHelper.NOTIFICATION_ID_FOCUS,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(FocusNotificationHelper.NOTIFICATION_ID_FOCUS, notification)
        }
    }

    private fun updateNotification() {
        notifHelper.showNotification(
            FocusNotificationHelper.NOTIFICATION_ID_FOCUS,
            notifHelper.buildOngoingNotification(sessionName, remainingSeconds, paused, invincible),
        )
    }

    // ── DND ───────────────────────────────────────────────────────────────────

    private fun applyDnd(enableDnd: Boolean, blockNotifications: Boolean) {
        if (!enableDnd && !blockNotifications) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) return // degrade gracefully
        runCatching {
            previousInterruptionFilter = nm.currentInterruptionFilter
            nm.setInterruptionFilter(
                if (blockNotifications) NotificationManager.INTERRUPTION_FILTER_NONE
                else NotificationManager.INTERRUPTION_FILTER_PRIORITY
            )
            dndApplied = true
        }
    }

    private fun restoreDnd() {
        if (!dndApplied) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.isNotificationPolicyAccessGranted) {
            runCatching { nm.setInterruptionFilter(previousInterruptionFilter) }
        }
        dndApplied = false
    }

    // ── Teardown ──────────────────────────────────────────────────────────────

    private fun finishSession(completed: Boolean) {
        tickerJob?.cancel()
        restoreDnd()
        if (completed) {
            notifHelper.showNotification(
                FocusNotificationHelper.NOTIFICATION_ID_FOCUS_DONE,
                notifHelper.buildCompletionNotification(sessionName, totalSeconds / 60),
            )
        }
        recordHistory(completed)
        registry.markIdle(sessionId)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Writes the history row exactly once; skips sub-minute early aborts. */
    private fun recordHistory(completed: Boolean) {
        if (historyRecorded || totalSeconds == 0) return
        historyRecorded = true
        val focusedMinutes = if (completed) totalSeconds / 60
                             else (totalSeconds - remainingSeconds) / 60
        if (!completed && focusedMinutes < 1) return
        val entry = FocusCompletionEntity(
            sessionId = sessionId,
            name = sessionName,
            type = sessionType,
            focusedMinutes = focusedMinutes,
            completedAt = System.currentTimeMillis(),
            endedEarly = !completed,
        )
        applicationScope.launch { focusRepository.recordCompletion(entry) }
    }

    override fun onDestroy() {
        // Safety net: whatever kills the service must never leave DND stuck on.
        tickerJob?.cancel()
        restoreDnd()
        recordHistory(completed = false)
        registry.markIdle(sessionId)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START  = "com.focusforceplus.app.FOCUS_START"
        const val ACTION_PAUSE  = "com.focusforceplus.app.FOCUS_PAUSE"
        const val ACTION_RESUME = "com.focusforceplus.app.FOCUS_RESUME"
        const val ACTION_END    = "com.focusforceplus.app.FOCUS_END"

        private const val EXTRA_SESSION_ID = "sessionId"
        private const val EXTRA_SESSION_NAME = "sessionName"
        private const val EXTRA_SESSION_TYPE = "sessionType"
        private const val EXTRA_DURATION_MINUTES = "durationMinutes"
        private const val EXTRA_ENABLE_DND = "enableDnd"
        private const val EXTRA_BLOCK_NOTIFICATIONS = "blockNotifications"
        private const val EXTRA_BLOCKS_APPS = "blocksApps"
        private const val EXTRA_INVINCIBLE = "invincible"
        private const val EXTRA_BLOCKED_GROUPS = "blockedGroupsCsv"

        fun start(
            context: Context,
            sessionId: Long,
            name: String,
            durationMinutes: Int,
            enableDnd: Boolean,
            blockNotifications: Boolean,
            blocksApps: Boolean,
            invincible: Boolean,
            type: String = "CUSTOM",
            blockedGroupsCsv: String? = null,
        ) {
            context.startForegroundService(
                Intent(context, FocusForegroundService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_SESSION_ID, sessionId)
                    putExtra(EXTRA_SESSION_NAME, name)
                    putExtra(EXTRA_SESSION_TYPE, type)
                    putExtra(EXTRA_DURATION_MINUTES, durationMinutes)
                    putExtra(EXTRA_ENABLE_DND, enableDnd)
                    putExtra(EXTRA_BLOCK_NOTIFICATIONS, blockNotifications)
                    putExtra(EXTRA_BLOCKS_APPS, blocksApps)
                    putExtra(EXTRA_INVINCIBLE, invincible)
                    putExtra(EXTRA_BLOCKED_GROUPS, blockedGroupsCsv)
                }
            )
        }

        fun pause(context: Context) = context.startService(
            Intent(context, FocusForegroundService::class.java).apply { action = ACTION_PAUSE }
        )

        fun resume(context: Context) = context.startService(
            Intent(context, FocusForegroundService::class.java).apply { action = ACTION_RESUME }
        )

        fun end(context: Context) = context.startService(
            Intent(context, FocusForegroundService::class.java).apply { action = ACTION_END }
        )
    }
}
