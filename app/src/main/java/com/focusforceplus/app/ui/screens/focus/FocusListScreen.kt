package com.focusforceplus.app.ui.screens.focus

import com.focusforceplus.app.ui.theme.glossOverlay
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.focusforceplus.app.data.db.entity.FocusSessionEntity
import com.focusforceplus.app.ui.common.RoutineIcons
import com.focusforceplus.app.ui.common.routineDaysSummary
import com.focusforceplus.app.ui.components.DeleteDialog
import com.focusforceplus.app.ui.components.FfCard
import com.focusforceplus.app.ui.components.FfChip
import com.focusforceplus.app.ui.components.FfPaddedCard
import com.focusforceplus.app.ui.components.FfSwitch
import com.focusforceplus.app.ui.components.GroupLabel
import com.focusforceplus.app.ui.components.IconCircle
import com.focusforceplus.app.ui.components.ListRow
import com.focusforceplus.app.ui.components.PrimaryButton
import com.focusforceplus.app.ui.components.RowDivider
import com.focusforceplus.app.ui.components.TextAction
import com.focusforceplus.app.ui.theme.FfTheme
import com.focusforceplus.app.util.PermissionHelper
import com.focusforceplus.app.util.TimeFormat

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FocusListScreen(
    onCreateSession: () -> Unit,
    onEditSession: (Long) -> Unit,
    onOpenActive: (Long) -> Unit,
    viewModel: FocusListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val colors = FfTheme.colors
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var quickMinutes by remember { mutableIntStateOf(25) }
    var quickDnd by remember { mutableStateOf(true) }
    var menuFor by remember { mutableStateOf<FocusSessionEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<FocusSessionEntity?>(null) }

    // Do Not Disturb access can be granted in system settings; recheck on return.
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

    val active = state.activeSession

    Scaffold(
        containerColor = colors.screen,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateSession,
                modifier = Modifier.glossOverlay(MaterialTheme.shapes.large, colors.isAero),
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                shape = MaterialTheme.shapes.large,
            ) { Icon(Icons.Filled.Add, contentDescription = "New focus session") }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // ── Running now, or a quick start ─────────────────────────────────
            item(key = "now") {
                if (active != null) {
                    FfCard(onClick = { onOpenActive(active.sessionId) }) {
                        ListRow(
                            title = active.name,
                            subtitle = "Running · open to see the time left",
                            subtitleColor = colors.accent,
                            leading = { IconCircle(icon = Icons.Filled.SelfImprovement) },
                            onClick = { onOpenActive(active.sessionId) },
                            trailing = { TextAction("Open", onClick = { onOpenActive(active.sessionId) }) },
                        )
                    }
                } else {
                    FfPaddedCard {
                        Text("Quick focus", style = MaterialTheme.typography.titleMedium, color = colors.text)
                        Text(
                            "A one-off session with your blocker rules on. Nothing is saved.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(15, 25, 45, 60, 90).forEach { minutes ->
                                FfChip(
                                    label = "$minutes min",
                                    selected = quickMinutes == minutes,
                                    onClick = { quickMinutes = minutes },
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = state.dndAccessGranted) { quickDnd = !quickDnd }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Do Not Disturb", style = MaterialTheme.typography.bodyLarge, color = colors.text)
                                if (!state.dndAccessGranted) {
                                    Text(
                                        "Needs Do Not Disturb access",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textMuted,
                                    )
                                }
                            }
                            FfSwitch(
                                checked = quickDnd && state.dndAccessGranted,
                                onCheckedChange = null,
                                enabled = state.dndAccessGranted,
                            )
                        }
                        PrimaryButton(
                            text = "Start ${TimeFormat.duration(quickMinutes)}",
                            onClick = {
                                if (viewModel.quickStart(quickMinutes, quickDnd && state.dndAccessGranted)) {
                                    onOpenActive(0L)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            if (!state.dndAccessGranted) {
                item(key = "dnd") {
                    FfCard {
                        ListRow(
                            title = "Let focus silence your phone",
                            subtitle = "Do Not Disturb access lets sessions mute calls and notifications.",
                            subtitleMaxLines = 3,
                            onClick = { PermissionHelper.openDndAccessSettings(context) },
                            trailing = { TextAction("Allow", onClick = { PermissionHelper.openDndAccessSettings(context) }) },
                        )
                    }
                }
            }

            // ── Saved sessions ────────────────────────────────────────────────
            item(key = "label") {
                Spacer(Modifier.height(10.dp))
                GroupLabel("Your sessions")
            }
            if (state.sessions.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "Save a session you use often, like \"Deep work, 50 min\", and start it " +
                            "with one tap or on a schedule.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                }
            } else {
                item(key = "sessions") {
                    FfCard {
                        state.sessions.forEachIndexed { index, session ->
                            if (index > 0) RowDivider()
                            SessionRow(
                                session = session,
                                running = active?.sessionId == session.id,
                                onOpen = { onEditSession(session.id) },
                                onMenu = { menuFor = session },
                                onStart = {
                                    if (viewModel.startSession(session)) onOpenActive(session.id)
                                },
                            )
                        }
                    }
                }
                item(key = "hint") {
                    Text(
                        "Press and hold a session for more.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textFaint,
                        modifier = Modifier.padding(top = 8.dp, start = 2.dp),
                    )
                }
            }
        }
    }

    menuFor?.let { session ->
        ModalBottomSheet(
            onDismissRequest = { menuFor = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.surfaceVariant,
        ) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
                Text(
                    session.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                ListRow(title = "Start now", onClick = {
                    menuFor = null
                    if (viewModel.startSession(session)) onOpenActive(session.id)
                })
                ListRow(title = "Edit", onClick = { menuFor = null; onEditSession(session.id) })
                ListRow(title = "Duplicate", onClick = { menuFor = null; viewModel.duplicate(session) })
                if (session.scheduledDays != null) {
                    ListRow(
                        title = if (session.isActive) "Pause the schedule" else "Resume the schedule",
                        onClick = { menuFor = null; viewModel.toggleScheduleActive(session) },
                    )
                }
                ListRow(
                    title = "Delete",
                    titleColor = colors.error,
                    onClick = { menuFor = null; deleteTarget = session },
                )
            }
        }
    }

    deleteTarget?.let { session ->
        DeleteDialog(
            what = "\"${session.name}\"",
            onConfirm = {
                viewModel.deleteSession(session)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun SessionRow(
    session: FocusSessionEntity,
    running: Boolean,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    onStart: () -> Unit,
) {
    val colors = FfTheme.colors
    val meta = FocusTypes.of(session.type)
    val schedule = if (session.scheduledDays != null && session.scheduledTimeHour != null) {
        val time = TimeFormat.clock(session.scheduledTimeHour * 60 + (session.scheduledTimeMinute ?: 0))
        val days = routineDaysSummary(session.scheduledDays)
        if (session.isActive) "$days $time" else "paused"
    } else {
        null
    }
    val detail = when {
        running -> "Running now"
        else -> listOfNotNull(
            TimeFormat.duration(session.durationMinutes),
            meta.label.takeIf { meta != FocusTypes.CUSTOM },
            schedule,
            "invincible".takeIf { session.invincibleMode },
        ).joinToString(" · ")
    }
    ListRow(
        title = session.name,
        subtitle = detail,
        subtitleColor = if (running) colors.accent else colors.textMuted,
        onClick = onOpen,
        onLongClick = onMenu,
        leading = { IconCircle(icon = RoutineIcons.find(session.iconKey)?.vector ?: meta.icon) },
        trailing = {
            IconButton(onClick = onStart, enabled = !running) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Start ${session.name}",
                    tint = if (running) colors.textFaint else colors.accent,
                )
            }
        },
    )
}
