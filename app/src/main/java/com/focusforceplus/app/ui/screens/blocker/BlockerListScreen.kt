package com.focusforceplus.app.ui.screens.blocker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.focusforceplus.app.compliance.Guard
import com.focusforceplus.app.compliance.GuardSlot
import com.focusforceplus.app.ui.common.RoutineIcons
import com.focusforceplus.app.ui.common.routineDaysSummary
import com.focusforceplus.app.ui.components.FfCard
import com.focusforceplus.app.ui.components.FfSwitch
import com.focusforceplus.app.ui.components.IconCircle
import com.focusforceplus.app.ui.components.ListRow
import com.focusforceplus.app.ui.components.RowDivider
import com.focusforceplus.app.ui.components.SectionHeader
import com.focusforceplus.app.ui.components.TextAction
import com.focusforceplus.app.ui.guards.GuardCopy
import com.focusforceplus.app.ui.guards.GuardDialog
import com.focusforceplus.app.ui.theme.FfTheme

/**
 * The blocker, built around blocks: one switch for all of it, what is blocked
 * right now and why, and the blocks themselves (a group of apps with its rules,
 * or a single app's own rule). Editing a block opens a full screen.
 */
@Composable
fun BlockerListScreen(
    onOpenSettings: () -> Unit,
    onOpenDisclosure: (String) -> Unit,
    onNewBlock: () -> Unit = {},
    onEditBlock: (String) -> Unit = {},
    viewModel: BlockerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val guards by viewModel.guards.collectAsState()
    val shorts by viewModel.shortVideos.collectAsState()
    val colors = FfTheme.colors
    val snackbar = remember { SnackbarHostState() }
    var editingApp by remember { mutableStateOf<BlockerAppRow?>(null) }
    // What waits behind the pause speed bump: null, the whole blocker, or a block.
    var pausing by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Permissions change in system settings; recheck on every return.
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

    val pauseGuard: Guard = guards[GuardSlot.BLOCKER_PAUSE]

    // Switching short videos off again counts as pausing a block. Switching them on
    // without the service first leads to its disclosure.
    fun shortsToggle(on: Boolean, apply: (Boolean) -> Unit) {
        when {
            on && !shorts.serviceOn -> {
                apply(true)
                onOpenDisclosure("shorts")
            }
            on -> apply(true)
            else -> if (pauseGuard.isNone) apply(false) else pausing = { apply(false) }
        }
    }
    fun pause(action: () -> Unit) {
        if (pauseGuard.isNone) action() else pausing = action
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewBlock,
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                shape = MaterialTheme.shapes.large,
            ) { Icon(Icons.Filled.Add, contentDescription = "New block") }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ── The switch for all of it ──────────────────────────────────────
            item(key = "status") {
                FfCard {
                    ListRow(
                        title = if (state.blockerEnabled) "Blocking is on" else "Blocking is paused",
                        subtitle = when {
                            !state.accessibilityEnabled -> "Needs the blocker service, see below"
                            !state.blockerEnabled -> "Nothing is blocked until you switch it on"
                            state.blockedNow.isEmpty() -> "Nothing is blocked right now"
                            state.blockedNow.size == 1 -> "1 app is blocked right now"
                            else -> "${state.blockedNow.size} apps are blocked right now"
                        },
                        onClick = {
                            if (state.blockerEnabled) pause { viewModel.setBlockerEnabled(false) }
                            else viewModel.setBlockerEnabled(true)
                        },
                        trailing = { FfSwitch(checked = state.blockerEnabled, onCheckedChange = null) },
                    )
                }
            }

            // ── What it still needs ───────────────────────────────────────────
            if (!state.accessibilityEnabled) {
                item(key = "a11y") {
                    FfCard {
                        ListRow(
                            title = "Turn on the blocker service",
                            subtitle = "Without it nothing can be blocked. It only notices which app " +
                                "is opened and reads nothing on the screen.",
                            subtitleMaxLines = 3,
                            onClick = { onOpenDisclosure("accessibility") },
                            trailing = { TextAction("Set up", onClick = { onOpenDisclosure("accessibility") }) },
                        )
                    }
                }
            }
            val usesLimits = state.blocks.any { item ->
                when (item) {
                    is BlockItem.Group -> item.row.group.sharedDailyLimitMinutes != null
                    is BlockItem.App -> item.rule.dailyLimitMinutes != null
                }
            }
            if (usesLimits && !state.usageAccessGranted) {
                item(key = "usage") {
                    FfCard {
                        ListRow(
                            title = "Allow usage access",
                            subtitle = "Daily limits count your app time with it.",
                            onClick = { onOpenDisclosure("usage") },
                            trailing = { TextAction("Allow", onClick = { onOpenDisclosure("usage") }) },
                        )
                    }
                }
            }

            // ── Blocked right now ─────────────────────────────────────────────
            if (state.blockerEnabled && state.blockedNow.isNotEmpty()) {
                item(key = "now") {
                    Column {
                        SectionHeader("Blocked right now")
                        FfCard {
                            state.blockedNow.forEachIndexed { index, blocked ->
                                if (index > 0) RowDivider()
                                ListRow(
                                    title = blocked.appName,
                                    subtitle = blocked.why,
                                    leading = {
                                        InstalledAppIcon(
                                            blocked.packageName,
                                            blocked.appName,
                                            loadIcon = { viewModel.loadIcon(blocked.packageName) },
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // ── Your blocks ───────────────────────────────────────────────────
            item(key = "blocks-header") {
                SectionHeader("Your blocks", actionLabel = "New block", onAction = onNewBlock)
            }
            if (state.blocks.isEmpty()) {
                item(key = "blocks-empty") {
                    FfCard {
                        ListRow(
                            title = "No blocks yet",
                            subtitle = "A block is a few apps with a daily limit, a schedule, or both. " +
                                "Social media is a good first one.",
                            subtitleMaxLines = 3,
                            onClick = onNewBlock,
                            leading = { IconCircle(icon = Icons.Filled.Block) },
                        )
                    }
                }
            }
            state.blocks.forEach { block ->
                item(key = block.key) {
                    when (block) {
                        is BlockItem.Group -> GroupBlockCard(
                            block = block,
                            onOpen = { onEditBlock(block.row.group.name) },
                            onToggle = {
                                if (block.active) pause { viewModel.setGroupBlocked(block.row.group.name, false) }
                                else viewModel.setGroupBlocked(block.row.group.name, true)
                            },
                        )
                        is BlockItem.App -> AppBlockCard(
                            block = block,
                            loadIcon = { viewModel.loadIcon(block.rule.packageName) },
                            onOpen = { editingApp = BlockerAppRow(block.rule.packageName, block.rule.appName, block.rule) },
                            onToggle = {
                                val row = BlockerAppRow(block.rule.packageName, block.rule.appName, block.rule)
                                if (block.rule.isBlocked) pause { viewModel.toggleBlock(row) } else viewModel.toggleBlock(row)
                            },
                        )
                    }
                }
            }

            // ── Short videos ──────────────────────────────────────────────────
            item(key = "shorts") {
                Column {
                    SectionHeader("Short videos")
                    FfCard {
                        if (!shorts.serviceOn) {
                            ListRow(
                                title = "Keep the app, skip the scrolling",
                                subtitle = "Leaves Reels and Shorts when you open them; the rest of " +
                                    "Instagram and YouTube keeps working. Needs its own service.",
                                subtitleMaxLines = 3,
                                onClick = { onOpenDisclosure("shorts") },
                                trailing = { TextAction("Set up", onClick = { onOpenDisclosure("shorts") }) },
                            )
                            RowDivider()
                        }
                        ListRow(
                            title = "Instagram Reels",
                            subtitle = if (shorts.instagram) "Off" else "Allowed",
                            onClick = { shortsToggle(!shorts.instagram) { viewModel.setShortsBlock(instagram = it) } },
                            trailing = {
                                FfSwitch(checked = shorts.instagram, onCheckedChange = { on ->
                                    shortsToggle(on) { viewModel.setShortsBlock(instagram = it) }
                                })
                            },
                        )
                        RowDivider()
                        ListRow(
                            title = "YouTube Shorts",
                            subtitle = if (shorts.youtube) "Off" else "Allowed",
                            onClick = { shortsToggle(!shorts.youtube) { viewModel.setShortsBlock(youtube = it) } },
                            trailing = {
                                FfSwitch(checked = shorts.youtube, onCheckedChange = { on ->
                                    shortsToggle(on) { viewModel.setShortsBlock(youtube = it) }
                                })
                            },
                        )
                    }
                }
            }

            item(key = "settings") {
                TextAction(
                    "Blocker settings",
                    onClick = onOpenSettings,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
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

    editingApp?.let { row ->
        AppRuleConfigDialog(
            row = row,
            existingGroups = state.groupNames,
            onSave = { rule ->
                editingApp = null
                // Saving a block as switched off is a pause like the switch in the list.
                if (row.rule?.isBlocked == true && !rule.isBlocked) pause { viewModel.saveRule(rule) } else viewModel.saveRule(rule)
            },
            onDelete = { rule ->
                viewModel.deleteRule(rule)
                editingApp = null
            },
            onDismiss = { editingApp = null },
        )
    }
}

@Composable
private fun GroupBlockCard(block: BlockItem.Group, onOpen: () -> Unit, onToggle: () -> Unit) {
    val colors = FfTheme.colors
    val group = block.row.group
    val limit = group.sharedDailyLimitMinutes
    val parts = buildList {
        add(if (block.members.size == 1) "1 app" else "${block.members.size} apps")
        if (limit != null) add("${block.row.usedTodayMinutes} of $limit min")
        if (group.windowStartMinutes != null && group.windowEndMinutes != null) {
            val days = group.windowDaysCsv?.takeIf { it.isNotBlank() }?.let { routineDaysSummary(it) } ?: "Every day"
            add("$days ${formatMinutes(group.windowStartMinutes)} to ${formatMinutes(group.windowEndMinutes)}")
        }
        if (group.alwaysBlock) add("always")
        if (block.row.locked) add("locked") else if (group.invincibleMode) add("invincible")
    }
    FfCard {
        ListRow(
            title = group.name,
            subtitle = parts.joinToString(" · "),
            subtitleMaxLines = 3,
            onClick = onOpen,
            leading = { IconCircle(icon = RoutineIcons.find(group.iconKey)?.vector ?: Icons.Filled.Block) },
            trailing = { FfSwitch(checked = block.active, onCheckedChange = { onToggle() }) },
        )
        if (limit != null && limit > 0 && block.active) {
            val fraction = (block.row.usedTodayMinutes.toFloat() / limit).coerceIn(0f, 1f)
            Box(
                Modifier
                    .padding(start = 66.dp, end = 16.dp, bottom = 12.dp)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.surfaceHigh),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .background(if (fraction >= 1f) colors.error else colors.accent),
                )
            }
        }
    }
}

@Composable
private fun AppBlockCard(
    block: BlockItem.App,
    loadIcon: suspend () -> android.graphics.drawable.Drawable?,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
) {
    val rule = block.rule
    val parts = buildList {
        rule.dailyLimitMinutes?.let { add("${rule.usedTodayMinutes} of $it min") }
        if (rule.windowStartMinutes != null && rule.windowEndMinutes != null) {
            add("${formatMinutes(rule.windowStartMinutes)} to ${formatMinutes(rule.windowEndMinutes)}")
        }
        if (isEmpty()) add("Always")
        if (rule.invincibleMode) add("invincible")
    }
    FfCard {
        ListRow(
            title = rule.appName,
            subtitle = parts.joinToString(" · "),
            onClick = onOpen,
            leading = { InstalledAppIcon(rule.packageName, rule.appName, loadIcon = loadIcon) },
            trailing = { FfSwitch(checked = rule.isBlocked, onCheckedChange = { onToggle() }) },
        )
    }
}
