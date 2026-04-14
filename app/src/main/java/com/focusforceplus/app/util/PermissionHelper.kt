package com.focusforceplus.app.util

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

object PermissionHelper {

    /** True if the app can draw over other apps (SYSTEM_ALERT_WINDOW).
     *  Required for startActivity() to interrupt a foreground app on all Android/OEM versions. */
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Opens the system page where the user can grant "Display over other apps". */
    fun openOverlaySettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** True if the app is allowed to launch full-screen intents (alarm-style overlays). */
    fun canUseFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.canUseFullScreenIntent()
    }

    /** Opens the system page where the user can grant USE_FULL_SCREEN_INTENT. */
    fun openFullScreenIntentSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val intent = Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENTS")
            intent.data  = Uri.parse("package:${context.packageName}")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
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
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data  = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    /** True if POST_NOTIFICATIONS is granted (Android 13+). */
    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
               android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
