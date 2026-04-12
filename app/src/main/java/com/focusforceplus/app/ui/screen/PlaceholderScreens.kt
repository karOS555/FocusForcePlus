package com.focusforceplus.app.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun HomeScreen() = PlaceholderScreen("Home")

@Composable
fun RoutinesScreen() = PlaceholderScreen("Routinen")

@Composable
fun TodosScreen() = PlaceholderScreen("Todos")

@Composable
fun BlockerScreen() = PlaceholderScreen("App Blocker")

@Composable
fun FocusScreen() = PlaceholderScreen("Fokus")

@Composable
private fun PlaceholderScreen(name: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
