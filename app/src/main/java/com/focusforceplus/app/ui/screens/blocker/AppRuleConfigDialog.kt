package com.focusforceplus.app.ui.screens.blocker

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.focusforceplus.app.compliance.isRuleLocked
import com.focusforceplus.app.data.db.entity.BlockedAppEntity
import com.focusforceplus.app.ui.common.DiscardChangesDialog
import com.focusforceplus.app.ui.components.ConfirmDialog
import com.focusforceplus.app.ui.components.FfCard
import com.focusforceplus.app.ui.components.FfChip
import com.focusforceplus.app.ui.components.FfPaddedCard
import com.focusforceplus.app.ui.components.FfTimePickerDialog
import com.focusforceplus.app.ui.components.FormScaffold
import com.focusforceplus.app.ui.components.FormSection
import com.focusforceplus.app.ui.components.RowDivider
import com.focusforceplus.app.ui.components.SwitchRow
import com.focusforceplus.app.ui.components.TextAction
import com.focusforceplus.app.ui.components.ValueRow
import com.focusforceplus.app.ui.theme.FfTheme
import java.util.Calendar

/**
 * Editor for a block that holds a single app. Same layout as the block editor, as a
 * full-screen dialog so it opens straight from the list. While the rule is locked
 * by invincible mode everything is read-only and says when it frees up (Golden
 * Rule #11).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppRuleConfigDialog(
    row: BlockerAppRow,
    existingGroups: List<String>,
    onSave: (BlockedAppEntity) -> Unit,
    onDelete: (BlockedAppEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FfTheme.colors
    val base = row.rule ?: BlockedAppEntity(packageName = row.packageName, appName = row.appName, isBlocked = true)
    val locked = remember(base) {
        val cal = Calendar.getInstance()
        isRuleLocked(base, cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE))
    }

    var enabled by remember { mutableStateOf(base.isBlocked) }
    var limitOn by remember { mutableStateOf(base.dailyLimitMinutes != null) }
    var limit by remember { mutableIntStateOf(base.dailyLimitMinutes ?: 30) }
    var scheduleOn by remember { mutableStateOf(base.windowStartMinutes != null && base.windowEndMinutes != null) }
    var start by remember { mutableIntStateOf(base.windowStartMinutes ?: 9 * 60) }
    var end by remember { mutableIntStateOf(base.windowEndMinutes ?: 17 * 60) }
    var invincible by remember { mutableStateOf(base.invincibleMode) }
    var duringRoutines by remember { mutableStateOf(base.blockDuringRoutines) }
    var duringFocus by remember { mutableStateOf(base.blockDuringFocus) }
    var group by remember { mutableStateOf(base.groupName) }

    var pickTime by remember { mutableStateOf<Boolean?>(null) } // true = from, false = until
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val canBeInvincible = limitOn || scheduleOn
    fun result() = base.copy(
        isBlocked = enabled,
        dailyLimitMinutes = if (limitOn) limit else null,
        windowStartMinutes = if (scheduleOn) start else null,
        windowEndMinutes = if (scheduleOn) end else null,
        // Invincible without an end would be an endless lock, so it only sticks
        // together with a limit or a schedule.
        invincibleMode = invincible && canBeInvincible,
        blockDuringRoutines = duringRoutines,
        blockDuringFocus = duringFocus,
        groupName = group,
    )
    val changed = row.rule == null || result() != base
    val close = { if (changed && !locked) confirmDiscard = true else onDismiss() }

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        FormScaffold(
            title = row.appName,
            onClose = close,
            primaryLabel = if (row.rule == null) "Save block" else "Save changes",
            onPrimary = { onSave(result()) },
            primaryEnabled = !locked,
            secondaryAction = if (row.rule != null && !locked) {
                { TextAction("Remove", onClick = { confirmDelete = true }, color = colors.error) }
            } else {
                null
            },
        ) {
            if (locked) {
                FfPaddedCard(color = colors.container, spacing = 4.dp) {
                    Text("Locked right now", style = MaterialTheme.typography.titleSmall, color = colors.onContainer)
                    Text(
                        "Invincible mode keeps this block as it is until its limit resets at " +
                            "midnight or its schedule ends.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onContainer,
                    )
                }
            }

            FfCard {
                SwitchRow(
                    title = "Blocked",
                    subtitle = "Off pauses this block without deleting it.",
                    checked = enabled,
                    enabled = !locked,
                    onCheckedChange = { enabled = it },
                )
            }

            FormSection(
                title = "When it is blocked",
                hint = "With neither, the app is blocked all day.",
            ) {
                FfCard {
                    SwitchRow(
                        title = "Daily limit",
                        subtitle = "Open until the time is used up, then blocked until midnight.",
                        checked = limitOn,
                        enabled = !locked,
                        onCheckedChange = { limitOn = it },
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
                                    enabled = !locked,
                                    onClick = { limit = minutes },
                                )
                            }
                        }
                    }
                    RowDivider()
                    SwitchRow(
                        title = "Schedule",
                        subtitle = "Blocked every day between two times, up to 12 hours.",
                        checked = scheduleOn,
                        enabled = !locked,
                        onCheckedChange = { scheduleOn = it },
                    )
                    if (scheduleOn) {
                        ValueRow(title = "From", value = formatMinutes(start), enabled = !locked, onClick = { pickTime = true })
                        ValueRow(title = "Until", value = formatMinutes(end), enabled = !locked, onClick = { pickTime = false })
                    }
                }
            }

            FormSection(title = "Also") {
                FfCard {
                    SwitchRow(
                        title = "During routines",
                        subtitle = "While a routine that blocks apps runs.",
                        checked = duringRoutines,
                        enabled = !locked,
                        onCheckedChange = { duringRoutines = it },
                    )
                    RowDivider()
                    SwitchRow(
                        title = "During focus sessions",
                        subtitle = "While a focus session that blocks apps runs.",
                        checked = duringFocus,
                        enabled = !locked,
                        onCheckedChange = { duringFocus = it },
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
                        enabled = canBeInvincible && !locked,
                        onCheckedChange = { invincible = it },
                    )
                }
            }

            if (existingGroups.isNotEmpty()) {
                FormSection(
                    title = "Part of a block",
                    hint = "In a block, the app follows that block's rules and shares its limit.",
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FfChip(label = "On its own", selected = group == null, enabled = !locked, onClick = { group = null })
                        existingGroups.forEach { name ->
                            FfChip(label = name, selected = group == name, enabled = !locked, onClick = { group = name })
                        }
                    }
                }
            }
        }
    }

    pickTime?.let { isStart ->
        FfTimePickerDialog(
            title = if (isStart) "Blocked from" else "Blocked until",
            hour = (if (isStart) start else end) / 60,
            minute = (if (isStart) start else end) % 60,
            onConfirm = { h, m ->
                if (isStart) start = h * 60 + m else end = h * 60 + m
                pickTime = null
            },
            onDismiss = { pickTime = null },
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Remove this block?",
            message = "${row.appName} is no longer blocked by it. This cannot be undone.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                onDelete(base)
            },
            onDismiss = { confirmDelete = false },
        )
    }

    if (confirmDiscard) {
        DiscardChangesDialog(
            onDiscard = {
                confirmDiscard = false
                onDismiss()
            },
            onKeepEditing = { confirmDiscard = false },
        )
    }
}
