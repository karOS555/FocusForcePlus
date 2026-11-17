package com.focusforceplus.app.ui.screens.focus

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.focusforceplus.app.compliance.ComplianceLimits
import com.focusforceplus.app.ui.common.DiscardChangesDialog
import com.focusforceplus.app.ui.common.IconPickerButton
import com.focusforceplus.app.ui.components.FfCard
import com.focusforceplus.app.ui.components.FfChip
import com.focusforceplus.app.ui.components.FfTextField
import com.focusforceplus.app.ui.components.FfTimePickerDialog
import com.focusforceplus.app.ui.components.FormScaffold
import com.focusforceplus.app.ui.components.FormSection
import com.focusforceplus.app.ui.components.RowDivider
import com.focusforceplus.app.ui.components.StepperRow
import com.focusforceplus.app.ui.components.SwitchRow
import com.focusforceplus.app.ui.components.ValueRow
import com.focusforceplus.app.ui.theme.FfTheme
import com.focusforceplus.app.util.PermissionHelper
import com.focusforceplus.app.util.TimeFormat

private val FOCUS_DAYS = listOf(
    "MO" to "Mon", "TU" to "Tue", "WE" to "Wed", "TH" to "Thu",
    "FR" to "Fri", "SA" to "Sat", "SU" to "Sun",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateFocusScreen(
    onNavigateBack: () -> Unit,
    viewModel: CreateFocusViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val colors = FfTheme.colors
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showTime by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    val attemptClose = { if (viewModel.hasUnsavedChanges) showDiscard = true else onNavigateBack() }
    BackHandler { attemptClose() }

    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it) }
    }

    val maxMinutes = ComplianceLimits.MAX_FOCUS_SESSION_DURATION.inWholeMinutes.toInt()
    val dndGranted = PermissionHelper.hasDndAccess(context)

    FormScaffold(
        title = if (viewModel.isEditMode) "Edit focus session" else "New focus session",
        onClose = attemptClose,
        primaryLabel = if (viewModel.isEditMode) "Save changes" else "Save session",
        onPrimary = { viewModel.save(onNavigateBack) },
        primaryLoading = state.isSaving,
        snackbarHostState = snackbar,
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconPickerButton(selectedKey = state.iconKey, size = 56.dp, onSelect = viewModel::updateIcon)
            FfTextField(
                value = state.name,
                onValueChange = viewModel::updateName,
                label = "Name",
                isError = state.nameError,
                supportingText = if (state.nameError) "Give it a name" else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.weight(1f),
            )
        }

        // ── Kind ──────────────────────────────────────────────────────────────
        FormSection(
            title = "Kind",
            hint = FocusTypes.of(state.type).caption.substringAfter("—").trim().ifEmpty { null },
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FocusTypes.ALL.forEach { meta ->
                    FfChip(
                        label = meta.label,
                        selected = state.type == meta.key,
                        onClick = { viewModel.updateType(meta.key) },
                    )
                }
            }
        }

        // ── Length ────────────────────────────────────────────────────────────
        FormSection(
            title = "Length",
            hint = "At most ${maxMinutes / 60} hours, so a locked session always ends.",
        ) {
            FfCard {
                StepperRow(
                    title = "Duration",
                    value = state.durationMinutes,
                    range = 5..maxMinutes,
                    step = 5,
                    onChange = viewModel::updateDuration,
                    valueLabel = { TimeFormat.duration(it) },
                )
            }
        }

        // ── While it runs ─────────────────────────────────────────────────────
        FormSection(title = "While it runs") {
            FfCard {
                SwitchRow(
                    title = "Do Not Disturb",
                    subtitle = if (dndGranted) "Silences calls and notifications, except priority ones." else
                        "Needs Do Not Disturb access; switching it on opens the setting.",
                    checked = state.enableDnd,
                    onCheckedChange = { on ->
                        if (on && !dndGranted) PermissionHelper.openDndAccessSettings(context)
                        viewModel.updateEnableDnd(on)
                    },
                )
                RowDivider()
                SwitchRow(
                    title = "Silence everything",
                    subtitle = "Total silence instead of priority only.",
                    checked = state.blockNotifications,
                    onCheckedChange = viewModel::updateBlockNotifications,
                )
                RowDivider()
                SwitchRow(
                    title = "Block distracting apps",
                    subtitle = "Uses your blocker setup.",
                    checked = state.appBlockerEnabled,
                    onCheckedChange = viewModel::updateAppBlocker,
                )
                if (state.appBlockerEnabled && state.availableGroups.isNotEmpty()) {
                    Column(Modifier.padding(horizontal = 14.dp).padding(bottom = 12.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FfChip(
                                label = "All blocked apps",
                                selected = state.blockAllApps,
                                onClick = { viewModel.updateBlockAllApps(true) },
                            )
                            FfChip(
                                label = "Only some groups",
                                selected = !state.blockAllApps,
                                onClick = { viewModel.updateBlockAllApps(false) },
                            )
                        }
                        if (!state.blockAllApps) {
                            Spacer(Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.availableGroups.forEach { group ->
                                    FfChip(
                                        label = group,
                                        selected = group in state.selectedGroups,
                                        onClick = { viewModel.toggleBlockedGroup(group) },
                                    )
                                }
                            }
                            if (state.selectedGroups.isEmpty()) {
                                Text(
                                    "Pick at least one group, or block all apps.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.error,
                                )
                            }
                        }
                    }
                }
                RowDivider()
                SwitchRow(
                    title = "Invincible mode",
                    subtitle = if (viewModel.isCurrentlyRunning) "Locked while the session runs." else
                        "Once started, it cannot be paused or ended early.",
                    checked = state.invincibleMode,
                    onCheckedChange = viewModel::updateInvincible,
                    enabled = !viewModel.isCurrentlyRunning,
                )
            }
        }

        // ── Schedule ──────────────────────────────────────────────────────────
        FormSection(title = "Schedule") {
            FfCard {
                SwitchRow(
                    title = "Remind me to start it",
                    subtitle = "On fixed days, at a fixed time.",
                    checked = state.scheduleEnabled,
                    onCheckedChange = viewModel::updateScheduleEnabled,
                )
                if (state.scheduleEnabled) {
                    RowDivider()
                    ValueRow(
                        title = "At",
                        value = TimeFormat.clock(state.scheduledHour * 60 + state.scheduledMinute),
                        onClick = { showTime = true },
                    )
                    RowDivider()
                    FlowRow(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FOCUS_DAYS.forEach { (key, label) ->
                            FfChip(
                                label = label,
                                selected = key in state.scheduledDays,
                                onClick = { viewModel.toggleScheduledDay(key) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showTime) {
        FfTimePickerDialog(
            title = "Remind me at",
            hour = state.scheduledHour,
            minute = state.scheduledMinute,
            onConfirm = { h, m ->
                viewModel.updateScheduledTime(h, m)
                showTime = false
            },
            onDismiss = { showTime = false },
        )
    }

    if (showDiscard) {
        DiscardChangesDialog(
            onDiscard = { showDiscard = false; onNavigateBack() },
            onKeepEditing = { showDiscard = false },
        )
    }
}
