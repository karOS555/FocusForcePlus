package com.focusforceplus.app.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** A launchable app as shown in the blocker list. */
data class InstalledApp(
    val packageName: String,
    val appName: String,
)

/**
 * Loads the launchable apps on the device.
 *
 * Visibility comes from the `<queries>` launcher-intent filter in the manifest — NOT
 * from QUERY_ALL_PACKAGES (Golden Rule #6: the narrow alternative suffices, because
 * only apps a user can launch are meaningful blocking targets).
 *
 * The name list is cached in memory; icons are intentionally not cached here (they
 * are loaded lazily per visible row to keep memory flat).
 */
@Singleton
class InstalledAppsHelper @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()
    private var cache: List<InstalledApp>? = null

    /** The device's current default launcher — never a blocking target. */
    fun defaultLauncherPackage(): String? =
        context.packageManager
            .resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY,
            )
            ?.activityInfo?.packageName

    suspend fun getInstalledApps(forceRefresh: Boolean = false): List<InstalledApp> =
        mutex.withLock {
            cache?.takeIf { !forceRefresh }?.let { return it }
            val fresh = loadApps()
            cache = fresh
            fresh
        }

    private suspend fun loadApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val ownPackage = context.packageName
        val launcher = defaultLauncherPackage()

        pm.queryIntentActivities(launcherIntent, 0)
            .asSequence()
            .map { it.activityInfo }
            .filter { it.packageName != ownPackage && it.packageName != launcher }
            .distinctBy { it.packageName }
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    appName = info.loadLabel(pm).toString(),
                )
            }
            .sortedBy { it.appName.lowercase() }
            .toList()
    }

    /** Loads an app icon; null when the package vanished meanwhile. */
    suspend fun loadIcon(packageName: String): Drawable? = withContext(Dispatchers.IO) {
        runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
    }

    /** Resolves a display name even for apps without a launcher entry. */
    suspend fun loadLabel(packageName: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrNull()
    }
}
