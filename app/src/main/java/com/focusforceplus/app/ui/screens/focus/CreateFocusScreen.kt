package com.focusforceplus.app.ui.screens.focus

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.focusforceplus.app.compliance.ComplianceLimits
import com.focusforceplus.app.ui.common.DiscardChangesDialog
import com.focusforceplus.app.ui.common.routineDayLabel
import com.focusforceplus.app.util.PermissionHelper

private val FOCUS_DAY_KEYS = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateFocusScreen(
    onNavigateBack: () -> Unit,
    viewModel: CreateFocusViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    val attemptClose = {
        if (viewModel.hasUnsavedChanges) showDiscardDialog = true else onNavigateBack()
    }
    BackHandler { attemptClose() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditMode) "Edit Focus Session" else "New Focus Session") },
                navigationIcon = {
                    IconButton(onClick = attemptClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::updateName,
                label = { Text("Session name *") },
                isError = uiState.nameError,
                supportingText = if (uiState.nameError) ({ Text("Required") }) else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // ── Type ───────────────────────────────────────────────────────────
            SectionLabel("Type")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FocusTypes.ALL.forEach { meta ->
                    FilterChip(
                        selected = uiState.type == meta.key,
                        onClick = { viewModel.updateType(meta.key) },
                        label = { Text(meta.label) },
                        leadingIcon = {
                            Icon(
                                meta.icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = meta.color.copy(alpha = 0.2f),
                            selectedLabelColor = meta.color,
                            selectedLeadingIconColor = meta.color,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = uiState.type == meta.key,
                            selectedBorderColor = meta.color,
                        ),
                    )
                }
            }
            Text(
                text = FocusTypes.of(uiState.type).caption +
                    " Picking a type applies its suggested settings — adjust anything below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ── Duration ───────────────────────────────────────────────────────
            SectionLabel("Duration")
            Column {
                Text(
                    "${uiState.durationMinutes} minutes",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Slider(
                    value = uiState.durationMinutes.toFloat(),
                    onValueChange = { viewModel.updateDuration(it.toInt()) },
                    valueRange = 5f..ComplianceLimits.MAX_FOCUS_SESSION_DURATION.inWholeMinutes.toFloat(),
                    steps = 46,
                )
                Text(
                    "Capped at ${ComplianceLimits.MAX_FOCUS_SESSION_DURATION.inWholeHours} hours " +
                        "to keep locked sessions Google Play compliant.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ── Toggles ────────────────────────────────────────────────────────
            val dndGranted = PermissionHelper.hasDndAccess(context)

            FocusToggleRow(
                title = "Enable Do Not Disturb",
                subtitle = if (dndGranted) "Silences calls and notifications (priority only)"
                           else "Needs DND access - tap to grant it in system settings",
                checked = uiState.enableDnd,
                onChange = { enabled ->
                    if (enabled && !dndGranted) PermissionHelper.openDndAccessSettings(context)
                    viewModel.updateEnableDnd(enabled)
                },
            )

            FocusToggleRow(
                title = "Block all notifications",
                subtitle = "Total silence instead of priority-only (also needs DND access)",
                checked = uiState.blockNotifications,
                onChange = viewModel::updateBlockNotifications,
            )

            FocusToggleRow(
                title = "App Blocker",
                subtitle = "Block your configured apps during this session",
                checked = uiState.appBlockerEnabled,
                onChange = viewModel::updateAppBlocker,
            )

            // App-blocking scope: all apps, or specific groups.
            if (uiState.appBlockerEnabled) {
                Column(
                    modifier = Modifier.padding(start = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "What to block",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (uiState.availableGroups.isEmpty()) {
                        // No groups yet — the session blocks everything; guide the user.
                        Text(
                            "This session will block all the apps you've set up in the " +
                                "Blocker tab. To block specific app groups here (like just " +
                                "social media), first create a group under the Blocker tab.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = uiState.blockAllApps,
                                onClick = { viewModel.updateBlockAllApps(true) },
                                label = { Text("All blocked apps") },
                            )
                            FilterChip(
                                selected = !uiState.blockAllApps,
                                onClick = { viewModel.updateBlockAllApps(false) },
                                label = { Text("Specific groups") },
                            )
                        }
                        if (!uiState.blockAllApps) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                uiState.availableGroups.forEach { group ->
                                    FilterChip(
                                        selected = group in uiState.selectedGroups,
                                        onClick = { viewModel.toggleBlockedGroup(group) },
                                        label = { Text(group) },
                                    )
                                }
                            }
                            if (uiState.selectedGroups.isEmpty()) {
                                Text(
                                    "Pick at least one group, or switch back to \"All blocked apps\".",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }

            FocusToggleRow(
                title = "Invincible Mode",
                subtitle = if (viewModel.isCurrentlyRunning) {
                    "Locked while the session is running."
                } else {
                    "Once started, the session cannot be paused or ended early — it runs to the timer."
                },
                checked = uiState.invincibleMode,
                onChange = viewModel::updateInvincible,
                enabled = !viewModel.isCurrentlyRunning,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ── Schedule ───────────────────────────────────────────────────────
            FocusToggleRow(
                title = "Schedule",
                subtitle = "Remind me to start this session on fixed days",
                checked = uiState.scheduleEnabled,
                onChange = viewModel::updateScheduleEnabled,
            )

            if (uiState.scheduleEnabled) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FOCUS_DAY_KEYS.forEach { day ->
                        FilterChip(
                            selected = day in uiState.scheduledDays,
                            onClick = { viewModel.toggleScheduledDay(day) },
                            label = { Text(routineDayLabel(day)) },
                        )
                    }
                }
                FilledTonalButton(
                    onClick = { showTimePicker = true },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(Icons.Filled.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("%02d:%02d".format(uiState.scheduledHour, uiState.scheduledMinute))
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { viewModel.save(onNavigateBack) },
                enabled = !uiState.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(if (viewModel.isEditMode) "Save changes" else "Save session")
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = uiState.scheduledHour,
            initialMinute = uiState.scheduledMinute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Scheduled time") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state = timeState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateScheduledTime(timeState.hour, timeState.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
        )
    }

    if (showDiscardDialog) {
        DiscardChangesDialog(
            onDiscard = { showDiscardDialog = false; onNavigateBack() },
            onKeepEditing = { showDiscardDialog = false },
        )
    }
}

@Composable
private fun FocusToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
