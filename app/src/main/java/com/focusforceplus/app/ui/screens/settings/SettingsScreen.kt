package com.focusforceplus.app.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.focusforceplus.app.compliance.ComplianceLimits
import com.focusforceplus.app.compliance.NagPolicy
import com.focusforceplus.app.ui.components.Choice
import com.focusforceplus.app.ui.components.ChoiceDialog
import com.focusforceplus.app.ui.components.ConfirmDialog
import com.focusforceplus.app.ui.components.FfCard
import com.focusforceplus.app.ui.components.FfChip
import com.focusforceplus.app.ui.components.FfSwitch
import com.focusforceplus.app.ui.components.FormSection
import com.focusforceplus.app.ui.components.ListRow
import com.focusforceplus.app.ui.components.PageScaffold
import com.focusforceplus.app.ui.components.RowDivider
import com.focusforceplus.app.ui.components.SwitchRow
import com.focusforceplus.app.ui.components.TextAction
import com.focusforceplus.app.ui.components.ValueRow
import com.focusforceplus.app.ui.navigation.NavEditSheet
import com.focusforceplus.app.ui.navigation.NavViewModel
import com.focusforceplus.app.ui.theme.FfTheme
import com.focusforceplus.app.util.BackupManager
import com.focusforceplus.app.util.PermissionHelper
import com.focusforceplus.app.util.TimeFormat
import com.focusforceplus.app.util.TodoAlarmScheduler

private enum class SettingsDialog { RING, NAG_HIGH, NAG_MEDIUM, GAP, PRIORITY, AUTO_TRASH, RETENTION, TP_WINDOW }

private const val REPO_URL = "https://github.com/karOS555/FocusForcePlus"

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onOpenBlockerSettings: () -> Unit = {},
    onOpenDisclosure: (String) -> Unit = {},
    onReplayOnboarding: () -> Unit = {},
    onOpenSleepMode: () -> Unit = {},
    onOpenSpeedBumps: () -> Unit = {},
    onOpenTrash: () -> Unit = {},
    onOpenIntegrations: () -> Unit = {},
    onOpenAutoBackup: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
    onOpenCalendar: () -> Unit = {},
    onOpenTaskSync: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    navViewModel: NavViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val navConfig by navViewModel.config.collectAsState()
    val navMessage by navViewModel.message.collectAsState()
    val context = LocalContext.current
    val colors = FfTheme.colors
    val snackbar = remember { SnackbarHostState() }

    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    // -2: no time picker; -1: adding a time; otherwise the index being edited.
    var editingTime by remember { mutableIntStateOf(-2) }
    var showNavEdit by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var confirmResetUsage by remember { mutableStateOf(false) }
    var pickingWidget by remember { mutableStateOf(false) }
    var confirmUndoImport by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(viewModel::exportBackup) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { pendingImportUri = it } }

    // Permissions can change in system settings, so refresh on every return.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(navMessage) {
        navMessage?.let {
            snackbar.showSnackbar(it)
            navViewModel.clearMessage()
        }
    }

    PageScaffold(title = "Settings", onBack = onNavigateBack, snackbarHostState = snackbar) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            // ── Alarms ────────────────────────────────────────────────────────
            item {
                FormSection(title = "Alarms") {
                    FfCard {
                        SwitchRow(
                            title = "Sound",
                            subtitle = "Routines and high-priority todos ring out loud.",
                            checked = state.alarmSoundEnabled,
                            onCheckedChange = viewModel::setAlarmSoundEnabled,
                        )
                        RowDivider()
                        SwitchRow(
                            title = "Vibration",
                            subtitle = "When an alarm goes off and when a task's time is up.",
                            checked = state.alarmVibrationEnabled,
                            onCheckedChange = viewModel::setAlarmVibrationEnabled,
                        )
                        RowDivider()
                        ValueRow(
                            title = "A high-priority todo rings for",
                            value = "${state.todoRingSeconds} s",
                            onClick = { dialog = SettingsDialog.RING },
                        )
                    }
                }
            }

            // ── Speed bumps ───────────────────────────────────────────────────
            item {
                FormSection(
                    title = "Speed bumps",
                    hint = "Small steps in front of the quick taps, like marking something done " +
                        "or snoozing, so they stay a decision.",
                ) {
                    FfCard {
                        SwitchRow(
                            title = "Use speed bumps",
                            subtitle = "Off means none anywhere, whatever a todo or routine says.",
                            checked = state.guards.enabled,
                            onCheckedChange = viewModel::setGuardsEnabled,
                        )
                        RowDivider()
                        LinkRow(
                            title = "Defaults",
                            subtitle = "For each priority, routines, focus sessions and the blocker",
                            onClick = onOpenSpeedBumps,
                        )
                    }
                }
            }

            // ── Todos ─────────────────────────────────────────────────────────
            item {
                FormSection(
                    title = "Daily reminders",
                    hint = "At these times, open medium and high todos without a time come up one " +
                        "after another, and low ones are listed in one quiet notification.",
                ) {
                    FfCard {
                        state.digestTimes.forEachIndexed { index, (hour, minute) ->
                            if (index > 0) RowDivider()
                            ListRow(
                                title = TimeFormat.clock(hour * 60 + minute),
                                onClick = { editingTime = index },
                                trailing = {
                                    if (state.digestTimes.size > 1) {
                                        IconButton(onClick = { viewModel.removeDigestTime(index) }) {
                                            Icon(
                                                Icons.Filled.Close,
                                                contentDescription = "Remove ${TimeFormat.clock(hour * 60 + minute)}",
                                                tint = colors.textMuted,
                                            )
                                        }
                                    }
                                },
                            )
                        }
                        if (state.digestTimes.size < TodoAlarmScheduler.MAX_DIGEST_SLOTS) {
                            RowDivider()
                            ListRow(
                                title = "Add a time",
                                titleColor = colors.accent,
                                onClick = { editingTime = -1 },
                                trailing = { Icon(Icons.Filled.Add, contentDescription = null, tint = colors.accent) },
                            )
                        }
                    }
                }
            }

            item {
                FormSection(title = "Todos") {
                    FfCard {
                        ValueRow(
                            title = "High priority comes back",
                            value = nagLabel(state.nagHigh),
                            onClick = { dialog = SettingsDialog.NAG_HIGH },
                        )
                        RowDivider()
                        ValueRow(
                            title = "Medium priority comes back",
                            value = nagLabel(state.nagMedium),
                            onClick = { dialog = SettingsDialog.NAG_MEDIUM },
                        )
                        RowDivider()
                        ValueRow(
                            title = "Gap between alarms",
                            subtitle = "When several todos come up at a reminder time",
                            value = "${state.undatedAlarmGapMinutes} min",
                            onClick = { dialog = SettingsDialog.GAP },
                        )
                        RowDivider()
                        ValueRow(
                            title = "Priority of new todos",
                            value = priorityLabel(state.defaultPriority),
                            onClick = { dialog = SettingsDialog.PRIORITY },
                        )
                        RowDivider()
                        ValueRow(
                            title = "Done todos go to the trash",
                            value = autoTrashLabel(state.autoDeleteDays),
                            onClick = { dialog = SettingsDialog.AUTO_TRASH },
                        )
                    }
                }
            }

            // ── Look ──────────────────────────────────────────────────────────
            item {
                val theme by com.focusforceplus.app.ui.theme.ThemeStore.spec.collectAsState()
                FormSection(title = "Look") {
                    FfCard {
                        LinkRow(title = "Theme", subtitle = theme.name, onClick = onOpenAppearance)
                        RowDivider()
                        LinkRow(
                            title = "Widgets",
                            subtitle = "Today, quick focus and a list on your home screen",
                            onClick = { pickingWidget = true },
                        )
                    }
                }
            }

            // ── Navigation and modules ────────────────────────────────────────
            item {
                FormSection(title = "Navigation and modules") {
                    FfCard {
                        LinkRow(
                            title = "Navigation bar",
                            subtitle = "Order, what is in the bar, modules that are off",
                            onClick = { showNavEdit = true },
                        )
                        RowDivider()
                        LinkRow(
                            title = "App blocker",
                            subtitle = "Master switch, blocking during routines and focus",
                            onClick = onOpenBlockerSettings,
                        )
                        RowDivider()
                        LinkRow(
                            title = "Sleep",
                            subtitle = "Bedtime, wake-up, wind-down, night blocking",
                            onClick = onOpenSleepMode,
                        )
                    }
                }
            }

            // ── Tamper protection ─────────────────────────────────────────────
            item {
                FormSection(
                    title = "Tamper protection",
                    hint = "Switching it on or off also only works inside the window. That is the " +
                        "point: changes happen at a moment you planned, not in a weak one. It " +
                        "never blocks uninstalling, and a factory reset always works.",
                ) {
                    FfCard {
                        ListRow(
                            title = "Protect invincible settings",
                            subtitle = "While on, anything in invincible mode can only be " +
                                "weakened inside a daily window you pick.",
                            subtitleMaxLines = 3,
                            onClick = viewModel::toggleTamperProtection,
                            trailing = { FfSwitch(checked = state.tpEnabled, onCheckedChange = null) },
                        )
                        RowDivider()
                        ValueRow(
                            title = "Daily window",
                            subtitle = when {
                                !state.tpEnabled -> null
                                state.tpWindowOpenNow -> "Open right now"
                                else -> "Closed right now"
                            },
                            value = "${TimeFormat.clock(state.tpWindowStartMinutes)} · " +
                                "${state.tpWindowDurationMinutes} min",
                            enabled = !state.tpEnabled || state.tpWindowOpenNow,
                            onClick = { dialog = SettingsDialog.TP_WINDOW },
                        )
                    }
                }
            }

            // ── Permissions ───────────────────────────────────────────────────
            item {
                val perms = state.permissions
                FormSection(title = "Permissions") {
                    FfCard {
                        PermissionRow("Notifications", perms.notifications) {
                            // The system asks only once; after that it is granted in the
                            // app's notification settings.
                            context.startActivity(
                                Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                        RowDivider()
                        PermissionRow("Exact alarms", perms.exactAlarms) {
                            PermissionHelper.openExactAlarmSettings(context)
                        }
                        RowDivider()
                        PermissionRow("Display over other apps", perms.overlay) {
                            PermissionHelper.openOverlaySettings(context)
                        }
                        RowDivider()
                        PermissionRow(
                            title = "Full-screen alarms",
                            granted = perms.fullScreenIntent,
                            unavailableNote = if (perms.fullScreenIntentGrantable) null else
                                "This phone has no setting for it. Alarms still ring and open " +
                                    "over the lock screen through \"Display over other apps\".",
                        ) { PermissionHelper.openFullScreenIntentSettings(context) }
                        RowDivider()
                        PermissionRow("Accessibility (app blocker)", perms.accessibility) {
                            onOpenDisclosure("accessibility")
                        }
                        RowDivider()
                        PermissionRow("Usage access (daily limits)", perms.usageStats) {
                            onOpenDisclosure("usage")
                        }
                        RowDivider()
                        PermissionRow("Battery: unrestricted", perms.batteryUnrestricted) {
                            PermissionHelper.openBatteryOptimizationSettings(context)
                        }
                        RowDivider()
                        PermissionRow("Do Not Disturb access", perms.dndAccess) {
                            PermissionHelper.openDndAccessSettings(context)
                        }
                        // Android 16 and later: what puts a running routine, focus session
                        // or sleep into the status bar chip and the lock screen.
                        if (perms.liveUpdatesSupported) {
                            RowDivider()
                            PermissionRow(
                                title = "Live updates",
                                granted = perms.liveUpdates,
                                unavailableNote = if (perms.liveUpdatesGrantable) null else
                                    "Not offered by this phone. Nothing is missing: running " +
                                        "sessions show their full notification, on the lock screen too.",
                            ) { PermissionHelper.openLiveUpdateSettings(context) }
                        }
                    }
                }
            }

            // ── Data ──────────────────────────────────────────────────────────
            item {
                FormSection(title = "Data") {
                    FfCard {
                        LinkRow(
                            title = "Trash",
                            subtitle = "Deleted todos, routines, focus sessions and lists",
                            onClick = onOpenTrash,
                        )
                        RowDivider()
                        ValueRow(
                            title = "Keep things in the trash for",
                            value = "${state.trashRetentionDays} days",
                            onClick = { dialog = SettingsDialog.RETENTION },
                        )
                        RowDivider()
                        LinkRow(
                            title = "Export a backup",
                            subtitle = "Everything in one file, saved where you choose",
                            onClick = { exportLauncher.launch(BackupManager.SUGGESTED_FILE_NAME) },
                        )
                        RowDivider()
                        LinkRow(
                            title = "Automatic backup",
                            subtitle = if (state.autoBackupOn) "Every night into a folder you chose" else "Off",
                            onClick = onOpenAutoBackup,
                        )
                        RowDivider()
                        LinkRow(
                            title = "Import a backup",
                            subtitle = "Replaces everything that is in the app now; what is here now is kept to undo it",
                            onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                        )
                        if (state.canUndoImport) {
                            RowDivider()
                            LinkRow(
                                title = "Undo the last import",
                                subtitle = "Puts back what was in the app before it",
                                onClick = { confirmUndoImport = true },
                            )
                        }
                        RowDivider()
                        LinkRow(
                            title = "Calendar",
                            subtitle = "Today's events on Home, todos in a calendar, blocking during events",
                            onClick = onOpenCalendar,
                        )
                        RowDivider()
                        LinkRow(
                            title = "Tasks app",
                            subtitle = "Sync todos with OpenTasks or Tasks.org, and with DAVx5 beyond",
                            onClick = onOpenTaskSync,
                        )
                        RowDivider()
                        LinkRow(
                            title = "Other apps",
                            subtitle = if (state.externalStartsAllowed) "Sharing, start links are on" else "Sharing, start links are off",
                            onClick = onOpenIntegrations,
                        )
                        RowDivider()
                        LinkRow(
                            title = "Reset today's usage counters",
                            subtitle = "Blocker minutes and exceptions start from zero",
                            onClick = { confirmResetUsage = true },
                        )
                    }
                }
            }

            // ── About ─────────────────────────────────────────────────────────
            item {
                FormSection(title = "About") {
                    FfCard {
                        ListRow(
                            title = "Version",
                            trailing = {
                                Text(state.appVersion, style = MaterialTheme.typography.bodyLarge, color = colors.textMuted)
                            },
                        )
                        RowDivider()
                        LinkRow(
                            title = "Source code",
                            subtitle = "Free and open source, GPLv3",
                            onClick = { openUrl(context, REPO_URL) },
                        )
                        RowDivider()
                        // No internet access by design, so the app cannot look for
                        // updates itself; this opens the releases page instead.
                        LinkRow(
                            title = "Check for updates",
                            subtitle = "Opens the latest release. Obtainium can update the app for you.",
                            onClick = { openUrl(context, "$REPO_URL/releases/latest") },
                        )
                        RowDivider()
                        LinkRow(
                            title = "Report a bug or suggest an idea",
                            subtitle = "Opens GitHub",
                            onClick = { openUrl(context, "$REPO_URL/issues/new/choose") },
                        )
                        RowDivider()
                        LinkRow(
                            title = "Show the setup guide again",
                            subtitle = "Permissions, updates and the navigation bar",
                            onClick = onReplayOnboarding,
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Concepts and architecture draw on the open-source apps Mindful " +
                            "(akaMrNagar), MindMaster (ArmanKhanTech) and Curbox/DigiPaws " +
                            "(nethical6), with UX ideas from Routinery.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                    Text(
                        "FocusForce+ is not a medical device and does not diagnose, treat, cure " +
                            "or prevent ADHD or any other condition. It is a self-help tool for " +
                            "habits and digital wellbeing. For medical advice, please talk to a " +
                            "qualified healthcare professional.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
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

    // ── Dialogs ───────────────────────────────────────────────────────────────

    when (dialog) {
        SettingsDialog.RING -> ChoiceDialog(
            title = "A high-priority todo rings for",
            choices = listOf(15, 30, 60, 90, 120, 180)
                .filter { it in ComplianceLimits.MIN_TODO_RING_SECONDS..ComplianceLimits.MAX_TODO_RING_SECONDS }
                .map { Choice(it, "$it seconds") },
            selected = state.todoRingSeconds,
            onSelect = { viewModel.setTodoRingSeconds(it); dialog = null },
            onDismiss = { dialog = null },
        )
        SettingsDialog.NAG_HIGH -> NagDialog(
            title = "High priority comes back",
            policy = state.nagHigh,
            onSave = { viewModel.setNagPolicy(2, it); dialog = null },
            onDismiss = { dialog = null },
        )
        SettingsDialog.NAG_MEDIUM -> NagDialog(
            title = "Medium priority comes back",
            policy = state.nagMedium,
            onSave = { viewModel.setNagPolicy(1, it); dialog = null },
            onDismiss = { dialog = null },
        )
        SettingsDialog.GAP -> ChoiceDialog(
            title = "Gap between alarms",
            choices = listOf(2, 5, 10, 15).map { Choice(it, "$it minutes") },
            selected = state.undatedAlarmGapMinutes,
            onSelect = { viewModel.setUndatedAlarmGapMinutes(it); dialog = null },
            onDismiss = { dialog = null },
        )
        SettingsDialog.PRIORITY -> ChoiceDialog(
            title = "Priority of new todos",
            choices = listOf(0, 1, 2).map { Choice(it, priorityLabel(it)) },
            selected = state.defaultPriority,
            onSelect = { viewModel.setDefaultPriority(it); dialog = null },
            onDismiss = { dialog = null },
        )
        SettingsDialog.AUTO_TRASH -> ChoiceDialog(
            title = "Done todos go to the trash",
            choices = listOf(0, 7, 30, 90).map { Choice(it, autoTrashLabel(it)) },
            selected = state.autoDeleteDays,
            onSelect = { viewModel.setAutoDeleteDays(it); dialog = null },
            onDismiss = { dialog = null },
        )
        SettingsDialog.RETENTION -> ChoiceDialog(
            title = "Keep things in the trash for",
            choices = listOf(7, 14, 30, 60, 90, 180, 365)
                .filter { it <= ComplianceLimits.MAX_TRASH_RETENTION_DAYS }
                .map { Choice(it, "$it days") },
            selected = state.trashRetentionDays,
            onSelect = { viewModel.setTrashRetentionDays(it); dialog = null },
            onDismiss = { dialog = null },
        )
        SettingsDialog.TP_WINDOW -> TpWindowDialog(
            startMinutes = state.tpWindowStartMinutes,
            durationMinutes = state.tpWindowDurationMinutes,
            onSave = { start, duration -> viewModel.setTpWindow(start, duration); dialog = null },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }

    if (editingTime != -2) {
        val adding = editingTime !in state.digestTimes.indices
        val initial = if (adding) 9 to 0 else state.digestTimes[editingTime]
        key(editingTime) {
            TimeDialog(
                title = if (adding) "Add a reminder time" else "Change the reminder time",
                hour = initial.first,
                minute = initial.second,
                onSave = { hour, minute ->
                    if (adding) viewModel.addDigestTime(hour, minute)
                    else viewModel.updateDigestTime(editingTime, hour, minute)
                    editingTime = -2
                },
                onDismiss = { editingTime = -2 },
            )
        }
    }

    pendingImportUri?.let { uri ->
        ConfirmDialog(
            title = "Replace everything?",
            message = "Importing this backup replaces all routines, todos, lists, blocker rules, " +
                "focus sessions and history that are in the app now. Alarms are set again for " +
                "the imported data. What is here now is kept: \"Undo the last import\" in " +
                "Settings puts it back.",
            confirmLabel = "Import and replace",
            destructive = true,
            onConfirm = {
                viewModel.importBackup(uri)
                pendingImportUri = null
            },
            onDismiss = { pendingImportUri = null },
        )
    }

    if (confirmUndoImport) {
        com.focusforceplus.app.ui.components.ConfirmDialog(
            title = "Undo the last import?",
            message = "Everything goes back to how it was before the last import. What is in the app now is kept, so you can undo this too.",
            confirmLabel = "Undo import",
            dismissLabel = "Cancel",
            onConfirm = {
                confirmUndoImport = false
                viewModel.undoLastImport()
            },
            onDismiss = { confirmUndoImport = false },
        )
    }

    if (pickingWidget) {
        val widgetContext = androidx.compose.ui.platform.LocalContext.current
        com.focusforceplus.app.ui.components.ChoiceDialog(
            title = "Add a widget",
            choices = listOf(
                com.focusforceplus.app.ui.components.Choice(
                    com.focusforceplus.app.widget.TodayWidgetReceiver::class.java as Class<*>, "Today", "What is left today, by time"),
                com.focusforceplus.app.ui.components.Choice(
                    com.focusforceplus.app.widget.FocusWidgetReceiver::class.java as Class<*>, "Quick focus", "Start a session with one tap"),
                com.focusforceplus.app.ui.components.Choice(
                    com.focusforceplus.app.widget.ListWidgetReceiver::class.java as Class<*>, "A list", "To tick off from the home screen"),
            ),
            selected = null,
            onSelect = { receiver ->
                pickingWidget = false
                if (receiver != null && !com.focusforceplus.app.widget.pinWidget(widgetContext, receiver)) {
                    android.widget.Toast.makeText(
                        widgetContext,
                        "Your home screen does not take widgets from apps. Add it from the home screen's widget list.",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
            },
            onDismiss = { pickingWidget = false },
        )
    }

    if (confirmResetUsage) {
        ConfirmDialog(
            title = "Reset today's counters?",
            message = "Today's blocker minutes and used exceptions go back to zero.",
            confirmLabel = "Reset",
            onConfirm = {
                viewModel.resetUsageStatistics()
                confirmResetUsage = false
            },
            onDismiss = { confirmResetUsage = false },
        )
    }

    val nav = navConfig
    if (showNavEdit && nav != null) {
        NavEditSheet(
            config = nav,
            onMove = navViewModel::move,
            onSetShown = navViewModel::setShown,
            onSetModuleOn = navViewModel::setModuleOn,
            onReset = navViewModel::resetOrder,
            onDismiss = { showNavEdit = false },
        )
    }
}

// ── Rows ──────────────────────────────────────────────────────────────────────

@Composable
private fun LinkRow(title: String, subtitle: String?, onClick: () -> Unit) {
    ListRow(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        trailing = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = FfTheme.colors.textFaint,
            )
        },
    )
}

@Composable
private fun PermissionRow(
    title: String,
    granted: Boolean,
    /** Set when this phone offers no way to grant it: the row explains instead of asking. */
    unavailableNote: String? = null,
    onGrant: () -> Unit,
) {
    val colors = FfTheme.colors
    val unavailable = !granted && unavailableNote != null
    ListRow(
        title = title,
        subtitle = when {
            granted -> "Granted"
            unavailable -> unavailableNote
            else -> null
        },
        subtitleColor = if (granted) colors.success else colors.textMuted,
        subtitleMaxLines = 4,
        onClick = if (!granted && !unavailable) onGrant else null,
        trailing = {
            when {
                granted -> Icon(Icons.Filled.Check, contentDescription = "Granted", tint = colors.success)
                unavailable -> Unit
                else -> TextAction("Grant", onClick = onGrant)
            }
        },
    )
}

// ── Labels ────────────────────────────────────────────────────────────────────

private fun nagLabel(policy: NagPolicy): String = when (policy.maxRepeats) {
    0 -> "Once"
    1 -> "Once more after ${policy.intervalMinutes} min"
    else -> "Every ${policy.intervalMinutes} min, ${policy.maxRepeats}×"
}

private fun priorityLabel(priority: Int): String = when (priority) {
    0 -> "Low"
    2 -> "High"
    else -> "Medium"
}

private fun autoTrashLabel(days: Int): String = if (days <= 0) "Never" else "After $days days"

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

// ── Dialogs ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NagDialog(
    title: String,
    policy: NagPolicy,
    onSave: (NagPolicy) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FfTheme.colors
    var interval by remember { mutableIntStateOf(policy.intervalMinutes) }
    var repeats by remember { mutableIntStateOf(policy.maxRepeats) }
    val intervals = listOf(2, 5, 10, 15, 30, 60, 120).filter {
        it in ComplianceLimits.MIN_TODO_NAG_INTERVAL_MINUTES..ComplianceLimits.MAX_TODO_NAG_INTERVAL_MINUTES
    }
    val counts = listOf(0, 1, 2, 3, 6, 9, 12).filter { it <= ComplianceLimits.MAX_TODO_NAG_REPEATS }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceVariant,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = colors.text) },
        text = {
            Column {
                Text(
                    "If it is not answered, it rings again. After the last time it stays as a " +
                        "notification until your next daily reminder time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                )
                Spacer(Modifier.height(16.dp))
                Text("How often again", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    counts.forEach { n ->
                        FfChip(
                            label = if (n == 0) "Never" else "$n×",
                            selected = repeats == n,
                            onClick = { repeats = n },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Time in between", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    intervals.forEach { minutes ->
                        FfChip(
                            label = if (minutes < 60) "$minutes min" else "${minutes / 60} h",
                            selected = interval == minutes,
                            enabled = repeats > 0,
                            onClick = { interval = minutes },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(NagPolicy.clamp(NagPolicy(interval, repeats))) }) {
                Text("Save", style = MaterialTheme.typography.labelLarge, color = colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(
    title: String,
    hour: Int,
    minute: Int,
    onSave: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FfTheme.colors
    val time = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceVariant,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = colors.text) },
        text = {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                TimePicker(state = time, colors = pickerColors())
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(time.hour, time.minute) }) {
                Text("Save", style = MaterialTheme.typography.labelLarge, color = colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TpWindowDialog(
    startMinutes: Int,
    durationMinutes: Int,
    onSave: (start: Int, duration: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FfTheme.colors
    val time = rememberTimePickerState(
        initialHour = startMinutes / 60,
        initialMinute = startMinutes % 60,
        is24Hour = true,
    )
    var duration by remember { mutableIntStateOf(durationMinutes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceVariant,
        title = { Text("Daily window", style = MaterialTheme.typography.titleLarge, color = colors.text) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = time, colors = pickerColors())
                Text(
                    "How long it stays open",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    listOf(10, 20, 30, 45, 60).forEach { minutes ->
                        FfChip(label = "$minutes min", selected = duration == minutes, onClick = { duration = minutes })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(time.hour * 60 + time.minute, duration) }) {
                Text("Save", style = MaterialTheme.typography.labelLarge, color = colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun pickerColors() = FfTheme.colors.let { colors ->
    TimePickerDefaults.colors(
        clockDialColor = colors.surface,
        selectorColor = colors.primary,
        timeSelectorSelectedContainerColor = colors.container,
        timeSelectorSelectedContentColor = colors.onContainer,
        timeSelectorUnselectedContainerColor = colors.surface,
        timeSelectorUnselectedContentColor = colors.text,
        clockDialSelectedContentColor = colors.onPrimary,
        clockDialUnselectedContentColor = colors.textSecondary,
        containerColor = Color.Transparent,
    )
}
