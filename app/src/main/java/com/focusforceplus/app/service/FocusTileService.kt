package com.focusforceplus.app.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.focusforceplus.app.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Quick-settings tile: one tap starts a 25-minute quick focus session (never
 * invincible); while a session runs, tapping opens the app instead. The tile tap
 * counts as user interaction, so starting the foreground service is exempt from
 * background-start restrictions.
 */
@AndroidEntryPoint
class FocusTileService : TileService() {

    @Inject lateinit var registry: FocusSessionRegistry

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        if (registry.activeSession.value != null) {
            openApp()
            return
        }
        val startQuickFocus = Runnable {
            FocusForegroundService.start(
                context = this,
                sessionId = 0L,
                name = "Quick focus",
                durationMinutes = 25,
                enableDnd = true,
                blockNotifications = false,
                blocksApps = true,
                invincible = false,
            )
            refreshTile()
        }
        if (isLocked) unlockAndRun(startQuickFocus) else startQuickFocus.run()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated") // Intent overload is the only option below API 34
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val active = registry.activeSession.value
        tile.state = if (active != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (active != null) "Focusing" else "Quick focus"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                active != null -> active.name
                else -> "25 min"
            }
        }
        tile.updateTile()
    }
}
