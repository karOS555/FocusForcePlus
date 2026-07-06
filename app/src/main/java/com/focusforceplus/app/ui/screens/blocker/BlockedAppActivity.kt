package com.focusforceplus.app.ui.screens.blocker

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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF050508), Color(0xFF0f2847)))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val bitmap = icon?.toBitmap(96, 96)
            if (bitmap != null) {
                Icon(
                    painter = BitmapPainter(bitmap.asImageBitmap()),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(72.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Block,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp),
                )
            }

            Text(
                text = state.appName.ifBlank { state.packageName },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )

            Text(
                text = "This app is blocked",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.7f),
            )

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.White.copy(alpha = 0.07f),
            ) {
                Text(
                    text = reasonText(state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onGoHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text("Back to home screen", style = MaterialTheme.typography.titleMedium)
            }

            if (!state.isLoading && state.canUseException) {
                OutlinedButton(
                    onClick = onUseException,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        "5-minute exception (${state.exceptionsLeft} left today)",
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        }
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
    BlockReason.TIME_WINDOW -> {
        val left = state.minutesUntilWindowEnd
        if (left != null) "Blocked during your scheduled window.\nUnblocks in ${formatDuration(left)}."
        else "Blocked during your scheduled window."
    }
    BlockReason.ROUTINE_ACTIVE -> "A routine with app blocking is running.\nFinish the routine to use this app again."
    BlockReason.FOCUS_ACTIVE -> "A focus session is running.\nStay with it - the block ends with the session."
    BlockReason.MANUAL -> "You chose to block this app.\nYou can change the rule in FocusForce+ at any time."
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
