package com.focusforceplus.app.ui.screens.blocker

import android.graphics.drawable.Drawable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.focusforceplus.app.data.db.entity.BlockedAppEntity
import com.focusforceplus.app.ui.common.FeatureHelpAction
import com.focusforceplus.app.ui.common.FeatureHelpContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockerListScreen(
    onOpenSettings: () -> Unit,
    onOpenDisclosure: (String) -> Unit,
    viewModel: BlockerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var configRow by remember { mutableStateOf<BlockerAppRow?>(null) }
    // Group editor: holds the row being edited, or a "new group" sentinel.
    var groupEditorFor by remember { mutableStateOf<GroupEditorTarget?>(null) }
    var editorInstalledApps by remember { mutableStateOf<List<com.focusforceplus.app.util.InstalledApp>>(emptyList()) }
    var editorMembers by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Permission states can change in system settings — refresh whenever we return.
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
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── Header row ─────────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "App Blocker",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            if (uiState.blockerEnabled) "Blocking is on" else "Blocking is paused",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.blockerEnabled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!uiState.blockerEnabled) {
                        TextButton(onClick = viewModel::enableBlocking) { Text("Turn on") }
                    }
                    FeatureHelpAction(FeatureHelpContent.BLOCKER)
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Blocker settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── Permission setup cards ────────────────────────────────────────
            if (!uiState.accessibilityEnabled) {
                item {
                    PermissionSetupCard(
                        title = "Enable the blocking service",
                        text = "Blocking needs the accessibility service so FocusForce+ can " +
                            "see which app you open. It reads no screen content.",
                        buttonLabel = "Set up",
                        onClick = { onOpenDisclosure(DisclosureType.ACCESSIBILITY) },
                    )
                }
            }
            if (!uiState.usageAccessGranted) {
                item {
                    PermissionSetupCard(
                        title = "Allow usage access for daily limits",
                        text = "Daily time limits need usage access to measure how long you " +
                            "used each app today. Without it, only manual and window blocking work.",
                        buttonLabel = "Set up",
                        onClick = { onOpenDisclosure(DisclosureType.USAGE_ACCESS) },
                    )
                }
            }

            // ── Search ────────────────────────────────────────────────────────
            item {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    placeholder = { Text("Search apps") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotBlank()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ── Groups section (prominent) ─────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Groups",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        scope.launch {
                            editorInstalledApps = viewModel.installedAppsSnapshot()
                            editorMembers = emptySet()
                            groupEditorFor = GroupEditorTarget.New
                        }
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New group")
                    }
                }
            }
            if (uiState.groups.isEmpty()) {
                item {
                    Text(
                        "Bundle apps (like all your social media) into a group with one shared " +
                            "daily limit, and select whole groups to block in focus sessions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            } else {
                items(uiState.groups, key = { it.group.id }) { groupRow ->
                    GroupCard(
                        groupRow = groupRow,
                        selected = uiState.selectedGroup == groupRow.group.name,
                        onFilter = {
                            viewModel.selectGroup(
                                if (uiState.selectedGroup == groupRow.group.name) null else groupRow.group.name
                            )
                        },
                        onEdit = {
                            scope.launch {
                                editorInstalledApps = viewModel.installedAppsSnapshot()
                                editorMembers = uiState.rows
                                    .filter { it.rule?.groupName == groupRow.group.name }
                                    .map { it.packageName }.toSet()
                                groupEditorFor = GroupEditorTarget.Existing(groupRow)
                            }
                        },
                        onBlockAll = { viewModel.setGroupBlocked(groupRow.group.name, true) },
                        onUnblockAll = { viewModel.setGroupBlocked(groupRow.group.name, false) },
                    )
                }
            }

            // ── App list ──────────────────────────────────────────────────────
            if (uiState.isLoadingApps) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }
            } else {
                items(uiState.rows, key = { it.packageName }) { row ->
                    BlockerAppRowItem(
                        row = row,
                        loadIcon = { viewModel.loadIcon(row.packageName) },
                        onToggle = { viewModel.toggleBlock(row) },
                        onClick = { configRow = row },
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    configRow?.let { row ->
        AppRuleConfigDialog(
            row = row,
            existingGroups = uiState.groupNames,
            onSave = { rule ->
                viewModel.saveRule(rule)
                configRow = null
            },
            onDelete = { rule ->
                viewModel.deleteRule(rule)
                configRow = null
            },
            onDismiss = { configRow = null },
        )
    }

    groupEditorFor?.let { target ->
        val existing = (target as? GroupEditorTarget.Existing)?.row
        GroupEditorDialog(
            existing = existing,
            installedApps = editorInstalledApps,
            currentMemberPackages = editorMembers,
            onSave = { name, limit, invincible, members, names ->
                viewModel.saveGroup(existing?.group?.name, name, limit, invincible, members, names)
                groupEditorFor = null
            },
            onDelete = existing?.let {
                {
                    viewModel.deleteGroup(it)
                    groupEditorFor = null
                }
            },
            onDismiss = { groupEditorFor = null },
        )
    }
}

/** What the group editor is editing. */
private sealed interface GroupEditorTarget {
    data object New : GroupEditorTarget
    data class Existing(val row: BlockerGroupRow) : GroupEditorTarget
}

@Composable
private fun GroupCard(
    groupRow: BlockerGroupRow,
    selected: Boolean,
    onFilter: () -> Unit,
    onEdit: () -> Unit,
    onBlockAll: () -> Unit,
    onUnblockAll: () -> Unit,
) {
    val group = groupRow.group
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                             else MaterialTheme.colorScheme.surface,
        ),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            group.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        if (group.invincibleMode) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = "Invincible",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                    val limitText = group.sharedDailyLimitMinutes?.let { limit ->
                        "${groupRow.usedTodayMinutes}/${limit} min today · ${groupRow.memberCount} apps"
                    } ?: "${groupRow.memberCount} apps · no shared limit"
                    Text(
                        limitText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (groupRow.locked) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onEdit) { Text("Edit") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = selected,
                    onClick = onFilter,
                    label = { Text(if (selected) "Showing this group" else "Show only") },
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onBlockAll) { Text("Block all") }
                TextButton(onClick = onUnblockAll) { Text("Unblock") }
            }
        }
    }
}

// ── Row ───────────────────────────────────────────────────────────────────────

@Composable
private fun BlockerAppRowItem(
    row: BlockerAppRow,
    loadIcon: suspend () -> Drawable?,
    onToggle: () -> Unit,
    onClick: () -> Unit,
) {
    val borderColor = if (row.isBlocked) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(if (row.isBlocked) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .background(
                if (row.isBlocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                else Color.Transparent,
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName = row.packageName, loadIcon = loadIcon)

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = row.appName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (row.rule?.invincibleMode == true) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = "Invincible mode",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
            val subtitle = ruleSummary(row.rule)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Switch(
            checked = row.isBlocked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
}

private fun ruleSummary(rule: BlockedAppEntity?): String? {
    if (rule == null || !rule.isBlocked) return null
    val parts = mutableListOf<String>()
    rule.dailyLimitMinutes?.let { parts += "${rule.usedTodayMinutes}/$it min today" }
    if (rule.windowStartMinutes != null && rule.windowEndMinutes != null) {
        parts += "blocked ${formatMinutes(rule.windowStartMinutes)}-${formatMinutes(rule.windowEndMinutes)}"
    }
    if (parts.isEmpty()) parts += "always blocked"
    rule.groupName?.let { parts += it }
    return parts.joinToString(" · ")
}

internal fun formatMinutes(minutesOfDay: Int): String =
    "%02d:%02d".format(minutesOfDay / 60, minutesOfDay % 60)

@Composable
internal fun AppIcon(packageName: String, loadIcon: suspend () -> Drawable?, size: Int = 40) {
    val drawable by produceState<Drawable?>(initialValue = null, packageName) {
        value = loadIcon()
    }
    val bitmap = drawable?.toBitmap(96, 96)
    if (bitmap != null) {
        Icon(
            painter = BitmapPainter(bitmap.asImageBitmap()),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(size.dp),
        )
    } else {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
            Icon(
                Icons.Filled.Android,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(size.dp)
                    .padding(8.dp),
            )
        }
    }
}

// ── Permission setup card ─────────────────────────────────────────────────────

@Composable
private fun PermissionSetupCard(
    title: String,
    text: String,
    buttonLabel: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onClick) { Text(buttonLabel) }
        }
    }
}
