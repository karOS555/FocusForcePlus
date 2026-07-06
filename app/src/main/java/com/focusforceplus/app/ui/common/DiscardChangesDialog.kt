package com.focusforceplus.app.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** Confirmation shown when leaving a create/edit screen with unsaved changes. */
@Composable
fun DiscardChangesDialog(
    onDiscard: () -> Unit,
    onKeepEditing: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text("Discard changes?") },
        text = { Text("You have unsaved changes. Leaving now will throw them away.") },
        confirmButton = {
            Button(
                onClick = onDiscard,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text("Discard") }
        },
        dismissButton = {
            TextButton(onClick = onKeepEditing) { Text("Keep editing") }
        },
    )
}
