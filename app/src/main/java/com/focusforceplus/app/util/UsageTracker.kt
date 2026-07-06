package com.focusforceplus.app.util

import android.app.usage.UsageStatsManager
import android.content.Context
import com.focusforceplus.app.data.repository.BlockerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads today's per-app foreground time via [UsageStatsManager] and mirrors it into
 * the blocked-apps table (`usedTodayMinutes`), which drives daily-limit blocking and
 * the usage numbers shown in the UI.
 *
 * Requires Usage Access (PACKAGE_USAGE_STATS); without it every query returns empty
 * and limits simply never trip — graceful degradation, no errors surfaced here.
 */
@Singleton
class UsageTracker @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val blockerRepository: BlockerRepository,
) {

    /** Foreground minutes per package since local midnight. */
    suspend fun queryUsageMinutesToday(): Map<String, Int> = withContext(Dispatchers.IO) {
        if (!PermissionHelper.hasUsageStatsPermission(context)) return@withContext emptyMap()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        runCatching {
            usm.queryAndAggregateUsageStats(startOfDay, System.currentTimeMillis())
                .mapValues { (_, stats) -> (stats.totalTimeInForeground / 60_000L).toInt() }
        }.getOrElse { emptyMap() }
    }

    /**
     * Refreshes `usedTodayMinutes` for every configured rule. Returns the usage map
     * so callers (e.g. the accessibility service) can decide immediately.
     */
    suspend fun syncBlockedAppUsage(): Map<String, Int> {
        val usage = queryUsageMinutesToday()
        if (usage.isEmpty()) return usage
        blockerRepository.getAllAppsOnce().forEach { app ->
            val minutes = usage[app.packageName] ?: 0
            if (minutes != app.usedTodayMinutes) {
                blockerRepository.updateUsedTimeByPackage(app.packageName, minutes)
            }
        }
        return usage
    }

    /** Total screen time today across all apps, in minutes (home dashboard stat). */
    suspend fun totalScreenTimeMinutesToday(): Int =
        queryUsageMinutesToday().values.sum()
}
