package com.focusforceplus.app.ui.screens.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
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
import com.focusforceplus.app.ui.components.PageScaffold
import com.focusforceplus.app.ui.components.RowDivider
import com.focusforceplus.app.ui.components.TextAction
import com.focusforceplus.app.ui.screens.blocker.InstalledAppIcon
import com.focusforceplus.app.ui.theme.FfTheme
import com.focusforceplus.app.util.TimeFormat

/** How a measure is read: minutes or a count, and which direction is the good one. */
private enum class Kind(val minutes: Boolean, val lessIsBetter: Boolean) {
    FOCUS(minutes = true, lessIsBetter = false),
    COUNT(minutes = false, lessIsBetter = false),
    SCREEN(minutes = true, lessIsBetter = true),
}

private fun Kind.format(value: Int): String = if (minutes) TimeFormat.duration(value) else "$value"

/**
 * The last seven days: a week overview on top, compared with the week before,
 * then one chart per measure. Tapping a bar shows that day's number.
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
            FormSection(title = "Last 7 days", hint = "Compared with the 7 days before.") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WeekTile("Focused", s.focus, Kind.FOCUS, Modifier.weight(1f))
                        WeekTile("Routines finished", s.routines, Kind.COUNT, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WeekTile("Todos done", s.todos, Kind.COUNT, Modifier.weight(1f))
                        val screen = s.screen
                        if (screen != null) {
                            WeekTile("Screen time a day", screen, Kind.SCREEN, Modifier.weight(1f), perDay = true)
                        } else {
                            FfPaddedCard(modifier = Modifier.weight(1f), spacing = 2.dp, onClick = onOpenUsageDisclosure) {
                                Text("Screen time", style = MaterialTheme.typography.labelLarge, color = colors.textMuted)
                                Text("Set up", style = MaterialTheme.typography.titleMedium, color = colors.accent)
                                Text("Needs usage access", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                            }
                        }
                    }
                }
            }

            FormSection(title = "Focus") {
                WeekChart(s.focus, Kind.FOCUS, emptyText = "Finished focus sessions show up here.")
            }
            FormSection(
                title = "Routines",
                hint = if (s.overtimeMinutes > 0) {
                    "Routines ran ${TimeFormat.duration(s.overtimeMinutes)} over their planned time this week."
                } else null,
            ) {
                WeekChart(s.routines, Kind.COUNT, emptyText = "Finished routines show up here.")
            }
            FormSection(title = "Todos") {
                WeekChart(s.todos, Kind.COUNT, emptyText = "Ticked-off todos show up here.")
            }

            val screen = s.screen
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
                    WeekChart(screen, Kind.SCREEN, emptyText = "No screen time recorded yet.")
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
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * This week's number, the change against the week before (an arrow and words,
 * never only a colour), and a streak where one makes sense.
 */
@Composable
private fun WeekTile(label: String, metric: Metric, kind: Kind, modifier: Modifier, perDay: Boolean = false) {
    val colors = FfTheme.colors
    // A day's average counts only days with data: the app may be newer than a week.
    val value = if (perDay) metric.average else metric.total
    val before = if (perDay) metric.previousAverage else metric.previousTotal
    val change = value - before
    FfPaddedCard(modifier = modifier, spacing = 2.dp) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.textMuted, maxLines = 1)
        Text(kind.format(value), style = MaterialTheme.typography.titleLarge, color = colors.text, maxLines = 1)
        if (before > 0 || value > 0) {
            val better = if (kind.lessIsBetter) change < 0 else change > 0
            val tint = when {
                change == 0 -> colors.textMuted
                better -> colors.success
                else -> colors.textMuted
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when {
                        change > 0 -> Icons.AutoMirrored.Filled.TrendingUp
                        change < 0 -> Icons.AutoMirrored.Filled.TrendingDown
                        else -> Icons.AutoMirrored.Filled.TrendingFlat
                    },
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    when {
                        change == 0 -> "Same as before"
                        change > 0 -> "${kind.format(change)} more"
                        else -> "${kind.format(-change)} less"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
        if (metric.streak >= 2) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = colors.warning, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("${metric.streak} days in a row", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1)
            }
        }
    }
}

/**
 * Seven bars, one hue. The picked day (today at first) is in full colour and its
 * number stands above the chart; a dashed line marks the average. Tap a bar to
 * read another day.
 */
@Composable
private fun WeekChart(metric: Metric, kind: Kind, emptyText: String) {
    val colors = FfTheme.colors
    val stats = metric.days
    FfPaddedCard(spacing = 10.dp) {
        val max = stats.maxOfOrNull { it.value } ?: 0
        if (max == 0) {
            Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, modifier = Modifier.padding(vertical = 12.dp))
            return@FfPaddedCard
        }
        var picked by remember(stats.size) { mutableIntStateOf(stats.lastIndex) }
        val day = stats.getOrNull(picked) ?: stats.last()
        val counted = stats.count { it.value > 0 }

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                if (day.isToday) "Today" else day.label,
                style = MaterialTheme.typography.labelLarge,
                color = colors.textMuted,
                modifier = Modifier.weight(1f),
            )
            Text(kind.format(day.value), style = MaterialTheme.typography.titleMedium, color = colors.text)
        }

        val chartHeight = 96.dp
        Box(Modifier.fillMaxWidth().height(chartHeight)) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                stats.forEachIndexed { index, d ->
                    val isPicked = index == picked
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(role = Role.Button) { picked = index }
                            .semantics {
                                contentDescription = "${if (d.isToday) "Today" else d.label}: ${kind.format(d.value)}"
                                selected = isPicked
                            },
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(chartHeight * (d.value.toFloat() / max).coerceAtLeast(if (d.value > 0) 0.04f else 0.015f))
                                .background(
                                    if (isPicked) colors.primary else colors.container,
                                    RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                                ),
                        )
                    }
                }
            }
            // The average, quiet, over the bars.
            if (counted >= 2) {
                val avg = metric.average
                Canvas(Modifier.fillMaxSize()) {
                    val y = size.height * (1f - avg.toFloat() / max)
                    drawLine(
                        color = colors.textMuted,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            stats.forEachIndexed { index, d ->
                Text(
                    d.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == picked) colors.text else colors.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
            }
        }
        if (counted >= 2) {
            Text(
                "Dashed line: on average ${kind.format(metric.average)} a day",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
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
