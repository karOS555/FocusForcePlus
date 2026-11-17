package com.focusforceplus.app.ui.screens.blocker

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.focusforceplus.app.compliance.DAY_CODES
import com.focusforceplus.app.ui.common.DiscardChangesDialog
import com.focusforceplus.app.ui.common.IconPickerButton
import com.focusforceplus.app.ui.components.ConfirmDialog
import com.focusforceplus.app.ui.components.FfCard
import com.focusforceplus.app.ui.components.FfChip
import com.focusforceplus.app.ui.components.FfPaddedCard
import com.focusforceplus.app.ui.components.FfTextField
import com.focusforceplus.app.ui.components.FfTimePickerDialog
import com.focusforceplus.app.ui.components.FormScaffold
import com.focusforceplus.app.ui.components.FormSection
import com.focusforceplus.app.ui.components.ListRow
import com.focusforceplus.app.ui.components.RowDivider
import com.focusforceplus.app.ui.components.SwitchRow
import com.focusforceplus.app.ui.components.ValueRow
import com.focusforceplus.app.ui.theme.FfTheme
import com.focusforceplus.app.util.GroupTemplates

private val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
internal val LIMIT_CHOICES = listOf(15, 30, 45, 60, 90, 120, 180)

/**
 * One block, on a full screen: its apps, and when they are blocked. Any mix of
 * "always", a daily limit shared by all its apps, and a schedule.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlockEditorScreen(
    groupName: String?,
    onBack: () -> Unit,
    viewModel: BlockerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val colors = FfTheme.colors
    val snackbar = remember { SnackbarHostState() }
    val existing = state.blocks.filterIsInstance<BlockItem.Group>().firstOrNull { it.row.group.name == groupName }

    var loaded by remember { mutableStateOf(groupName == null) }
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf<String?>(null) }
    var members by remember { mutableStateOf(setOf<String>()) }
    var memberNames by remember { mutableStateOf(mapOf<String, String>()) }
    var always by remember { mutableStateOf(false) }
    var limitOn by remember { mutableStateOf(false) }
    var limit by remember { mutableIntStateOf(60) }
    var scheduleOn by remember { mutableStateOf(false) }
    var start by remember { mutableIntStateOf(9 * 60) }
    var end by remember { mutableIntStateOf(17 * 60) }
    var days by remember { mutableStateOf(DAY_CODES.toSet()) }
    var duringRoutines by remember { mutableStateOf(true) }
    var duringFocus by remember { mutableStateOf(true) }
    var invincible by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }

    var picking by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf<Boolean?>(null) } // true = start, false = end
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    // Fill the form once the stored block has loaded.
    LaunchedEffect(existing) {
        if (!loaded && existing != null) {
            val g = existing.row.group
            name = g.name
            icon = g.iconKey
            members = existing.members.map { it.packageName }.toSet()
            memberNames = existing.members.associate { it.packageName to it.appName }
            always = g.alwaysBlock
            limitOn = g.sharedDailyLimitMinutes != null
            limit = g.sharedDailyLimitMinutes ?: 60
            scheduleOn = g.windowStartMinutes != null && g.windowEndMinutes != null
            start = g.windowStartMinutes ?: start
            end = g.windowEndMinutes ?: end
            days = g.windowDaysCsv?.split(',')?.filter { it.isNotBlank() }?.toSet()?.ifEmpty { null } ?: DAY_CODES.toSet()
            duringRoutines = g.blockDuringRoutines ?: true
            duringFocus = g.blockDuringFocus ?: true
            invincible = g.invincibleMode
            loaded = true
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val attemptClose = { if (dirty) confirmDiscard = true else onBack() }
    BackHandler { attemptClose() }
    fun edit(change: () -> Unit) {
        change()
        dirty = true
    }

    val canBeInvincible = limitOn || scheduleOn
    val installed = state.rows.map { it.packageName to it.appName }

    FormScaffold(
        title = if (groupName == null) "New block" else "Edit block",
        onClose = attemptClose,
        primaryLabel = if (groupName == null) "Save block" else "Save changes",
        onPrimary = {
            viewModel.saveGroup(
                originalName = groupName,
                form = GroupEditorResult(
                    name = name,
                    limitMinutes = if (limitOn) limit else null,
                    invincible = invincible && canBeInvincible,
                    windowStart = if (scheduleOn) start else null,
                    windowEnd = if (scheduleOn) end else null,
                    windowDaysCsv = if (scheduleOn && days.size < 7) DAY_CODES.filter { it in days }.joinToString(",") else null,
                    blockDuringRoutines = duringRoutines,
                    blockDuringFocus = duringFocus,
                    members = members,
                    memberDisplayNames = memberNames,
                    iconKey = icon,
                    alwaysBlock = always,
                ),
                onSaved = onBack,
            )
        },
        primaryEnabled = name.isNotBlank() && members.isNotEmpty(),
        snackbarHostState = snackbar,
        actions = {
            if (groupName != null) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete block")
                }
            }
        },
    ) {
        if (existing?.row?.locked == true) {
            FfPaddedCard(color = colors.container) {
                Text(
                    "This block is locked by invincible mode until its limit resets or its " +
                        "schedule ends. Making it weaker needs your Tamper Protection window.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onContainer,
                )
            }
        }

        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconPickerButton(selectedKey = icon, size = 56.dp, onSelect = { edit { icon = it } })
            FfTextField(
                value = name,
                onValueChange = { edit { name = it } },
                label = "Name, like \"Social media\"",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.weight(1f),
            )
        }

        // ── Apps ──────────────────────────────────────────────────────────────
        FormSection(title = "Apps") {
            if (groupName == null && members.isEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GroupTemplates.ALL.forEach { template ->
                        FfChip(
                            label = template.name,
                            selected = false,
                            onClick = {
                                val matched = state.rows.filter { row ->
                                    template.packagePrefixes.any { row.packageName.startsWith(it) }
                                }
                                edit {
                                    if (name.isBlank()) name = template.name
                                    members = members + matched.map { it.packageName }
                                    memberNames = memberNames + matched.associate { it.packageName to it.appName }
                                }
                            },
                        )
                    }
                }
            }
            FfCard {
                members.sortedBy { memberNames[it]?.lowercase() ?: it }.forEachIndexed { index, pkg ->
                    if (index > 0) RowDivider()
                    val appName = memberNames[pkg] ?: pkg
                    ListRow(
                        title = appName,
                        leading = { InstalledAppIcon(pkg, appName, loadIcon = { viewModel.loadIcon(pkg) }) },
                        trailing = {
                            IconButton(onClick = { edit { members = members - pkg } }) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove $appName", tint = colors.textMuted)
                            }
                        },
                    )
                }
                if (members.isNotEmpty()) RowDivider()
                ListRow(
                    title = if (members.isEmpty()) "Choose apps" else "Add or remove apps",
                    titleColor = colors.accent,
                    onClick = { picking = true },
                    trailing = { Icon(Icons.Filled.Add, contentDescription = null, tint = colors.accent) },
                )
            }
        }

        // ── When ──────────────────────────────────────────────────────────────
        FormSection(
            title = "When they are blocked",
            hint = "Any mix works. With none of them, the block only applies during routines " +
                "and focus sessions.",
        ) {
            FfCard {
                SwitchRow(
                    title = "Always",
                    subtitle = "Blocked all day, every day.",
                    checked = always,
                    onCheckedChange = { edit { always = it } },
                )
                RowDivider()
                SwitchRow(
                    title = "Daily limit",
                    subtitle = "Time shared by all these apps, reset at midnight.",
                    checked = limitOn,
                    onCheckedChange = { edit { limitOn = it } },
                )
                if (limitOn) {
                    FlowRow(
                        modifier = Modifier.padding(horizontal = 14.dp).padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LIMIT_CHOICES.forEach { minutes ->
                            FfChip(
                                label = if (minutes < 60 || minutes % 60 != 0) "$minutes min" else "${minutes / 60} h",
                                selected = limit == minutes,
                                onClick = { edit { limit = minutes } },
                            )
                        }
                    }
                }
                RowDivider()
                SwitchRow(
                    title = "Schedule",
                    subtitle = "Blocked between two times, on the days you pick.",
                    checked = scheduleOn,
                    onCheckedChange = { edit { scheduleOn = it } },
                )
                if (scheduleOn) {
                    ValueRow(title = "From", value = formatMinutes(start), onClick = { pickTime = true })
                    ValueRow(title = "Until", value = formatMinutes(end), onClick = { pickTime = false })
                    FlowRow(
                        modifier = Modifier.padding(horizontal = 14.dp).padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        DAY_CODES.forEachIndexed { i, code ->
                            FfChip(
                                label = DAY_LABELS[i],
                                selected = code in days,
                                onClick = {
                                    edit { days = if (code in days && days.size > 1) days - code else days + code }
                                },
                            )
                        }
                    }
                }
            }
        }

        // ── Also ──────────────────────────────────────────────────────────────
        FormSection(title = "Also") {
            FfCard {
                SwitchRow(
                    title = "During routines",
                    subtitle = "While a routine with app blocking runs.",
                    checked = duringRoutines,
                    onCheckedChange = { edit { duringRoutines = it } },
                )
                RowDivider()
                SwitchRow(
                    title = "During focus sessions",
                    subtitle = "While a focus session that blocks apps runs.",
                    checked = duringFocus,
                    onCheckedChange = { edit { duringFocus = it } },
                )
                RowDivider()
                SwitchRow(
                    title = "Invincible mode",
                    subtitle = if (canBeInvincible) {
                        "While the limit is used up or the schedule runs, it cannot be switched off."
                    } else {
                        "Needs a daily limit or a schedule, so it always has an end."
                    },
                    checked = invincible && canBeInvincible,
                    enabled = canBeInvincible,
                    onCheckedChange = { edit { invincible = it } },
                )
            }
        }
    }

    if (picking) {
        AppPickerDialog(
            apps = installed,
            initiallySelected = members,
            loadIcon = { viewModel.loadIcon(it) },
            onDone = { chosen, names ->
                edit {
                    members = chosen
                    memberNames = memberNames + names
                }
                picking = false
            },
            onDismiss = { picking = false },
        )
    }

    pickTime?.let { isStart ->
        FfTimePickerDialog(
            title = if (isStart) "Blocked from" else "Blocked until",
            hour = (if (isStart) start else end) / 60,
            minute = (if (isStart) start else end) % 60,
            onConfirm = { h, m ->
                edit { if (isStart) start = h * 60 + m else end = h * 60 + m }
                pickTime = null
            },
            onDismiss = { pickTime = null },
        )
    }

    if (confirmDelete && existing != null) {
        ConfirmDialog(
            title = "Delete this block?",
            message = "Its apps are no longer blocked by it. This cannot be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                viewModel.deleteGroup(existing.row, onDeleted = onBack)
            },
            onDismiss = { confirmDelete = false },
        )
    }

    if (confirmDiscard) {
        DiscardChangesDialog(
            onDiscard = { confirmDiscard = false; onBack() },
            onKeepEditing = { confirmDiscard = false },
        )
    }
}
