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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.focusforceplus.app.ui.components.FfCard
import com.focusforceplus.app.ui.components.FfPaddedCard
import com.focusforceplus.app.ui.components.FormSection
import com.focusforceplus.app.ui.components.ListRow
import com.focusforceplus.app.ui.components.NumberTile
import com.focusforceplus.app.ui.components.PageScaffold
import com.focusforceplus.app.ui.components.RowDivider
import com.focusforceplus.app.ui.components.TextAction
import com.focusforceplus.app.ui.screens.blocker.InstalledAppIcon
import com.focusforceplus.app.ui.theme.FfTheme
import com.focusforceplus.app.util.TimeFormat

/**
 * The last seven days in numbers: screen time, focus, routines and todos. Everything
 * here is computed on the device from data the app already has.
 */
@Composable
fun StatsScreen(
    onNavigateBack: () -> Unit,
    onOpenUsageDisclosure: () -> Unit = {},
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val s by viewModel.uiState.collectAsState()
    val colors = FfTheme.colors

    // Screen time changes while the app is closed, so read it on every return.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    PageScaffold(title = "Statistics", onBack = onNavigateBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            FormSection(title = "Today") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberTile(
                        value = s.screenToday?.let { TimeFormat.compact(it) } ?: "–",
                        label = "Screen time",
                        modifier = Modifier.weight(1f),
                    )
                    NumberTile(
                        value = TimeFormat.compact(s.focusToday),
                        label = "Focused",
                        modifier = Modifier.weight(1f),
                    )
                    NumberTile(
                        value = "${s.todosToday}",
                        label = if (s.todosToday == 1) "Todo done" else "Todos done",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            val screen = s.screenPerDay
            if (screen == null) {
                FfPaddedCard(spacing = 6.dp) {
                    Text("Screen time", style = MaterialTheme.typography.titleSmall, color = colors.text)
                    Text(
                        "Needs usage access. FocusForce+ only reads how long each app was open; " +
                            "nothing leaves your phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                    )
                    TextAction("Set up", onClick = onOpenUsageDisclosure, modifier = Modifier.padding(top = 2.dp))
                }
            } else {
                FormSection(title = "Screen time") {
                    // Days before the app could see usage are empty, not zero.
                    val counted = screen.filter { it.value > 0 }
                    WeekChart(
                        stats = screen,
                        emptyText = "No screen time recorded yet.",
                        valueLabel = ::chartLabel,
                        footer = if (counted.size > 1) {
                            "On average ${TimeFormat.duration(counted.sumOf { it.value } / counted.size)} a day"
                        } else {
                            null
                        },
                    )
                }
                if (s.topApps.isNotEmpty()) {
                    FormSection(title = "Most used today") {
                        FfCard {
                            val most = s.topApps.first().minutes.coerceAtLeast(1)
                            s.topApps.forEachIndexed { i, app ->
                                if (i > 0) RowDivider()
                                ListRow(
                                    title = app.name,
                                    subtitle = TimeFormat.duration(app.minutes),
                                    leading = {
                                        InstalledAppIcon(
                                            packageName = app.packageName,
                                            appName = app.name,
                                            loadIcon = { viewModel.appIcon(app.packageName) },
                                            size = 34.dp,
                                        )
                                    },
                                    trailing = { ShareBar(fraction = app.minutes.toFloat() / most) },
                                )
                            }
                        }
                    }
                }
            }

            FormSection(title = "Focus") {
                WeekChart(
                    stats = s.focusMinutesPerDay,
                    emptyText = "Finished focus sessions show up here.",
                    valueLabel = ::chartLabel,
                )
            }
            FormSection(
                title = "Routines",
                hint = if (s.totalOvertimeMinutes7d > 0) {
                    "Routines ran ${TimeFormat.duration(s.totalOvertimeMinutes7d)} over their planned time this week."
                } else {
                    null
                },
            ) {
                WeekChart(
                    stats = s.routinesPerDay,
                    emptyText = "Finished routines show up here.",
                    valueLabel = { "$it" },
                )
            }
            FormSection(title = "Todos") {
                WeekChart(
                    stats = s.todosPerDay,
                    emptyText = "Ticked-off todos show up here.",
                    valueLabel = { "$it" },
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Short enough for a bar: "45m", "2h". */
private fun chartLabel(minutes: Int): String =
    if (minutes >= 60) "${(minutes + 30) / 60}h" else "${minutes}m"

/** Seven bars, today in full colour, each with its value above it. */
@Composable
private fun WeekChart(
    stats: List<DayStat>,
    emptyText: String,
    valueLabel: (Int) -> String,
    footer: String? = null,
) {
    val colors = FfTheme.colors
    FfPaddedCard(spacing = 10.dp) {
        val max = stats.maxOfOrNull { it.value } ?: 0
        if (max == 0) {
            Text(
                emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            return@FfPaddedCard
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            stats.forEach { day ->
                val label = valueLabel(day.value)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = "${day.label}: $label" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (day.value > 0) label else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (day.isToday) colors.text else colors.textMuted,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((4 + 84 * day.value.toFloat() / max).dp)
                            .background(
                                if (day.isToday) colors.primary else colors.container,
                                RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                            ),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        day.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (day.isToday) colors.primary else colors.textMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        if (footer != null) {
            Text(footer, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
    }
}

@Composable
private fun ShareBar(fraction: Float) {
    val colors = FfTheme.colors
    Box(
        modifier = Modifier
            .padding(start = 8.dp)
            .height(6.dp)
            .width(88.dp)
            .background(colors.surfaceHigh, RoundedCornerShape(3.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0.04f, 1f))
                .height(6.dp)
                .background(colors.primary, RoundedCornerShape(3.dp)),
        )
    }
}
