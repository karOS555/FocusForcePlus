package com.focusforceplus.app.ui.screens.blocker

import com.focusforceplus.app.ui.components.verticalScrollWithHint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.focusforceplus.app.compliance.GuardSlot
import com.focusforceplus.app.ui.components.FfCard
import com.focusforceplus.app.ui.components.FfPaddedCard
import com.focusforceplus.app.ui.components.FormSection
import com.focusforceplus.app.ui.components.PageScaffold
import com.focusforceplus.app.ui.components.RowDivider
import com.focusforceplus.app.ui.components.SwitchRow
import com.focusforceplus.app.ui.guards.GuardCopy
import com.focusforceplus.app.ui.guards.GuardDialog
import com.focusforceplus.app.ui.theme.FfTheme

/**
 * The few blocker settings that apply to every block. Switching blocking off during
 * sessions counts as pausing, so it goes through the same speed bump.
 */
@Composable
fun BlockerSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: BlockerSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val guards by viewModel.guards.collectAsState()
    val colors = FfTheme.colors
    val snackbar = remember { SnackbarHostState() }
    var pausing by remember { mutableStateOf<(() -> Unit)?>(null) }
    val pauseGuard = guards[GuardSlot.BLOCKER_PAUSE]

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    fun toggle(on: Boolean, apply: (Boolean) -> Unit) {
        if (on || pauseGuard.isNone) apply(on) else pausing = { apply(false) }
    }

    PageScaffold(title = "Blocker settings", onBack = onNavigateBack, snackbarHostState = snackbar) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScrollWithHint()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            FormSection(
                title = "During sessions",
                hint = "Which blocks take part is set in each block.",
            ) {
                FfCard {
                    SwitchRow(
                        title = "Routines",
                        subtitle = "Blocks close while a routine with app blocking runs.",
                        checked = state.blockDuringRoutines,
                        onCheckedChange = { on -> toggle(on, viewModel::setBlockDuringRoutines) },
                    )
                    RowDivider()
                    SwitchRow(
                        title = "Focus sessions",
                        subtitle = "Blocks close while a focus session with app blocking runs.",
                        checked = state.blockDuringFocus,
                        onCheckedChange = { on -> toggle(on, viewModel::setBlockDuringFocus) },
                    )
                }
            }

            FormSection(title = "Good to know") {
                FfPaddedCard(spacing = 6.dp) {
                    Text("Never blocked", style = MaterialTheme.typography.titleSmall, color = colors.text)
                    Text(
                        "Android settings, the phone app, emergency calls, your home screen, " +
                            "your clock app and FocusForce+ itself.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
                Spacer(Modifier.height(10.dp))
                FfPaddedCard(spacing = 6.dp) {
                    Text("How strict it can get", style = MaterialTheme.typography.titleSmall, color = colors.text)
                    Text(
                        "An invincible block stays closed until its limit resets at midnight or " +
                            "its schedule ends. FocusForce+ cannot lock you in beyond that: you can " +
                            "always uninstall it from Android settings. It is there to stop a " +
                            "reflex, not to trap you.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }

    pausing?.let { action ->
        GuardDialog(
            guard = pauseGuard,
            copy = GuardCopy.BLOCKER_PAUSE,
            onPassed = {
                pausing = null
                action()
            },
            onDismiss = { pausing = null },
        )
    }
}
