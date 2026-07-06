package com.focusforceplus.app.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onNavigateBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val s by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistics") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ── Totals ────────────────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TotalCard(
                    modifier = Modifier.weight(1f),
                    value = "${s.totalRoutines7d}",
                    label = "Routines done",
                )
                TotalCard(
                    modifier = Modifier.weight(1f),
                    value = formatHoursMinutes(s.totalFocusMinutes7d),
                    label = "Focus time",
                )
                TotalCard(
                    modifier = Modifier.weight(1f),
                    value = "${s.focusSessions7d}",
                    label = "Focus sessions",
                )
            }
            Text(
                "Last 7 days" + if (s.totalOvertimeMinutes7d > 0) {
                    " · ${s.totalOvertimeMinutes7d} min routine overtime"
                } else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ── Charts ────────────────────────────────────────────────────────
            WeekBarCard(
                title = "Routines completed",
                stats = s.routinesPerDay,
                emptyHint = "Complete a routine and it shows up here.",
                valueLabel = { "$it" },
            )
            WeekBarCard(
                title = "Focus minutes",
                stats = s.focusMinutesPerDay,
                emptyHint = "Finish a focus session and your minutes land here.",
                valueLabel = { if (it >= 60) "${it / 60}h${if (it % 60 > 0) (it % 60).toString() else ""}" else "$it" },
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TotalCard(modifier: Modifier = Modifier, value: String, label: String) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Seven vertical bars, today highlighted, value shown above each non-zero bar. */
@Composable
private fun WeekBarCard(
    title: String,
    stats: List<DayStat>,
    emptyHint: String,
    valueLabel: (Int) -> String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))

            val max = stats.maxOfOrNull { it.value } ?: 0
            if (max == 0) {
                Text(
                    emptyHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    stats.forEach { day ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                if (day.value > 0) valueLabel(day.value) else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                            Spacer(Modifier.height(2.dp))
                            val fraction = day.value.toFloat() / max.toFloat()
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((4 + 86 * fraction).dp)
                                    .background(
                                        if (day.isToday) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                        RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp),
                                    ),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                day.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (day.isToday) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatHoursMinutes(totalMinutes: Int): String {
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
