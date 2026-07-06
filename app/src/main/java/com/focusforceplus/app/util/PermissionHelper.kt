package com.focusforceplus.app.util

import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings

object PermissionHelper {

    /** True if the app can draw over other apps (SYSTEM_ALERT_WINDOW).
     *  Required for startActivity() to interrupt a foreground app on all Android/OEM versions. */
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Opens the system page where the user can grant "Display over other apps". */
    fun openOverlaySettings(context: Context) {
        startSettingsSafely(
            context,
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            ),
        )
    }

    /** True if the app is allowed to launch full-screen intents (alarm-style overlays). */
    fun canUseFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.canUseFullScreenIntent()
    }

    /** Opens the system page where the user can grant USE_FULL_SCREEN_INTENT.
     *  Not every device/OEM ships this dedicated screen, so we fall back gracefully. */
    fun openFullScreenIntentSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startSettingsSafely(
                context,
                Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENTS")
                    .setData(Uri.parse("package:${context.packageName}")),
                Intent("android.settings.APP_NOTIFICATION_SETTINGS")
                    .putExtra("android.provider.extra.APP_PACKAGE", context.packageName),
            )
        }
    }

    /** True if the app can schedule exact alarms (needed for the 15-min pre-alarm).
     *  Always true on Android <12. On Android 13+, USE_EXACT_ALARM auto-grants this.
     *  On Android 12, the user must grant SCHEDULE_EXACT_ALARM manually. */
    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    /** Opens the system page where the user can grant SCHEDULE_EXACT_ALARM (Android 12 only). */
    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startSettingsSafely(
                context,
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(Uri.parse("package:${context.packageName}")),
            )
        }
    }

    /** True if POST_NOTIFICATIONS is granted (Android 13+). */
    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
               android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    // ── App blocker permissions ───────────────────────────────────────────────

    /** True if the app-blocker AccessibilityService is enabled in system settings. */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(
            context,
            "com.focusforceplus.app.service.AppBlockerAccessibilityService",
        ).flattenToString()
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any {
            it.equals(expected, ignoreCase = true) ||
                ComponentName.unflattenFromString(it)?.flattenToString()
                    .equals(expected, ignoreCase = true)
        }
    }

    /** Opens the system accessibility settings (the service must be enabled there).
     *  Only call this AFTER the in-app prominent disclosure has been confirmed. */
    fun openAccessibilitySettings(context: Context) {
        startSettingsSafely(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    /** Opens this app's Android app-info page (where "Allow restricted settings" lives). */
    fun openAppDetailsSettings(context: Context) {
        startSettingsSafely(context, appDetailsIntent(context))
    }

    /** True if Usage Access (PACKAGE_USAGE_STATS) has been granted. */
    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Opens the system Usage Access settings.
     *  Only call this AFTER the in-app prominent disclosure has been confirmed. */
    fun openUsageAccessSettings(context: Context) {
        startSettingsSafely(
            context,
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}")),
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
        )
    }

    // ── Focus mode (Do Not Disturb) ───────────────────────────────────────────

    /** True if the app may toggle Do Not Disturb. */
    fun hasDndAccess(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.isNotificationPolicyAccessGranted
    }

    /** Opens the system page where the user can grant DND access. */
    fun openDndAccessSettings(context: Context) {
        startSettingsSafely(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
    }

    // ── Battery optimization ──────────────────────────────────────────────────

    /** True when the app is exempt from battery optimization (reminders fire reliably). */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Opens the battery optimization list — the user picks FocusForce+ manually.
     *  We deliberately use the non-prompting list intent: REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
     *  is restricted and per compliance guide (3.5) not to be forced. */
    fun openBatteryOptimizationSettings(context: Context) {
        startSettingsSafely(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    // ── Internals ──────────────────────────────────────────────────────────────

    private fun appDetailsIntent(context: Context) = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}"),
    )

    /**
     * Tries each settings intent in turn, always falling back to the app-info page,
     * so a device that doesn't ship a particular settings screen never crashes the
     * app. Every candidate gets NEW_TASK since these are launched from non-activity
     * contexts too.
     */
    private fun startSettingsSafely(context: Context, vararg candidates: Intent) {
        val all = candidates.toList() + appDetailsIntent(context)
        for (intent in all) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
                // try the next candidate
            } catch (_: Exception) {
                // some OEMs throw SecurityException etc. — try the next candidate
            }
        }
    }
}
