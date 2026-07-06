package com.focusforceplus.app.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.focusforceplus.app.ui.theme.Error
import com.focusforceplus.app.ui.theme.Warning
import com.focusforceplus.app.util.PermissionHelper
import com.focusforceplus.app.util.TodoAlarmHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onOpenBlockerSettings: () -> Unit = {},
    onOpenDisclosure: (String) -> Unit = {},
    onReplayOnboarding: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var editingSlot by remember { mutableIntStateOf(-1) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showTpTimePicker by remember { mutableStateOf(false) }

    // ── Backup (SAF) ──────────────────────────────────────────────────────────
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(viewModel::exportBackup) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { pendingImportUri = it } }

    // Permissions can change in system settings — refresh whenever we return.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            // ── General ───────────────────────────────────────────────────────
            item {
                SettingsSection(title = "General") {
                    ToggleRow(
                        title = "Alarm sound",
                        subtitle = "Play sound on routine and high-priority todo alarms",
                        checked = uiState.alarmSoundEnabled,
                        onChange = viewModel::setAlarmSoundEnabled,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    ToggleRow(
                        title = "Alarm vibration",
                        subtitle = "Vibrate on alarms and task-complete overlays",
                        checked = uiState.alarmVibrationEnabled,
                        onChange = viewModel::setAlarmVibrationEnabled,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    NavigationRow(
                        title = "Show setup guide again",
                        subtitle = "Replay the welcome and permission walkthrough",
                        onClick = onReplayOnboarding,
                    )
                }
            }

            // ── Todos ─────────────────────────────────────────────────────────
            item {
                SettingsSection(title = "Todos") {
                    // Daily reminders
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Daily reminders",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            "Notify you about open and overdue todos at these times.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(2.dp))

                        uiState.digestTimes.forEachIndexed { index, (hour, minute) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    ) {
                                        Icon(
                                            Icons.Filled.AccessTime,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "%02d:%02d".format(hour, minute),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                FilledTonalButton(
                                    onClick = { editingSlot = index; showTimePicker = true },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(36.dp),
                                ) {
                                    Text("Edit", style = MaterialTheme.typography.labelMedium)
                                }
                                Spacer(Modifier.weight(1f))
                                if (uiState.digestTimes.size > 1) {
                                    IconButton(
                                        onClick = { viewModel.removeDigestTime(index) },
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "Remove",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }

                        if (uiState.digestTimes.size < TodoAlarmHelper.MAX_DIGEST_SLOTS) {
                            TextButton(
                                onClick = {
                                    editingSlot = TodoAlarmHelper.MAX_DIGEST_SLOTS
                                    showTimePicker = true
                                },
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add reminder time")
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    // Default priority
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Default priority for new todos",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to "Low", 1 to "Medium", 2 to "High").forEach { (value, label) ->
                                val accentColor = when (value) {
                                    0 -> MaterialTheme.colorScheme.onSurfaceVariant
                                    2 -> Error
                                    else -> Warning
                                }
                                FilterChip(
                                    selected = uiState.defaultPriority == value,
                                    onClick = { viewModel.setDefaultPriority(value) },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accentColor.copy(alpha = 0.18f),
                                        selectedLabelColor = accentColor,
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = uiState.defaultPriority == value,
                                        selectedBorderColor = accentColor,
                                    ),
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    // Auto-delete completed
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Auto-delete completed todos",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            "Automatically remove completed todos after a set time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to "Never", 7 to "7 days", 30 to "30 days", 90 to "90 days")
                                .forEach { (days, label) ->
                                    FilterChip(
                                        selected = uiState.autoDeleteDays == days,
                                        onClick = { viewModel.setAutoDeleteDays(days) },
                                        label = { Text(label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                        ),
                                    )
                                }
                        }
                    }
                }
            }

            // ── App Blocker ───────────────────────────────────────────────────
            item {
                SettingsSection(title = "App Blocker") {
                    NavigationRow(
                        title = "Blocking rules and settings",
                        subtitle = "Master switch, session blocking, Invincible Mode info",
                        onClick = onOpenBlockerSettings,
                    )
                }
            }

            // ── Tamper Protection ─────────────────────────────────────────────
            item {
                SettingsSection(title = "Tamper Protection") {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "Protects your Invincible-Mode settings from impulsive changes: " +
                                "while enabled, weakening them (turning invincible flags off, " +
                                "softening invincible blocker rules, the blocker master switch) " +
                                "only works during a daily change window you pick. Honest " +
                                "limits: this never blocks uninstalling the app, and a factory " +
                                "reset always works.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        val windowLabel = "%02d:%02d".format(
                            uiState.tpWindowStartMinutes / 60,
                            uiState.tpWindowStartMinutes % 60,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Daily change window",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Text(
                                    if (uiState.tpEnabled) {
                                        if (uiState.tpWindowOpenNow) "Open right now" else "Currently closed"
                                    } else "Protection is off",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (uiState.tpEnabled && uiState.tpWindowOpenNow)
                                        Color(0xFF34d399)
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            FilledTonalButton(
                                onClick = { showTpTimePicker = true },
                                shape = RoundedCornerShape(8.dp),
                                enabled = !uiState.tpEnabled || uiState.tpWindowOpenNow,
                            ) { Text("Starts $windowLabel") }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(10, 20, 30, 45, 60).forEach { minutes ->
                                FilterChip(
                                    selected = uiState.tpWindowDurationMinutes == minutes,
                                    onClick = {
                                        viewModel.setTpWindow(uiState.tpWindowStartMinutes, minutes)
                                    },
                                    enabled = !uiState.tpEnabled || uiState.tpWindowOpenNow,
                                    label = { Text("$minutes min") },
                                )
                            }
                        }

                        Button(
                            onClick = viewModel::toggleTamperProtection,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(if (uiState.tpEnabled) "Disable Tamper Protection" else "Enable Tamper Protection")
                        }
                        Text(
                            "Turning it on AND off only works inside the window itself - " +
                                "that is the whole point: changes happen at your planned " +
                                "moment, not in a weak one.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── Permissions ───────────────────────────────────────────────────
            item {
                SettingsSection(title = "Permissions") {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        PermissionStatusRow("Notifications", uiState.permissions.notifications) {
                            // The system prompt only appears once; afterwards it must be
                            // granted from app settings — send the user there directly.
                            context.startActivity(
                                android.content.Intent(
                                    android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                ).apply {
                                    putExtra(
                                        android.provider.Settings.EXTRA_APP_PACKAGE,
                                        context.packageName,
                                    )
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        }
                        PermissionStatusRow("Exact alarms", uiState.permissions.exactAlarms) {
                            PermissionHelper.openExactAlarmSettings(context)
                        }
                        PermissionStatusRow("Display over other apps", uiState.permissions.overlay) {
                            PermissionHelper.openOverlaySettings(context)
                        }
                        PermissionStatusRow("Full-screen alarms", uiState.permissions.fullScreenIntent) {
                            PermissionHelper.openFullScreenIntentSettings(context)
                        }
                        PermissionStatusRow("Accessibility (app blocker)", uiState.permissions.accessibility) {
                            onOpenDisclosure("accessibility")
                        }
                        PermissionStatusRow("Usage access (daily limits)", uiState.permissions.usageStats) {
                            onOpenDisclosure("usage")
                        }
                        PermissionStatusRow("Battery: unrestricted", uiState.permissions.batteryUnrestricted) {
                            PermissionHelper.openBatteryOptimizationSettings(context)
                        }
                        PermissionStatusRow("Do Not Disturb access", uiState.permissions.dndAccess) {
                            PermissionHelper.openDndAccessSettings(context)
                        }
                    }
                }
            }

            // ── Data ──────────────────────────────────────────────────────────
            item {
                SettingsSection(title = "Data") {
                    NavigationRow(
                        title = "Reset usage statistics",
                        subtitle = "Sets today's app-usage counters and exceptions back to zero",
                        onClick = viewModel::resetUsageStatistics,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    NavigationRow(
                        title = "Export as JSON",
                        subtitle = "Save all routines, todos, blocker rules, sessions, and history to a file",
                        onClick = {
                            exportLauncher.launch(
                                com.focusforceplus.app.util.BackupManager.SUGGESTED_FILE_NAME
                            )
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    NavigationRow(
                        title = "Import from JSON",
                        subtitle = "Restore a backup — replaces everything currently in the app",
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                    )
                }
            }

            // ── About ─────────────────────────────────────────────────────────
            item {
                SettingsSection(title = "About") {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "Version",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                uiState.appVersion,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "License",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                "Open Source - GPLv3",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(
                            onClick = {
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://github.com/karOS555/FocusForcePlus"),
                                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            },
                            contentPadding = PaddingValues(0.dp),
                        ) { Text("Source code on GitHub") }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                        Text(
                            "Credits: concepts and architecture draw on the open-source apps " +
                                "Mindful (akaMrNagar), MindMaster (ArmanKhanTech), and " +
                                "Curbox/DigiPaws (nethical6), plus UX inspiration from Routinery.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                        Text(
                            "Health disclaimer: FocusForce+ is not a medical device and does " +
                                "not diagnose, treat, cure, or prevent ADHD or any other medical " +
                                "condition. It is a self-help tool for building habits and " +
                                "digital wellbeing. For medical advice, diagnosis, or treatment, " +
                                "please consult a qualified healthcare professional.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(22.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 110.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFFE40303), Color(0xFFFF8C00), Color(0xFFFFED00),
                                    Color(0xFF008026), Color(0xFF004DFF), Color(0xFF750787),
                                )
                            )
                        ),
                )
                Spacer(Modifier.height(18.dp))
            }
        }
    }

    // ── Tamper Protection window time picker ───────────────────────────────────
    if (showTpTimePicker) {
        val tpTimeState = rememberTimePickerState(
            initialHour = uiState.tpWindowStartMinutes / 60,
            initialMinute = uiState.tpWindowStartMinutes % 60,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTpTimePicker = false },
            title = { Text("Window start time") },
            text = {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                    TimePicker(state = tpTimeState)
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.setTpWindow(
                        tpTimeState.hour * 60 + tpTimeState.minute,
                        uiState.tpWindowDurationMinutes,
                    )
                    showTpTimePicker = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showTpTimePicker = false }) { Text("Cancel") }
            },
        )
    }

    // ── Import confirmation ────────────────────────────────────────────────────
    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("Replace all data?") },
            text = {
                Text(
                    "Importing this backup replaces ALL routines, todos, blocker rules, " +
                        "focus sessions, and history currently in the app. Alarms are " +
                        "re-armed for the imported data. This cannot be undone.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.importBackup(uri)
                        pendingImportUri = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Import and replace") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) { Text("Cancel") }
            },
        )
    }

    // ── Time picker dialog ─────────────────────────────────────────────────────
    // key(editingSlot) forces rememberTimePickerState to re-initialize with the
    // correct hour/minute whenever a different slot is opened.
    if (showTimePicker) {
        val isAdding = editingSlot !in uiState.digestTimes.indices
        val initialHour   = if (!isAdding) uiState.digestTimes[editingSlot].first   else 9
        val initialMinute = if (!isAdding) uiState.digestTimes[editingSlot].second  else 0
        key(editingSlot) {
            val timePickerState = rememberTimePickerState(
                initialHour   = initialHour,
                initialMinute = initialMinute,
                is24Hour      = true,
            )
            AlertDialog(
                onDismissRequest = { showTimePicker = false; editingSlot = -1 },
                title = { Text(if (isAdding) "Add reminder time" else "Edit reminder time") },
                text = {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                        TimePicker(state = timePickerState)
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (isAdding) {
                            viewModel.addDigestTime(timePickerState.hour, timePickerState.minute)
                        } else {
                            viewModel.updateDigestTime(editingSlot, timePickerState.hour, timePickerState.minute)
                        }
                        showTimePicker = false
                        editingSlot = -1
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { showTimePicker = false; editingSlot = -1 }) { Text("Cancel") }
                },
            )
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) { content() }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

@Composable
private fun NavigationRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (granted) Icons.Filled.Check else Icons.Filled.Close,
            contentDescription = if (granted) "Granted" else "Not granted",
            tint = if (granted) Color(0xFF34d399) else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (!granted) {
            TextButton(onClick = onGrant) { Text("Grant") }
        }
    }
}

@Composable
private fun PlaceholderRow(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(
            "Coming soon",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
}
