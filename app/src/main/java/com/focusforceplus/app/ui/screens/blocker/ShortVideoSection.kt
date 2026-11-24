package com.focusforceplus.app.ui.screens.blocker

import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focusforceplus.app.ui.components.FfChip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.runtime.Composable
import com.focusforceplus.app.compliance.ShortFeed
import com.focusforceplus.app.ui.components.FfCard
import com.focusforceplus.app.ui.components.FfSwitch
import com.focusforceplus.app.ui.components.IconCircle
import com.focusforceplus.app.ui.components.ListRow
import com.focusforceplus.app.ui.components.RowDivider
import com.focusforceplus.app.ui.components.SectionHeader
import com.focusforceplus.app.ui.components.SwitchRow
import com.focusforceplus.app.ui.components.TextAction
import com.focusforceplus.app.ui.components.ValueRow
import com.focusforceplus.app.ui.theme.FfTheme

/**
 * Short videos, in one card. Only what is installed on this phone is shown.
 *
 * - The feeds inside bigger apps (Reels, Shorts): a separate service leaves them,
 *   at once or after a daily time, and the rest of the app keeps working.
 * - The apps that are nothing but short videos (TikTok and the like): there is no
 *   rest to keep, so these rows are plain blocks of the whole app, done by the
 *   normal blocker, and each row says so.
 *
 * A switch that is on while nothing happens is the worst thing this card could
 * show, so whatever keeps the feeds from being left is said at the top of it, with
 * the one step that fixes it.
 */
@Composable
internal fun ShortVideoSection(
    shorts: ShortVideos,
    /** The app blocker blocks nothing at the moment: off, not running or paused. */
    blockerIdle: Boolean,
    loadIcon: suspend (String) -> Drawable?,
    onSetUp: () -> Unit,
    onOpenAndroidSettings: () -> Unit,
    onResumeBlocking: () -> Unit,
    onFeed: (ShortFeed, Boolean) -> Unit,
    onPickTime: () -> Unit,
    onShareTime: (Boolean) -> Unit,
    onOpenApp: (ShortAppRow) -> Unit,
    onApp: (ShortAppRow, Boolean) -> Unit,
) {
    val colors = FfTheme.colors
    val time = shorts.allowance
    // "Reels and Shorts", or only the one that is installed.
    val what = shorts.feeds.joinToString(" and ") { it.feed.noun }
    val both = shorts.feeds.size > 1

    val rows = buildList<@Composable () -> Unit> {
        when (shorts.trouble) {
            ShortTrouble.SERVICE_OFF -> add {
                ListRow(
                    title = "$what stay open right now",
                    subtitle = "The short-video service is switched off in Android. Without it they cannot be closed.",
                    subtitleMaxLines = 4,
                    titleColor = colors.error,
                    onClick = onSetUp,
                    trailing = { TextAction("Set up", onClick = onSetUp) },
                )
            }
            ShortTrouble.SERVICE_NOT_RUNNING -> add {
                ListRow(
                    title = "The short-video service is not running",
                    subtitle = "Android has it switched on, but it is not connected, which can happen " +
                        "after an update. Switch \"FocusForce+ Short videos\" off and on again in " +
                        "Android's accessibility settings.",
                    subtitleMaxLines = 6,
                    titleColor = colors.error,
                    onClick = onOpenAndroidSettings,
                    trailing = { TextAction("Open", onClick = onOpenAndroidSettings) },
                )
            }
            ShortTrouble.BLOCKING_PAUSED -> add {
                ListRow(
                    title = "Paused with the blocker",
                    subtitle = "Blocking is switched off at the top of this page, so $what stay open too.",
                    subtitleMaxLines = 3,
                    onClick = onResumeBlocking,
                    trailing = { TextAction("Switch on", onClick = onResumeBlocking) },
                )
            }
            null -> if (!shorts.serviceOn && shorts.feeds.isNotEmpty()) add {
                ListRow(
                    title = "Keep the app, skip the scrolling",
                    subtitle = "Leaves $what when you open them, or after a daily time; the rest of " +
                        "the app keeps working. Needs its own service.",
                    subtitleMaxLines = 4,
                    onClick = onSetUp,
                    trailing = { TextAction("Set up", onClick = onSetUp) },
                )
            }
        }

        shorts.feeds.forEach { row ->
            add {
                ListRow(
                    title = row.feed.label,
                    subtitle = feedDetail(row, shorts),
                    subtitleMaxLines = 3,
                    onClick = { onFeed(row.feed, !row.on) },
                    leading = {
                        InstalledAppIcon(row.feed.packageName, row.feed.label, loadIcon = { loadIcon(row.feed.packageName) })
                    },
                    trailing = { FfSwitch(checked = row.on, onCheckedChange = { onFeed(row.feed, it) }) },
                )
            }
        }

        if (shorts.feeds.isNotEmpty()) {
            add {
                ValueRow(
                    title = "Time per day",
                    value = if (time.none) "None" else "${time.minutes} min",
                    subtitle = when {
                        // Nothing is switched on, so there is nothing a time could hold back.
                        shorts.feeds.none { it.on } -> "Counts for what is switched on above"
                        time.none -> "$what close as soon as they open. Pick a time to allow some."
                        both && time.each -> "${time.minutes} min for Reels and ${time.minutes} min for Shorts"
                        both -> "${time.minutes} min for Reels and Shorts together"
                        else -> "${shorts.usedMinutes.coerceAtMost(time.minutes)} of ${time.minutes} min used today"
                    },
                    onClick = onPickTime,
                )
            }
            // How the time is shared is there from the start, as the two choices it
            // is, each saying what it means. As a switch that only showed once a
            // time was picked, it had to be found and then worked out.
            if (both) {
                add {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Text("The time counts", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FfChip(label = "Together", selected = !time.each, onClick = { onShareTime(true) })
                            FfChip(label = "For each", selected = time.each, onClick = { onShareTime(false) })
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = when {
                                time.none && time.each -> "Each would get its own time, once you pick one."
                                time.none -> "Both would use up the same time, once you pick one."
                                time.each -> "Reels and Shorts each have their own ${time.minutes} min a day."
                                else -> "Reels and Shorts use up the same ${time.minutes} min a day: " +
                                    "${shorts.usedMinutes.coerceAtMost(time.minutes)} of them are used today."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )
                    }
                }
            }
        }

        shorts.apps.forEach { app ->
            add {
                ListRow(
                    title = app.appName,
                    subtitle = appDetail(app, blockerIdle),
                    subtitleMaxLines = 3,
                    // With a block there is something to open: its limit and schedule.
                    onClick = { if (app.rule != null) onOpenApp(app) else onApp(app, true) },
                    leading = {
                        InstalledAppIcon(app.packageName, app.appName, loadIcon = { loadIcon(app.packageName) })
                    },
                    trailing = { FfSwitch(checked = app.on, onCheckedChange = { onApp(app, it) }) },
                )
            }
        }

        if (shorts.loaded && shorts.feeds.isEmpty() && shorts.apps.isEmpty()) {
            add {
                ListRow(
                    title = "No app with short videos found",
                    subtitle = "Instagram, YouTube, TikTok and similar apps show up here once they are installed.",
                    subtitleMaxLines = 3,
                    leading = { IconCircle(icon = Icons.Filled.SmartDisplay) },
                )
            }
        }
    }

    if (rows.isEmpty()) return
    Column {
        SectionHeader("Short videos")
        FfCard {
            rows.forEachIndexed { index, row ->
                if (index > 0) RowDivider()
                row()
            }
        }
    }
}

/** What a feed's switch means right now, in one line. */
private fun feedDetail(row: ShortFeedRow, shorts: ShortVideos): String {
    val noun = row.feed.noun
    val time = shorts.allowance
    val block = when {
        row.block == null -> null
        row.block.isEmpty() -> "a block of its own"
        else -> "your block \"${row.block}\""
    }
    return when {
        shorts.trouble != null && row.watched -> "Not closed right now, see above"
        !row.on && block != null -> "Open, except when $block closes them"
        !row.on -> "$noun are allowed"
        time.none -> "$noun close as soon as they open"
        time.each && row.usedMinutes >= time.minutes -> "Today's ${time.minutes} min are used: $noun close again"
        time.each -> "${row.usedMinutes} of ${time.minutes} min used today, then $noun close"
        shorts.usedMinutes >= time.minutes -> "Today's ${time.minutes} min are used: $noun close again"
        else -> "${row.usedMinutes} min today \u00b7 ${shorts.usedMinutes} of ${time.minutes} min used together"
    }
}

/** What the block of a whole short-video app does right now, in one line. */
private fun appDetail(app: ShortAppRow, blockerIdle: Boolean): String {
    val rule = app.rule
    val start = rule?.windowStartMinutes
    val end = rule?.windowEndMinutes
    return when {
        rule == null || !rule.isBlocked -> "Nothing but short videos, so this blocks the whole app"
        blockerIdle -> "Whole app, but nothing is blocked right now: see the top of this page"
        rule.groupName != null -> "Whole app, with your block \"${rule.groupName}\""
        rule.dailyLimitMinutes != null -> "Whole app · ${rule.usedTodayMinutes} of ${rule.dailyLimitMinutes} min today"
        start != null && end != null -> "Whole app · ${formatMinutes(start)} to ${formatMinutes(end)}"
        else -> "Whole app blocked · tap for a daily limit"
    }
}
