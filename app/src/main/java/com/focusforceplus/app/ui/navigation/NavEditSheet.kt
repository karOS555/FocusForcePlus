package com.focusforceplus.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.focusforceplus.app.ui.components.ConfirmDialog
import com.focusforceplus.app.ui.components.FfCard
import com.focusforceplus.app.ui.components.FfSwitch
import com.focusforceplus.app.ui.components.IconCircle
import com.focusforceplus.app.ui.components.ReorderableColumn
import com.focusforceplus.app.ui.components.RowDivider
import com.focusforceplus.app.ui.components.TextAction
import com.focusforceplus.app.ui.theme.FfTheme
import com.focusforceplus.app.ui.theme.FfType

private val ROW_HEIGHT = 60.dp

/**
 * Where the navigation bar is arranged: drag to reorder, switch entries in and out
 * of the bar, and switch whole modules off.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavEditSheet(
    config: NavConfig,
    onMove: (from: Int, to: Int) -> Unit,
    onSetShown: (AppModule, Boolean) -> Unit,
    onSetModuleOn: (AppModule, Boolean) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FfTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.background,
        contentColor = colors.text,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Navigation bar",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.text,
                    modifier = Modifier.weight(1f),
                )
                TextAction("Reset", onClick = onReset)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Drag to reorder. Use the switch to keep something out of the bar, or the " +
                    "menu to switch a whole module off.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
            )
            Spacer(Modifier.height(16.dp))
            // Only the list scrolls, and only when it does not fit. As one scrolling
            // page that was a few pixels too tall on some screens, the lightest swipe
            // moved the page, then the sheet, then the page again: it all jumped.
            val scroll = rememberScrollState()
            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(scroll, enabled = scroll.canScrollForward || scroll.canScrollBackward),
            ) {
                NavEditor(
                    config = config,
                    onMove = onMove,
                    onSetShown = onSetShown,
                    onSetModuleOn = onSetModuleOn,
                )
            }
        }
    }
}

/**
 * The arrangeable list of everything that can be in the bar. Used by the sheet
 * behind the bar's Edit entry and by the last steps of onboarding.
 */
@Composable
fun NavEditor(
    config: NavConfig,
    onMove: (from: Int, to: Int) -> Unit,
    onSetShown: (AppModule, Boolean) -> Unit,
    onSetModuleOn: (AppModule, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmOff by remember { mutableStateOf<AppModule?>(null) }

    FfCard(modifier = modifier) {
        ReorderableColumn(
            items = config.order,
            key = { it.key },
            rowHeight = ROW_HEIGHT,
            onMove = onMove,
        ) { module, handle, _ ->
            NavEditRow(
                module = module,
                state = config.state(module),
                showDivider = config.order.firstOrNull() != module,
                handle = handle,
                onSetShown = { onSetShown(module, it) },
                onSwitchOff = { confirmOff = module },
                onSwitchOn = { onSetModuleOn(module, true) },
            )
        }
    }

    confirmOff?.let { module ->
        ConfirmDialog(
            title = "Switch ${module.label} off?",
            message = "It leaves the bar and the home screen, and its reminders stay quiet " +
                "until you switch it back on. Nothing is deleted.",
            confirmLabel = "Switch off",
            onConfirm = {
                onSetModuleOn(module, false)
                confirmOff = null
            },
            onDismiss = { confirmOff = null },
        )
    }
}

@Composable
private fun NavEditRow(
    module: AppModule,
    state: ModuleState,
    showDivider: Boolean,
    handle: Modifier,
    onSetShown: (Boolean) -> Unit,
    onSwitchOff: () -> Unit,
    onSwitchOn: () -> Unit,
) {
    val colors = FfTheme.colors
    val isOff = state == ModuleState.OFF
    var menuOpen by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        if (showDivider) RowDivider(Modifier.align(Alignment.TopCenter))
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 4.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = handle.size(44.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.DragHandle,
                    contentDescription = "Drag to reorder ${module.label}",
                    tint = colors.textFaint,
                    modifier = Modifier.size(22.dp),
                )
            }
            IconCircle(
                icon = module.icon,
                size = 34.dp,
                modifier = Modifier.alpha(if (isOff) 0.45f else 1f),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    module.label,
                    style = FfType.rowTitle,
                    color = if (isOff) colors.textMuted else colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when {
                        !module.canHide -> "Always in the bar"
                        isOff -> "Off · reminders paused"
                        state == ModuleState.HIDDEN -> "Not in the bar"
                        else -> "In the bar"
                    },
                    style = FfType.rowDetail,
                    color = colors.textMuted,
                    maxLines = 1,
                )
            }
            when {
                !module.canHide -> Unit
                isOff -> TextAction("Switch on", onClick = onSwitchOn)
                else -> {
                    FfSwitch(
                        checked = state == ModuleState.SHOWN,
                        onCheckedChange = onSetShown,
                    )
                    if (module.isFeature) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = "More for ${module.label}",
                                    tint = colors.textMuted,
                                )
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                                containerColor = colors.surfaceVariant,
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Switch module off") },
                                    onClick = {
                                        menuOpen = false
                                        onSwitchOff()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
