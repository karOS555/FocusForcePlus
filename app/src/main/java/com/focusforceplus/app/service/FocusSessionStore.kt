package com.focusforceplus.app.service

import com.focusforceplus.app.util.Dnd
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.edit
import com.focusforceplus.app.data.db.entity.FocusCompletionEntity
import com.focusforceplus.app.data.repository.FocusRepository
import com.focusforceplus.app.util.FocusNotificationHelper
import com.focusforceplus.app.util.focusNotificationIcon
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** What is needed to bring a running focus session back, or to clean up after it. */
data class FocusSessionSnapshot(
    val sessionId: Long,
    val name: String,
    val type: String,
    val iconKey: String?,
    val totalSeconds: Int,
    /** Wall-clock end while running; not used while paused. */
    val endsAtMillis: Long,
    /** Seconds left while paused; null while running. */
    val pausedRemainingSeconds: Int?,
    val invincible: Boolean,
    val blocksApps: Boolean,
    val blockedGroupsCsv: String?,
    /** The Do Not Disturb filter the session switched to; null when it left DND alone. */
    val appliedFilter: Int?,
    /** The filter that was on before, to go back to. */
    val previousFilter: Int,
) {
    fun remainingSeconds(now: Long): Int =
        pausedRemainingSeconds ?: ((endsAtMillis - now) / 1_000L).toInt().coerceAtLeast(0)
}

/**
 * Kill insurance for focus sessions.
 *
 * The session lives in [FocusForegroundService], so when Android ends the process
 * (some phones do that at night even to foreground services, an update does it
 * too) the service's own teardown never runs. Without this, Do Not Disturb stayed
 * on for good and the session vanished without a trace. The service writes a
 * snapshot on start, pause and resume and clears it when the session ends.
 */
@Singleton
class FocusSessionStore @Inject constructor(
    @param:ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("focus_session_store", Context.MODE_PRIVATE)

    /** Written synchronously: the point is to survive a kill that may come any moment. */
    fun save(s: FocusSessionSnapshot) {
        prefs.edit(commit = true) {
            putLong(KEY_ID, s.sessionId)
            putString(KEY_NAME, s.name)
            putString(KEY_TYPE, s.type)
            putString(KEY_ICON, s.iconKey)
            putInt(KEY_TOTAL, s.totalSeconds)
            putLong(KEY_ENDS_AT, s.endsAtMillis)
            putInt(KEY_PAUSED_LEFT, s.pausedRemainingSeconds ?: -1)
            putBoolean(KEY_INVINCIBLE, s.invincible)
            putBoolean(KEY_BLOCKS, s.blocksApps)
            putString(KEY_GROUPS, s.blockedGroupsCsv)
            putInt(KEY_APPLIED, s.appliedFilter ?: -1)
            putInt(KEY_PREVIOUS, s.previousFilter)
            putBoolean(KEY_PRESENT, true)
        }
    }

    fun load(): FocusSessionSnapshot? {
        if (!prefs.getBoolean(KEY_PRESENT, false)) return null
        return FocusSessionSnapshot(
            sessionId = prefs.getLong(KEY_ID, 0L),
            name = prefs.getString(KEY_NAME, null) ?: "Focus",
            type = prefs.getString(KEY_TYPE, null) ?: "CUSTOM",
            iconKey = prefs.getString(KEY_ICON, null),
            totalSeconds = prefs.getInt(KEY_TOTAL, 0),
            endsAtMillis = prefs.getLong(KEY_ENDS_AT, 0L),
            pausedRemainingSeconds = prefs.getInt(KEY_PAUSED_LEFT, -1).takeIf { it >= 0 },
            invincible = prefs.getBoolean(KEY_INVINCIBLE, false),
            blocksApps = prefs.getBoolean(KEY_BLOCKS, false),
            blockedGroupsCsv = prefs.getString(KEY_GROUPS, null),
            appliedFilter = prefs.getInt(KEY_APPLIED, -1).takeIf { it >= 0 },
            previousFilter = prefs.getInt(KEY_PREVIOUS, NotificationManager.INTERRUPTION_FILTER_ALL),
        )
    }

    fun clear() {
        prefs.edit(commit = true) { clear() }
    }

    private companion object {
        const val KEY_PRESENT = "present"
        const val KEY_ID = "sessionId"
        const val KEY_NAME = "name"
        const val KEY_TYPE = "type"
        const val KEY_ICON = "iconKey"
        const val KEY_TOTAL = "totalSeconds"
        const val KEY_ENDS_AT = "endsAt"
        const val KEY_PAUSED_LEFT = "pausedLeft"
        const val KEY_INVINCIBLE = "invincible"
        const val KEY_BLOCKS = "blocksApps"
        const val KEY_GROUPS = "blockedGroups"
        const val KEY_APPLIED = "appliedFilter"
        const val KEY_PREVIOUS = "previousFilter"
    }
}

/**
 * Cleans up after a focus session whose process died: puts Do Not Disturb back
 * (only if nobody changed it since), records the session in the history, and
 * forgets it. Runs from a safety alarm at the session's end, a little after the
 * app starts, and after a reboot. Does nothing while the session is alive.
 */
@Singleton
class FocusSessionRecovery @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val store: FocusSessionStore,
    private val registry: FocusSessionRegistry,
    private val focusRepository: FocusRepository,
    private val notifications: FocusNotificationHelper,
) {
    private val mutex = Mutex()

    suspend fun resolveIfOrphaned() = mutex.withLock {
        val snap = store.load() ?: return@withLock
        if (registry.activeSession.value != null) return@withLock

        snap.appliedFilter?.let { applied ->
            Dnd.release(context, Dnd.FOCUS, legacyApplied = applied, legacyPrevious = snap.previousFilter)
        }

        val now = System.currentTimeMillis()
        val ranOut = snap.pausedRemainingSeconds == null && now >= snap.endsAtMillis
        val focusedMinutes = if (ranOut) snap.totalSeconds / 60
                             else (snap.totalSeconds - snap.remainingSeconds(now)) / 60
        if (snap.totalSeconds > 0 && focusedMinutes >= 1) {
            focusRepository.recordCompletion(
                FocusCompletionEntity(
                    sessionId = snap.sessionId,
                    name = snap.name,
                    type = snap.type,
                    focusedMinutes = focusedMinutes,
                    completedAt = if (ranOut) snap.endsAtMillis else now,
                    endedEarly = !ranOut,
                )
            )
        }
        if (ranOut) {
            runCatching {
                notifications.showNotification(
                    FocusNotificationHelper.NOTIFICATION_ID_FOCUS_DONE,
                    notifications.buildCompletionNotification(
                        snap.name, snap.totalSeconds / 60, focusNotificationIcon(snap.iconKey, snap.type),
                    ),
                )
            }
        }
        runCatching { notifications.cancelNotification(FocusNotificationHelper.NOTIFICATION_ID_FOCUS) }
        cancelCheck()
        store.clear()
    }

    /**
     * A check right after the session's end. With the screen off the phone sleeps
     * and the service's own countdown with it, so this alarm is what ends a session
     * on time; and when the service is gone, it is what cleans up after it.
     */
    fun scheduleCheck(endsAtMillis: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = endsAtMillis + CHECK_DELAY_MILLIS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, checkIntent())
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, checkIntent())
        }
    }

    fun cancelCheck() {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(checkIntent())
    }

    private fun checkIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, 1,
        Intent(context, FocusSessionReceiver::class.java).setAction(FocusSessionReceiver.ACTION_SAFETY_CHECK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        /** Just past the end, so the session is certain to see its time as up. */
        const val CHECK_DELAY_MILLIS = 1_000L
    }
}
