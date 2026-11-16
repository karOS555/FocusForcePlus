package com.focusforceplus.app.ui.screens.lists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.focusforceplus.app.ui.components.EmptyState

@Composable
fun ListsScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            icon = Icons.AutoMirrored.Filled.FormatListBulleted,
            title = "No lists yet",
        )
    }
}
