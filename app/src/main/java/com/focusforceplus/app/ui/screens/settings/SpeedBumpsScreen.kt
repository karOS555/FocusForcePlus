package com.focusforceplus.app.ui.screens.settings

import androidx.compose.foundation.lazy.rememberLazyListState
import com.focusforceplus.app.ui.components.scrollHint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.focusforceplus.app.compliance.GuardSlot
import com.focusforceplus.app.ui.components.ConfirmDialog
import com.focusforceplus.app.ui.components.FfCard
import com.focusforceplus.app.ui.components.FormSection
import com.focusforceplus.app.ui.components.PageScaffold
import com.focusforceplus.app.ui.components.RowDivider
import com.focusforceplus.app.ui.components.SwitchRow
import com.focusforceplus.app.ui.components.TextAction
import com.focusforceplus.app.ui.guards.GuardEditorDialog
import com.focusforceplus.app.ui.guards.GuardRow
import com.focusforceplus.app.ui.theme.FfTheme

/**
 * The default speed bumps: one row per place in the app that has one. Each todo
 * and routine can still pick its own in its editor.
 */
@Composable
fun SpeedBumpsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val colors = FfTheme.colors
    var editing by remember { mutableStateOf<GuardSlot?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val groups = GuardSlot.entries.groupBy { it.group }

    PageScaffold(title = "Speed bumps", onBack = onNavigateBack) { padding ->
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .scrollHint(listState),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item {
                Text(
                    "Each one can ask again, count down, and give you a small task. The parts " +
                        "add up, so you can use one or all three. These are the defaults; a todo " +
                        "or routine can choose its own when you edit it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                )
            }
            item {
                FfCard {
                    SwitchRow(
                        title = "Use speed bumps",
                        subtitle = "Off means none anywhere, whatever a todo or routine says.",
                        checked = state.guards.enabled,
                        onCheckedChange = viewModel::setGuardsEnabled,
                    )
                }
            }
            groups.forEach { (group, slots) ->
                item(key = group) {
                    FormSection(title = group) {
                        FfCard {
                            slots.forEachIndexed { index, slot ->
                                if (index > 0) RowDivider()
                                GuardRow(
                                    title = slot.label,
                                    guard = state.guards.configured(slot),
                                    note = if (state.guards.slots[slot] == null) "default" else null,
                                    enabled = state.guards.enabled,
                                    onClick = { editing = slot },
                                )
                            }
                        }
                    }
                }
            }
            item {
                TextAction(
                    "Put all back to the defaults",
                    onClick = { confirmReset = true },
                    modifier = Modifier.offset(x = (-8).dp),
                )
            }
        }
    }

    editing?.let { slot ->
        GuardEditorDialog(
            title = "${slot.group}: ${slot.label.replaceFirstChar { it.lowercase() }}",
            current = state.guards.slots[slot],
            default = slot.default,
            onSave = {
                viewModel.setGuard(slot, it)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    if (confirmReset) {
        ConfirmDialog(
            title = "Back to the defaults?",
            message = "Every speed bump on this page goes back to how the app ships. What you " +
                "set on single todos and routines stays.",
            confirmLabel = "Reset",
            onConfirm = {
                viewModel.resetGuards()
                confirmReset = false
            },
            onDismiss = { confirmReset = false },
        )
    }
}
