package com.focusforceplus.app.util

import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.focusforceplus.app.R

/**
 * One visual language for every notification this app posts.
 *
 * The app's whole promise is that its reminders are hard to ignore, which only
 * works if they also read as one coherent thing: same accent colour, same small
 * icon, a sub-text naming the module, and — for anything that is actually running
 * right now — a progress timeline instead of a line of text.
 *
 * ## Live Updates / Samsung Now Bar
 *
 * Android 16 can promote ongoing notifications that ask for it into the status bar
 * chip. **Measured on a Galaxy S24+ / One UI 8 (2026-08-07): nothing visible
 * happens there**, even with `POST_PROMOTED_NOTIFICATIONS` force-allowed via adb —
 * Samsung's Now Bar runs off a per-package allowlist of its own and Google's apps,
 * and One UI ships neither the status-bar chip nor a per-app settings screen for
 * it. So promotion is treated strictly as a bonus: the notification itself must
 * carry its full weight (see [asLiveUpdate]), and no user-facing text may promise
 * a Now Bar entry.
 *
 * The requirements for promotion (see
 * developer.android.com/develop/ui/views/notifications/live-update) are:
 * ongoing, a content title, one of the supported styles, no custom RemoteViews,
 * not colorized, not a group summary — plus `POST_PROMOTED_NOTIFICATIONS` and
 * `setRequestPromotedOngoing(true)`. [asLiveUpdate] applies the last two and the
 * status-bar chip text; everything else is the caller's job and is satisfied by
 * the builders in this app.
 *
 * On older Android versions these calls are simply no-ops, and the notification
 * degrades to a normal ongoing one.
 */
object NotificationStyle {

    /** Brand accent used for every notification's icon tint and progress bar. */
    const val ACCENT = 0xFF1d4ed8.toInt()          // Blue700

    /** Progress segment colours — read as "done / now / still ahead". */
    const val SEGMENT_DONE = 0xFF1d4ed8.toInt()    // Blue700
    const val SEGMENT_NOW = 0xFF60a5fa.toInt()     // light blue, the live section
    const val SEGMENT_AHEAD = 0xFF334155.toInt()   // Outline, muted
    const val SEGMENT_OVERTIME = 0xFFfbbf24.toInt() // Warning
    const val POINT_MARK = 0xFF94a3b8.toInt()      // OnSurfaceVar

    // ── Channel groups ────────────────────────────────────────────────────────
    //
    // Groups only organise the system's notification settings screen; the channel
    // ids stay untouched so users keep the per-channel choices they already made.

    const val GROUP_ALARMS = "grp_alarms"
    const val GROUP_LIVE = "grp_live"
    const val GROUP_REMINDERS = "grp_reminders"

    fun createChannelGroups(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Group descriptions need API 28 and add nothing the names don't say.
        manager.createNotificationChannelGroups(
            listOf(
                NotificationChannelGroup(GROUP_ALARMS, "Alarms"),
                NotificationChannelGroup(GROUP_LIVE, "Running sessions"),
                NotificationChannelGroup(GROUP_REMINDERS, "Reminders and status"),
            )
        )
    }

    // ── Shared look ───────────────────────────────────────────────────────────

    /**
     * The house style: brand icon and accent, plus a sub-text naming the module so
     * a glance at a stack of notifications tells the user which part of the app is
     * talking to them.
     */
    fun NotificationCompat.Builder.applyBrand(module: String): NotificationCompat.Builder = this
        .setSmallIcon(R.drawable.ic_notification)
        .setColor(ACCENT)
        .setSubText(module)

    /**
     * Marks a notification as the live view of a session the user started.
     *
     * Two things have to hold, and they are easy to get wrong:
     *
     * 1. **It must be readable on the lock screen even when nothing promotes it.**
     *    Promotion is a bonus (status-bar chip, Now Bar) that the system may refuse,
     *    and several devices do. So this always sets [VISIBILITY_PUBLIC] — a running
     *    routine is exactly the thing you want to see without unlocking, and it
     *    contains nothing private beyond a name the user chose.
     * 2. **It must not be classified as "silent".** Android's
     *    `lock_screen_show_silent_notifications` setting hides silent notifications
     *    from the lock screen entirely, and it is off by default on some phones.
     *    That means the channel must be [NotificationManager.IMPORTANCE_DEFAULT] or
     *    higher with its sound set to null — quiet, but not silent — and the builder
     *    must not call `setSilent(true)`. [liveSessionChannel] builds such a channel.
     *
     * [shortCriticalText] is the status-bar chip text and fits roughly seven
     * characters, so pass "12m" or "2/5", never a sentence.
     */
    fun NotificationCompat.Builder.asLiveUpdate(
        shortCriticalText: String?,
    ): NotificationCompat.Builder {
        setOngoing(true)
        setOnlyAlertOnce(true)
        setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (Build.VERSION.SDK_INT >= 36) {
            setRequestPromotedOngoing(true)
            if (shortCriticalText != null) setShortCriticalText(shortCriticalText)
        }
        return this
    }

    /**
     * A channel for a running session: quiet but not silent, so the notification
     * survives "hide silent notifications on the lock screen".
     */
    fun liveSessionChannel(id: String, name: String, description: String) =
        android.app.NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
            this.description = description
            group = GROUP_LIVE
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

    /** True when the user (and the system) allow promoted "live" notifications. */
    fun canPostLiveUpdates(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return runCatching { manager.canPostPromotedNotifications() }.getOrDefault(false)
    }

    // ── Progress timeline ─────────────────────────────────────────────────────

    /** One section of a timeline: how long it lasts and what state it is in. */
    data class TimelineSegment(val length: Int, val color: Int)

    /**
     * Builds the segmented progress bar used by routines, focus sessions and sleep
     * mode — the "where am I in this thing" visual, in place of a bare percentage.
     * [points] are marker positions (task boundaries, bedtime, wake-up).
     *
     * Returns null below Android 16, where the caller falls back to plain text.
     */
    fun timeline(
        segments: List<TimelineSegment>,
        progress: Int,
        points: List<Int> = emptyList(),
    ): NotificationCompat.Style? {
        if (Build.VERSION.SDK_INT < 36) return null
        if (segments.isEmpty()) return null
        val style = NotificationCompat.ProgressStyle()
            .setStyledByProgress(false)
            .setProgress(progress.coerceAtLeast(0))
            .setProgressSegments(
                segments.map { seg ->
                    NotificationCompat.ProgressStyle.Segment(seg.length.coerceAtLeast(1))
                        .setColor(seg.color)
                }
            )
        if (points.isNotEmpty()) {
            style.setProgressPoints(
                points.map { NotificationCompat.ProgressStyle.Point(it).setColor(POINT_MARK) }
            )
        }
        return style
    }

    /** "1h 05m" / "12m" / "45s" — used for chip text and content lines. */
    fun formatDuration(totalSeconds: Int): String {
        val s = totalSeconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        return when {
            h > 0 -> "%dh %02dm".format(h, m)
            m > 0 -> "%dm".format(m)
            else -> "%ds".format(s % 60)
        }
    }

    /** Chip text: at most a handful of characters, so "1:05" / "12m" / "<1m". */
    fun formatChip(totalSeconds: Int): String {
        val s = totalSeconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        return when {
            h > 0 -> "%d:%02d".format(h, m)
            m > 0 -> "%dm".format(m)
            else -> "<1m"
        }
    }
}
