package com.focusforceplus.app.ui.common

import androidx.compose.runtime.Composable
import com.focusforceplus.app.ui.components.ConfirmDialog

/** Confirmation shown when leaving a create/edit screen with unsaved changes. */
@Composable
fun DiscardChangesDialog(
    onDiscard: () -> Unit,
    onKeepEditing: () -> Unit,
) {
    ConfirmDialog(
        title = "Discard changes?",
        message = "What you changed here will be lost.",
        confirmLabel = "Discard",
        onConfirm = onDiscard,
        onDismiss = onKeepEditing,
        destructive = true,
        dismissLabel = "Keep editing",
    )
}
