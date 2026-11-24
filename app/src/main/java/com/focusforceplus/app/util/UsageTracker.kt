package com.focusforceplus.app.util

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.focusforceplus.app.data.repository.BlockerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/** Screen time for the statistics: per day, and today's apps. */
data class ScreenTime(
    /** Minutes per day, oldest first, today last. */
    val perDayMinutes: List<Int>,
    /** Today's apps by package, most used first. */
    val todayByApp: List<Pair<String, Int>>,
)

/**
 * Reads foreground time per app via [UsageStatsManager] and mirrors today's into
 * the blocked-apps table (`usedTodayMinutes`), which drives daily-limit blocking and
 * the usage numbers shown in the UI.
 *
 * Times come from Android's usage events, which are exact. The aggregated daily
 * buckets are not: a bucket can start before midnight and carry last evening into
 * today. The buckets are only a fallback for devices that return no events.
 *
 * Requires Usage Access (PACKAGE_USAGE_STATS); without it every query returns empty
 * and limits simply never trip. Nothing is stored beyond the per-rule minutes.
 */
@Singleton
class UsageTracker @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val blockerRepository: BlockerRepository,
    private val settings: com.focusforceplus.app.data.repository.SettingsRepository,
) {

    /** Foreground minutes per package since local midnight. */
    suspend fun queryUsageMinutesToday(): Map<String, Int> = withContext(Dispatchers.IO) {
        if (!PermissionHelper.hasUsageStatsPermission(context)) return@withContext emptyMap()
        val start = startOfDay(0)
        val byEvents = foregroundByDay(listOf(start), System.currentTimeMillis())?.first()
        if (byEvents != null) {
            byEvents.mapValues { (_, millis) -> (millis / 60_000L).toInt() }
        } else {
            aggregatedMinutesSince(start)
        }
    }

    /**
     * Refreshes `usedTodayMinutes` for every configured rule. Returns the usage map
     * so callers (e.g. the accessibility service) can decide immediately.
     */
    suspend fun syncBlockedAppUsage(): Map<String, Int> {
        val usage = queryUsageMinutesToday()
        // A short-video feed in a block is no app Android keeps time for. Its
        // minutes are the ones the short-video service counted in the player.
        val feeds = settings.shortVideoUsage.first().on(java.time.LocalDate.now().toEpochDay())
        blockerRepository.getAllAppsOnce().forEach { app ->
            val feed = com.focusforceplus.app.compliance.ShortFeed.fromRuleKey(app.packageName)
            if (feed == null && usage.isEmpty()) return@forEach
            val minutes = if (feed != null) feeds.minutes(feed) else usage[app.packageName] ?: 0
            if (minutes != app.usedTodayMinutes) {
                blockerRepository.updateUsedTimeByPackage(app.packageName, minutes)
            }
        }
        return usage
    }

    /** Total screen time today across all apps, in minutes (home dashboard). */
    suspend fun totalScreenTimeMinutesToday(): Int = screenTime(days = 1)?.perDayMinutes?.lastOrNull() ?: 0

    /**
     * Screen time for the last [days] days including today, or null without Usage
     * Access. The home screen and the system UI are left out: time spent on them
     * is time between apps, not in one.
     */
    suspend fun screenTime(days: Int): ScreenTime? = withContext(Dispatchers.IO) {
        if (!PermissionHelper.hasUsageStatsPermission(context)) return@withContext null
        val starts = (days - 1 downTo 0).map { startOfDay(it) }
        val perDay = foregroundByDay(starts, System.currentTimeMillis())
            ?: listOf(aggregatedMinutesSince(starts.last()).mapValues { (_, m) -> m * 60_000L })
                .let { today -> List(days - 1) { emptyMap<String, Long>() } + today }
        val ignored = ignoredPackages()
        ScreenTime(
            perDayMinutes = perDay.map { day ->
                (day.filterKeys { it !in ignored }.values.sum() / 60_000L).toInt()
            },
            todayByApp = perDay.last()
                .filterKeys { it !in ignored }
                .map { (pkg, millis) -> pkg to (millis / 60_000L).toInt() }
                .filter { it.second > 0 }
                .sortedByDescending { it.second },
        )
    }

    /**
     * Foreground milliseconds per package for each day starting at [dayStarts]
     * (oldest first; the last day runs until [now]). Null when the device returns
     * no events at all, so callers can fall back to the buckets.
     */
    private fun foregroundByDay(dayStarts: List<Long>, now: Long): List<Map<String, Long>>? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = runCatching { usm.queryEvents(dayStarts.first(), now) }.getOrNull() ?: return null
        val days = List(dayStarts.size) { HashMap<String, Long>() }

        fun add(pkg: String, from: Long, to: Long) {
            for (i in dayStarts.indices) {
                val dayEnd = if (i + 1 < dayStarts.size) dayStarts[i + 1] else now
                val a = maxOf(from, dayStarts[i])
                val b = minOf(to, dayEnd)
                if (b > a) days[i].merge(pkg, b - a, Long::plus)
            }
        }

        val openSince = HashMap<String, Long>()
        val seen = HashSet<String>()
        var any = false
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            any = true
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                EVENT_RESUMED -> {
                    openSince.putIfAbsent(pkg, event.timeStamp)
                    seen += pkg
                }
                EVENT_PAUSED -> {
                    // A pause with nothing open means the app was already in front
                    // when the window started, but only the first time we see it.
                    val from = openSince.remove(pkg) ?: if (pkg !in seen) dayStarts.first() else null
                    seen += pkg
                    if (from != null) add(pkg, from, event.timeStamp)
                }
                EVENT_SCREEN_OFF, EVENT_SHUTDOWN -> {
                    openSince.forEach { (p, from) -> add(p, from, event.timeStamp) }
                    openSince.clear()
                }
            }
        }
        if (!any) return null
        openSince.forEach { (p, from) -> add(p, from, now) }
        return days
    }

    private fun aggregatedMinutesSince(start: Long): Map<String, Int> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        return runCatching {
            usm.queryAndAggregateUsageStats(start, System.currentTimeMillis())
                .mapValues { (_, stats) -> (stats.totalTimeInForeground / 60_000L).toInt() }
        }.getOrElse { emptyMap() }
    }

    /** The default home screen and the system UI. */
    private fun ignoredPackages(): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launcher = runCatching {
            context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        }.getOrNull()
        return setOfNotNull(launcher, "com.android.systemui")
    }

    private fun startOfDay(daysAgo: Int): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -daysAgo)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private companion object {
        // UsageEvents.Event constants, by value, so they also read on API 26-28.
        const val EVENT_RESUMED = 1      // ACTIVITY_RESUMED, formerly MOVE_TO_FOREGROUND
        const val EVENT_PAUSED = 2       // ACTIVITY_PAUSED, formerly MOVE_TO_BACKGROUND
        const val EVENT_SCREEN_OFF = 16  // SCREEN_NON_INTERACTIVE (API 28+)
        const val EVENT_SHUTDOWN = 26    // DEVICE_SHUTDOWN (API 29+)
    }
}
