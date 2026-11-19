package com.focusforceplus.app.service

import com.focusforceplus.app.util.Dnd
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import com.focusforceplus.app.data.db.entity.FocusCompletionEntity
import com.focusforceplus.app.data.repository.FocusRepository
import com.focusforceplus.app.ui.common.AppIcon
import com.focusforceplus.app.util.FocusNotificationHelper
import com.focusforceplus.app.util.focusNotificationIcon
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns a running focus session end-to-end: the countdown, the ongoing notification,
 * Do Not Disturb, and the [FocusSessionRegistry] state. The service (not the UI)
 * drives the timer so the session survives the app being swiped away.
 *
 * Time is kept as an end point, not by counting seconds: a counting loop stalls
 * while the phone sleeps with the screen off, which is how focus is usually spent,
 * and the session then ran long. An exact alarm at the end wakes the phone to
 * finish on time. [FocusSessionStore] keeps a snapshot, so a session survives the
 * process being killed, and [FocusSessionRecovery] puts Do Not Disturb back when it
 * cannot.
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
    @Inject lateinit var store: FocusSessionStore
    @Inject lateinit var recovery: FocusSessionRecovery
    /** App-lifetime scope: the history write must survive this service stopping. */
    @Inject lateinit var applicationScope: CoroutineScope

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tickerJob: Job? = null

    private var sessionId: Long = 0L
    private var sessionName: String = ""
    private var sessionIcon: AppIcon? = null
    private var sessionIconKey: String? = null
    private var sessionType: String = "CUSTOM"
    private var totalSeconds: Int = 0
    /** End of the session on the elapsed-realtime clock, while running. */
    private var endsAtElapsed: Long = 0L
    /** Seconds left, frozen while paused. */
    private var pausedRemaining: Int = 0
    private var invincible: Boolean = false
    private var blocksApps: Boolean = false
    private var blockedGroups: Set<String>? = null
    private var appliedFilter: Int? = null
    private var previousInterruptionFilter: Int = NotificationManager.INTERRUPTION_FILTER_ALL
    private var paused: Boolean = false
    private var historyRecorded: Boolean = false
    private var finished: Boolean = false

    private val active: Boolean get() = totalSeconds > 0 && !finished

    private fun remainingSeconds(): Int =
        if (paused) pausedRemaining
        else ((endsAtElapsed - SystemClock.elapsedRealtime() + 999) / 1_000L).toInt().coerceAtLeast(0)

    private fun endsAtWall(): Long =
        System.currentTimeMillis() + remainingSeconds() * 1_000L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val redelivered = flags and START_FLAG_REDELIVERY != 0
        // A fresh process with a command for a session that was running: Android
        // killed us and brought us back. Continue from the snapshot first.
        if (!active && (redelivered || action != ACTION_START)) {
            if (!restoreFromSnapshot()) return START_NOT_STICKY
            if (action == ACTION_START) return START_REDELIVER_INTENT
        }
        when (action) {
            ACTION_START -> intent?.let { startSession(it) }
            ACTION_PAUSE -> if (!invincible && !paused) {
                pausedRemaining = remainingSeconds()
                paused = true
                tickerJob?.cancel()
                recovery.cancelCheck()
                persist()
                publishRegistry()
                updateNotification()
            }
            ACTION_RESUME -> if (paused) {
                paused = false
                endsAtElapsed = SystemClock.elapsedRealtime() + pausedRemaining * 1_000L
                persist()
                recovery.scheduleCheck(endsAtWall())
                publishRegistry()
                updateNotification()
                startTicker()
            }
            ACTION_END -> {
                // Invincible sessions have no early exit: the timer is the natural end.
                if (!invincible) finishSession(completed = false)
            }
            ACTION_CHECK -> if (active && !paused) tick()
        }
        return if (active) START_REDELIVER_INTENT else START_NOT_STICKY
    }

    private fun startSession(intent: Intent) {
        sessionId = intent.getLongExtra(EXTRA_SESSION_ID, 0L)
        sessionName = intent.getStringExtra(EXTRA_SESSION_NAME) ?: "Focus"
        sessionType = intent.getStringExtra(EXTRA_SESSION_TYPE) ?: "CUSTOM"
        sessionIconKey = intent.getStringExtra(EXTRA_ICON_KEY)
        sessionIcon = focusNotificationIcon(sessionIconKey, sessionType)
        totalSeconds = intent.getIntExtra(EXTRA_DURATION_MINUTES, 25) * 60
        endsAtElapsed = SystemClock.elapsedRealtime() + totalSeconds * 1_000L
        invincible = intent.getBooleanExtra(EXTRA_INVINCIBLE, false)
        blocksApps = intent.getBooleanExtra(EXTRA_BLOCKS_APPS, false)
        blockedGroups = intent.getStringExtra(EXTRA_BLOCKED_GROUPS)
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
        paused = false
        historyRecorded = false
        finished = false

        applyDnd(
            enableDnd = intent.getBooleanExtra(EXTRA_ENABLE_DND, false),
            blockNotifications = intent.getBooleanExtra(EXTRA_BLOCK_NOTIFICATIONS, false),
        )
        persist()
        recovery.scheduleCheck(endsAtWall())
        publishRegistry()
        startForegroundWithNotification()
        startTicker()
    }

    /** Brings a killed session back. False when there is nothing (left) to run. */
    private fun restoreFromSnapshot(): Boolean {
        val snap = store.load()
        if (snap == null) {
            runCatching { startForegroundWithNotification() }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return false
        }
        sessionId = snap.sessionId
        sessionName = snap.name
        sessionType = snap.type
        sessionIconKey = snap.iconKey
        sessionIcon = focusNotificationIcon(snap.iconKey, snap.type)
        totalSeconds = snap.totalSeconds
        invincible = snap.invincible
        blocksApps = snap.blocksApps
        blockedGroups = snap.blockedGroupsCsv?.split(',')?.filter { it.isNotBlank() }?.toSet()?.takeIf { it.isNotEmpty() }
        appliedFilter = snap.appliedFilter
        previousInterruptionFilter = snap.previousFilter
        // A session an older version started kept Do Not Disturb itself: hand it over.
        snap.appliedFilter?.let { applied ->
            if (!Dnd.holds(this, Dnd.FOCUS)) Dnd.acquire(this, Dnd.FOCUS, applied, knownPrevious = snap.previousFilter)
        }
        paused = snap.pausedRemainingSeconds != null
        pausedRemaining = snap.pausedRemainingSeconds ?: 0
        endsAtElapsed = SystemClock.elapsedRealtime() + (snap.endsAtMillis - System.currentTimeMillis())
        historyRecorded = false
        finished = false

        // Started as a foreground service: it has to be one before it may stop. If
        // Android does not allow that here, the session cannot run; clean up instead.
        if (runCatching { startForegroundWithNotification() }.isFailure) {
            finished = true
            applicationScope.launch { recovery.resolveIfOrphaned() }
            stopSelf()
            return false
        }
        if (!paused && remainingSeconds() <= 0) {
            finishSession(completed = true)
            return false
        }
        publishRegistry()
        if (!paused) {
            recovery.scheduleCheck(endsAtWall())
            startTicker()
        }
        return true
    }

    private fun persist() {
        store.save(
            FocusSessionSnapshot(
                sessionId = sessionId,
                name = sessionName,
                type = sessionType,
                iconKey = sessionIconKey,
                totalSeconds = totalSeconds,
                endsAtMillis = endsAtWall(),
                pausedRemainingSeconds = if (paused) pausedRemaining else null,
                invincible = invincible,
                blocksApps = blocksApps,
                blockedGroupsCsv = blockedGroups?.joinToString(","),
                appliedFilter = appliedFilter,
                previousFilter = previousInterruptionFilter,
            )
        )
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            var lastPost = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(1_000L)
                if (tick()) break
                // The countdown itself renders via the system chronometer; the
                // 15-second re-post is what advances the timeline tracker and the
                // Live Update chip.
                if (SystemClock.elapsedRealtime() - lastPost >= 15_000L) {
                    lastPost = SystemClock.elapsedRealtime()
                    publishRegistry()
                    updateNotification()
                }
            }
        }
    }

    /** Finishes the session once its time is up. True when it did. */
    private fun tick(): Boolean {
        if (remainingSeconds() > 0) return false
        finishSession(completed = true)
        return true
    }

    private fun publishRegistry() {
        registry.markActive(
            ActiveFocusSession(
                sessionId = sessionId,
                name = sessionName,
                endsAtMillis = endsAtWall(),
                blocksApps = blocksApps,
                invincible = invincible,
                pausedRemainingSeconds = if (paused) pausedRemaining else null,
                totalSeconds = totalSeconds,
                blockedGroups = blockedGroups,
            )
        )
    }

    private fun startForegroundWithNotification() {
        val notification = notifHelper.buildOngoingNotification(
            sessionId, sessionName.ifEmpty { "Focus" }, remainingSeconds(), paused, invincible, totalSeconds,
            icon = sessionIcon,
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
            notifHelper.buildOngoingNotification(
                sessionId, sessionName, remainingSeconds(), paused, invincible, totalSeconds, icon = sessionIcon,
            ),
        )
    }

    // ── DND ───────────────────────────────────────────────────────────────────

    private fun applyDnd(enableDnd: Boolean, blockNotifications: Boolean) {
        appliedFilter = null
        if (!enableDnd && !blockNotifications) {
            // A session that was cut off (a force stop) may have left its Do Not
            // Disturb behind. This one wants none, and nothing else would ever put
            // it back once this session's snapshot has replaced the old one.
            Dnd.release(this, Dnd.FOCUS)
            return
        }
        val filter = if (blockNotifications) NotificationManager.INTERRUPTION_FILTER_NONE
                     else NotificationManager.INTERRUPTION_FILTER_PRIORITY
        // Without access this degrades gracefully: the session runs without DND.
        if (Dnd.acquire(this, Dnd.FOCUS, filter)) appliedFilter = filter
    }

    /** Hands DND back (see [Dnd]: sleep mode or a routine may still want it). */
    private fun restoreDnd() {
        appliedFilter ?: return
        Dnd.release(this, Dnd.FOCUS)
        appliedFilter = null
    }

    // ── Teardown ──────────────────────────────────────────────────────────────

    private fun finishSession(completed: Boolean) {
        if (finished) return
        finished = true
        tickerJob?.cancel()
        recovery.cancelCheck()
        restoreDnd()
        if (completed) {
            notifHelper.showNotification(
                FocusNotificationHelper.NOTIFICATION_ID_FOCUS_DONE,
                notifHelper.buildCompletionNotification(sessionName, totalSeconds / 60, sessionIcon),
            )
        }
        recordHistory(completed)
        store.clear()
        registry.markIdle(sessionId)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Writes the history row exactly once; skips sub-minute early aborts. */
    private fun recordHistory(completed: Boolean) {
        if (historyRecorded || totalSeconds == 0) return
        historyRecorded = true
        val focusedMinutes = if (completed) totalSeconds / 60
                             else (totalSeconds - remainingSeconds()) / 60
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
        // Stopped while still running (not killed: then this never runs): treat it
        // as ending early, and never leave DND stuck on.
        if (active) {
            finished = true
            tickerJob?.cancel()
            recovery.cancelCheck()
            restoreDnd()
            recordHistory(completed = false)
            store.clear()
            registry.markIdle(sessionId)
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START  = "com.focusforceplus.app.FOCUS_START"
        const val ACTION_PAUSE  = "com.focusforceplus.app.FOCUS_PAUSE"
        const val ACTION_RESUME = "com.focusforceplus.app.FOCUS_RESUME"
        const val ACTION_END    = "com.focusforceplus.app.FOCUS_END"
        /** From the end-of-session alarm: finish now if the time is up. */
        const val ACTION_CHECK  = "com.focusforceplus.app.FOCUS_CHECK"

        private const val EXTRA_SESSION_ID = "sessionId"
        private const val EXTRA_SESSION_NAME = "sessionName"
        private const val EXTRA_SESSION_TYPE = "sessionType"
        private const val EXTRA_ICON_KEY = "iconKey"
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
            iconKey: String? = null,
        ) {
            context.startForegroundService(
                Intent(context, FocusForegroundService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_SESSION_ID, sessionId)
                    putExtra(EXTRA_SESSION_NAME, name)
                    putExtra(EXTRA_SESSION_TYPE, type)
                    putExtra(EXTRA_ICON_KEY, iconKey)
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

        /** Brings back a session after the app was updated or killed. */
        fun restore(context: Context) = context.startForegroundService(
            Intent(context, FocusForegroundService::class.java).apply { action = ACTION_CHECK }
        )

        fun check(context: Context) = context.startService(
            Intent(context, FocusForegroundService::class.java).apply { action = ACTION_CHECK }
        )
    }
}
