package com.focusforceplus.app.ui.screens.trash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.focusforceplus.app.ui.common.RoutineIcons
import com.focusforceplus.app.ui.components.ConfirmDialog
import com.focusforceplus.app.ui.components.DeleteDialog
import com.focusforceplus.app.ui.components.EmptyState
import com.focusforceplus.app.ui.components.FfCard
import com.focusforceplus.app.ui.components.FormSection
import com.focusforceplus.app.ui.components.IconCircle
import com.focusforceplus.app.ui.components.ListRow
import com.focusforceplus.app.ui.components.PageScaffold
import com.focusforceplus.app.ui.components.RowDivider
import com.focusforceplus.app.ui.components.TextAction
import com.focusforceplus.app.ui.theme.FfTheme
import com.focusforceplus.app.util.TimeFormat

private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

@Composable
fun TrashScreen(
    onNavigateBack: () -> Unit,
    viewModel: TrashViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val colors = FfTheme.colors
    var purge by remember { mutableStateOf<TrashEntry?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }

    PageScaffold(
        title = "Trash",
        onBack = onNavigateBack,
        actions = {
            if (state.entries.isNotEmpty()) TextAction("Empty", onClick = { confirmEmpty = true })
        },
    ) { padding ->
        if (!state.loading && state.entries.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Delete,
                title = "The trash is empty",
                text = "Things you delete stay here for ${state.retentionDays} days, so a slip " +
                    "of the thumb is never final.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            return@PageScaffold
        }
        val now = System.currentTimeMillis()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item {
                Text(
                    "Deleted things stay here for ${state.retentionDays} days, then they are gone " +
                        "for good. Press and hold one to delete it right away.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                )
            }
            TrashKind.entries.forEach { kind ->
                val entries = state.entries.filter { it.kind == kind }.sortedByDescending { it.deletedAt }
                if (entries.isNotEmpty()) {
                    item(key = kind.name) {
                        FormSection(title = "${kind.label} · ${entries.size}") {
                            FfCard {
                                entries.forEachIndexed { index, entry ->
                                    if (index > 0) RowDivider()
                                    ListRow(
                                        title = entry.title,
                                        subtitle = detail(entry, state.retentionDays, now),
                                        leading = { IconCircle(icon = iconFor(entry)) },
                                        onLongClick = { purge = entry },
                                        trailing = { TextAction("Restore", onClick = { viewModel.restore(entry) }) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    purge?.let { entry ->
        DeleteDialog(
            what = "\"${entry.title}\" for good",
            goesToTrash = false,
            onConfirm = {
                viewModel.deleteForever(entry)
                purge = null
            },
            onDismiss = { purge = null },
        )
    }

    if (confirmEmpty) {
        ConfirmDialog(
            title = "Empty the trash?",
            message = "Everything in it is deleted for good. This cannot be undone.",
            confirmLabel = "Empty",
            destructive = true,
            onConfirm = {
                viewModel.emptyTrash()
                confirmEmpty = false
            },
            onDismiss = { confirmEmpty = false },
        )
    }
}

private fun detail(entry: TrashEntry, retentionDays: Int, now: Long): String {
    val day = TimeFormat.day(entry.deletedAt).let {
        if (it == "Today" || it == "Yesterday") it.lowercase() else "on $it"
    }
    val left = retentionDays - ((now - entry.deletedAt) / DAY_MILLIS).toInt()
    val remaining = when {
        left <= 1 -> "gone within a day"
        else -> "$left days left"
    }
    return "Deleted $day · $remaining"
}

private fun iconFor(entry: TrashEntry): ImageVector =
    RoutineIcons.find(entry.iconKey)?.vector ?: when (entry.kind) {
        TrashKind.TODO -> Icons.Filled.TaskAlt
        TrashKind.ROUTINE -> Icons.Filled.ViewDay
        TrashKind.FOCUS -> Icons.Filled.SelfImprovement
        TrashKind.LIST -> Icons.AutoMirrored.Filled.FormatListBulleted
    }
