package com.focusforceplus.app.ui.screens.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.focusforceplus.app.data.db.entity.FocusSessionEntity
import com.focusforceplus.app.ui.common.FeatureHeader
import com.focusforceplus.app.ui.common.FeatureHelpContent
import com.focusforceplus.app.ui.common.routineDayLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusListScreen(
    onCreateSession: () -> Unit,
    onEditSession: (Long) -> Unit,
    onOpenActive: (Long) -> Unit,
    onOpenSleepSettings: () -> Unit = {},
    viewModel: FocusListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showQuickStart by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<FocusSessionEntity?>(null) }
    // Focus sessions and sleep mode share this tab: both are modes that restrict
    // the phone on purpose (DND + app blocking), just timed vs. scheduled.
    var selectedTab by remember { mutableIntStateOf(0) }

    // DND access can be granted in system settings — refresh the tip on return.
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
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = onCreateSession,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "New focus session")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                FeatureHeader(title = "Focus & Sleep", help = FeatureHelpContent.FOCUS)
            }

            item {
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Focus") },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Sleep") },
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            if (selectedTab == 1) {
                item {
                    SleepSummaryCard(
                        state = uiState,
                        onToggleEnabled = viewModel::toggleSleepEnabled,
                        onOpenSettings = onOpenSleepSettings,
                    )
                }
                item { Spacer(Modifier.height(72.dp)) }
                return@LazyColumn
            }

            // ── Running session banner ────────────────────────────────────────
            uiState.activeSession?.let { active ->
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenActive(active.sessionId) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "\"${active.name}\" is running",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Text(
                                    if (active.pausedRemainingSeconds != null) "Paused - tap to open"
                                    else "Tap to open the timer",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                )
                            }
                        }
                    }
                }
            }

            // ── Quick start ───────────────────────────────────────────────────
            item {
                FilledTonalButton(
                    onClick = { showQuickStart = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Quick start")
                }
            }

            if (!uiState.dndAccessGranted) {
                item {
                    Text(
                        "Tip: grant Do Not Disturb access in the session settings so focus " +
                            "sessions can silence interruptions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }

            // ── Saved sessions ────────────────────────────────────────────────
            if (uiState.sessions.isEmpty()) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "No focus sessions yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Tap + to create one, or use Quick start",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(uiState.sessions, key = { it.id }) { session ->
                    FocusSessionCard(
                        session = session,
                        isRunning = uiState.activeSession?.sessionId == session.id,
                        onEdit = { onEditSession(session.id) },
                        onStart = {
                            if (viewModel.startSession(session)) onOpenActive(session.id)
                        },
                        onDelete = { deleteTarget = session },
                        onToggleActive = { viewModel.toggleScheduleActive(session) },
                    )
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showQuickStart) {
        QuickStartDialog(
            onStart = { minutes, dnd ->
                showQuickStart = false
                if (viewModel.quickStart(minutes, dnd)) onOpenActive(0L)
            },
            onDismiss = { showQuickStart = false },
        )
    }

    deleteTarget?.let { session ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete session?") },
            text = { Text("\"${session.name}\" will be permanently deleted.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSession(session)
                        deleteTarget = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

// ── Session card ──────────────────────────────────────────────────────────────

@Composable
private fun FocusSessionCard(
    session: FocusSessionEntity,
    isRunning: Boolean,
    onEdit: () -> Unit,
    onStart: () -> Unit,
    onDelete: () -> Unit,
    onToggleActive: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            session.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        if (session.invincibleMode) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = "Invincible mode",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        TypeBadge(session.type)
                        Text(
                            "${session.durationMinutes} min",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (session.scheduledDays != null && session.scheduledTimeHour != null) {
                            Text(
                                session.scheduledDays.split(",")
                                    .joinToString(" ") { routineDayLabel(it) } +
                                    " · %02d:%02d".format(
                                        session.scheduledTimeHour,
                                        session.scheduledTimeMinute ?: 0,
                                    ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (isRunning) {
                    Text(
                        "Running",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete session",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(onClick = onStart) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "Start session",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    if (session.scheduledDays != null) {
                        Switch(
                            checked = session.isActive,
                            onCheckedChange = { onToggleActive() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun TypeBadge(type: String) {
    val meta = FocusTypes.of(type)
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = meta.color.copy(alpha = 0.18f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Icon(
                meta.icon,
                contentDescription = null,
                tint = meta.color,
                modifier = Modifier.size(11.dp),
            )
            Spacer(Modifier.width(3.dp))
            Text(
                meta.label,
                style = MaterialTheme.typography.labelSmall,
                color = meta.color,
            )
        }
    }
}

// ── Quick start dialog ────────────────────────────────────────────────────────

@Composable
private fun QuickStartDialog(
    onStart: (minutes: Int, dnd: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var minutes by remember { mutableStateOf(25) }
    var dnd by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick focus") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(15, 25, 45, 60, 90).forEach { m ->
                        FilledTonalButton(
                            onClick = { minutes = m },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 10.dp),
                            colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (minutes == m) MaterialTheme.colorScheme.primary
                                                 else MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = if (minutes == m) MaterialTheme.colorScheme.onPrimary
                                               else MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        ) { Text("${m}m", style = MaterialTheme.typography.labelMedium) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Enable Do Not Disturb",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = dnd,
                        onCheckedChange = { dnd = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onStart(minutes, dnd) }) { Text("Start") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ── Sleep summary (Sleep tab) ─────────────────────────────────────────────────

/**
 * Compact sleep-mode overview inside the shared tab: on/off, the window, what it
 * does tonight, and a way into the full editor. Deliberately not a second copy of
 * SleepScreen — everything editable lives there.
 */
@Composable
private fun SleepSummaryCard(
    state: FocusListUiState,
    onToggleEnabled: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val sleep = state.sleep
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (state.sleepWindowActive) MaterialTheme.colorScheme.primaryContainer
                             else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Bedtime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Sleep mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        when {
                            state.sleepWindowActive -> "Running until ${formatClock(sleep.wakeMinutes)}"
                            sleep.enabled -> "Tonight ${formatClock(sleep.bedtimeMinutes)} - " +
                                formatClock(sleep.wakeMinutes)
                            else -> "Off"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = sleep.enabled,
                    onCheckedChange = { onToggleEnabled() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }

            if (sleep.enabled) {
                val details = buildList {
                    if (sleep.windDownMinutes > 0) add("${sleep.windDownMinutes} min wind-down")
                    if (sleep.dndEnabled) add("Do Not Disturb")
                    if (sleep.blockedGroups.isNotEmpty()) {
                        add("blocks ${sleep.blockedGroups.joinToString(", ")}")
                    }
                    if (sleep.nativeAlarmCount > 0) {
                        add("${sleep.nativeAlarmCount} backup alarm(s)")
                    }
                }
                if (details.isNotEmpty()) {
                    Text(
                        details.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                val bedtimeRoutine = state.routineName(sleep.bedtimeRoutineId)
                val morningRoutine = state.routineName(sleep.morningRoutineId)
                if (bedtimeRoutine != null || morningRoutine != null) {
                    Text(
                        listOfNotNull(
                            bedtimeRoutine?.let { "Bedtime routine: $it" },
                            morningRoutine?.let { "Morning routine: $it" },
                        ).joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (state.sleepWindowActive && sleep.invincibleMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Locked until wake-up",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            FilledTonalButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(if (sleep.enabled) "Edit sleep schedule" else "Set up sleep mode")
            }
        }
    }
}

private fun formatClock(minutes: Int): String =
    "%02d:%02d".format(minutes / 60, minutes % 60)
