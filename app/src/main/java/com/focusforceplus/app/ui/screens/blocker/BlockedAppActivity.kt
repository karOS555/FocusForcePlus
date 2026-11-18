package com.focusforceplus.app.ui.screens.blocker

import com.focusforceplus.app.ui.theme.ffBackdrop
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.focusforceplus.app.compliance.BlockReason
import com.focusforceplus.app.ui.theme.FocusForceTheme
import com.focusforceplus.app.util.InstalledAppsHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Full-screen cover shown when a blocked app comes to the foreground.
 * Back is redirected to the home screen — returning to the blocked app would only
 * re-trigger this screen in a loop. The user is never trapped: Home always works
 * and this activity is not shown in recents.
 */
@AndroidEntryPoint
class BlockedAppActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PACKAGE = "packageName"
        const val EXTRA_APP_NAME = "appName"
        const val EXTRA_REASON = "reason"
        const val EXTRA_SESSION_INVINCIBLE = "sessionInvincible"
        const val EXTRA_GROUP_NAME = "groupName"
        const val EXTRA_GROUP_LIMIT = "groupLimit"
        const val EXTRA_GROUP_USED = "groupUsed"
    }

    private val viewModel: BlockedAppViewModel by viewModels()

    @Inject lateinit var installedAppsHelper: InstalledAppsHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this) { goHome() }

        setContent {
            FocusForceTheme {
                val state by viewModel.uiState.collectAsState()
                val icon by produceState<Drawable?>(initialValue = null, state.packageName) {
                    value = state.packageName
                        .takeIf { it.isNotBlank() }
                        ?.let { installedAppsHelper.loadIcon(it) }
                }
                BlockedAppScreen(
                    state = state,
                    icon = icon,
                    onGoHome = ::goHome,
                    onUseException = { viewModel.useException { finish() } },
                )
            }
        }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        finish()
    }
}

@Composable
private fun BlockedAppScreen(
    state: BlockedAppUiState,
    icon: Drawable?,
    onGoHome: () -> Unit,
    onUseException: () -> Unit,
) {
    val colors = com.focusforceplus.app.ui.theme.FfTheme.colors
    var asking by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .ffBackdrop()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text("Blocked", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
        ) {
            val bitmap = icon?.toBitmap(96, 96)
            if (bitmap != null) {
                Icon(
                    painter = BitmapPainter(bitmap.asImageBitmap()),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(56.dp),
                )
            } else {
                com.focusforceplus.app.ui.components.IconCircle(icon = Icons.Filled.Block, size = 56.dp)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = state.appName.ifBlank { state.packageName },
                style = MaterialTheme.typography.headlineMedium,
                color = colors.text,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = reasonText(state),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary,
            )
        }
        com.focusforceplus.app.ui.components.PrimaryButton(
            text = "Back to the home screen",
            onClick = onGoHome,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        )
        if (!state.isLoading && state.canUseException) {
            Spacer(Modifier.height(10.dp))
            com.focusforceplus.app.ui.components.OutlineButton(
                text = "5 more minutes \u00B7 ${state.exceptionsLeft} left today",
                onClick = { if (state.exceptionGuard.isNone) onUseException() else asking = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (asking) {
        com.focusforceplus.app.ui.guards.GuardDialog(
            guard = state.exceptionGuard,
            copy = com.focusforceplus.app.ui.guards.GuardCopy.BLOCKER_EXCEPTION,
            subject = state.appName,
            onPassed = {
                asking = false
                onUseException()
            },
            onDismiss = { asking = false },
        )
    }
}

private fun reasonText(state: BlockedAppUiState): String = when (state.reason) {
    BlockReason.DAILY_LIMIT -> {
        val limit = state.dailyLimitMinutes ?: 0
        "Daily limit of $limit min reached (used: ${state.usedTodayMinutes} min).\n" +
            "Unblocks at midnight - in ${formatDuration(state.minutesUntilMidnight)}."
    }
    BlockReason.GROUP_LIMIT -> {
        val group = state.groupName ?: "your group"
        val limit = state.groupLimitMinutes ?: 0
        "\"$group\" reached its shared daily limit of $limit min " +
            "(used together: ${state.groupUsedMinutes} min).\n" +
            "Unblocks at midnight - in ${formatDuration(state.minutesUntilMidnight)}."
    }
    BlockReason.GROUP_WINDOW -> {
        val group = state.groupName ?: "your group"
        val left = state.minutesUntilWindowEnd
        if (left != null) {
            "\"$group\" is blocked during its scheduled window.\nUnblocks in ${formatDuration(left)}."
        } else {
            "\"$group\" is blocked during its scheduled window."
        }
    }
    BlockReason.TIME_WINDOW -> {
        val left = state.minutesUntilWindowEnd
        if (left != null) "Blocked during your scheduled window.\nUnblocks in ${formatDuration(left)}."
        else "Blocked during your scheduled window."
    }
    BlockReason.ROUTINE_ACTIVE -> "A routine with app blocking is running.\nFinish the routine to use this app again."
    BlockReason.FOCUS_ACTIVE -> "A focus session is running.\nStay with it - the block ends with the session."
    BlockReason.SLEEP_ACTIVE -> {
        val left = state.minutesUntilWindowEnd
        if (left != null) {
            "Sleep mode is on - you planned to be asleep.\nUnblocks at wake-up in ${formatDuration(left)}."
        } else {
            "Sleep mode is on - you planned to be asleep."
        }
    }
    BlockReason.MANUAL -> "You chose to block this app.\nYou can change the rule in FocusForce+ at any time."
    BlockReason.GROUP_ALWAYS -> {
        val group = state.groupName ?: "One of your blocks"
        "\"$group\" blocks this app at all times.\nYou can change the block in FocusForce+ whenever you like."
    }
}

private fun formatDuration(totalMinutes: Int): String {
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}min"
        h > 0 -> "${h}h"
        else -> "${m}min"
    }
}
