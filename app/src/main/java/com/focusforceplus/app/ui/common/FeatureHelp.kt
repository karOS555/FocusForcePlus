package com.focusforceplus.app.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── Content model ─────────────────────────────────────────────────────────────

data class HelpSection(val title: String, val body: String)

data class FeatureHelp(
    /** Stable key persisted in the "seen" set — never rename once shipped. */
    val key: String,
    val title: String,
    val intro: String,
    val sections: List<HelpSection>,
)

/** All in-app feature guides. Plain English, honest about limits. */
object FeatureHelpContent {

    val ROUTINES = FeatureHelp(
        key = "routines",
        title = "How routines work",
        intro = "A routine is a fixed sequence of timed steps (like a morning routine) " +
            "that starts with a real alarm — loud, over the lock screen, hard to ignore.",
        sections = listOf(
            HelpSection(
                "Alarms",
                "On the days you pick, the routine fires a full alarm at its start time, " +
                    "plus a gentle heads-up 15 minutes before. Tapping Start walks you " +
                    "through each step with a countdown timer.",
            ),
            HelpSection(
                "Running late on a step",
                "When a step's time is up, the timer keeps counting into the negative " +
                    "and reminds you at an interval you set - you can always add extra " +
                    "time or mark the step done.",
            ),
            HelpSection(
                "Snooze and reschedule",
                "Snoozes and reschedules are limited per routine (you choose the caps " +
                    "when editing). Once they are used up, the routine expects to be done.",
            ),
            HelpSection(
                "Invincible Mode",
                "While an invincible routine is running there is no cancel button - " +
                    "only completing or rescheduling it. The setting itself can only be " +
                    "changed while the routine is NOT running. Honest limits: you can " +
                    "always uninstall the app from Android settings; this is friction " +
                    "against impulsive quitting, not a cage.",
            ),
            HelpSection(
                "The switch on each card",
                "The switch pauses future scheduled runs (alarms) without deleting " +
                    "anything. A currently running session keeps going.",
            ),
            HelpSection(
                "App blocking",
                "If a routine has \"App Blocker\" enabled, your configured blocked apps " +
                    "are covered while it runs (see the Blocker tab).",
            ),
        ),
    )

    val TODOS = FeatureHelp(
        key = "todos",
        title = "How todos work",
        intro = "A planner that refuses to let things quietly slip. How pushy a todo is " +
            "depends entirely on the priority you give it.",
        sections = listOf(
            HelpSection(
                "Priorities",
                "Low: a normal notification you can swipe away. Medium: a silent alarm " +
                    "screen with snooze, reschedule, and dismiss - after dismissing, a " +
                    "soft reminder stays pinned. High: a loud alarm with sound and " +
                    "vibration, no dismiss, and a pinned notification until you complete it.",
            ),
            HelpSection(
                "Snoozes and reschedules",
                "High-priority todos have hard caps you choose when creating them (1-5 " +
                    "each). Medium and Low stay flexible. Even when everything is used " +
                    "up, the todo itself always stays editable and deletable in this list.",
            ),
            HelpSection(
                "Daily digest",
                "Undated and overdue todos resurface as notifications at fixed times " +
                    "each day. Change the times under Settings > Todos.",
            ),
            HelpSection(
                "Recurring todos",
                "Completing a recurring todo automatically creates the next occurrence " +
                    "(daily, weekly on chosen days, or monthly).",
            ),
            HelpSection(
                "Checklists",
                "A todo can carry sub-items. On the alarm screen you tick them off one " +
                    "by one - when all are done, it offers to complete the todo.",
            ),
        ),
    )

    val BLOCKER = FeatureHelp(
        key = "blocker",
        title = "How the app blocker works",
        intro = "Pick apps that pull you away, and FocusForce+ covers them with a " +
            "blocking screen the moment they open.",
        sections = listOf(
            HelpSection(
                "Three ways to block",
                "Always: blocked whenever the rule is on. Daily limit: allowed until " +
                    "you used it for X minutes today, then blocked until midnight. Time " +
                    "window: blocked during a fixed daily range of up to 12 hours - " +
                    "overnight windows like 22:00-06:00 work too. Tap any app row to " +
                    "configure this.",
            ),
            HelpSection(
                "Groups",
                "Bundle related apps (e.g. all your social media) into a group with ONE " +
                    "shared daily limit - their combined usage counts against it, and when " +
                    "it's up the whole group is blocked until midnight. Create groups at the " +
                    "top of this screen; focus sessions can then block whole groups at once.",
            ),
            HelpSection(
                "During routines and focus",
                "Apps can additionally be blocked whenever a routine or focus session " +
                    "with app blocking is running - regardless of limits. Focus sessions " +
                    "can narrow this to specific groups.",
            ),
            HelpSection(
                "Invincible Mode (per rule)",
                "Locks a rule while it is actively blocking for a bounded reason: limit " +
                    "reached (releases at midnight) or inside its window (releases at " +
                    "the window end). While locked, the rule cannot be weakened or " +
                    "turned off. An always-on block never locks - it would have no end.",
            ),
            HelpSection(
                "5-minute exceptions",
                "Twice per day and app you can grant yourself 5 more minutes from the " +
                    "blocking screen - unless the rule or the running session is invincible.",
            ),
            HelpSection(
                "The two permissions",
                "The accessibility service only detects which app comes to the " +
                    "foreground - it cannot read screen content. Usage access only " +
                    "measures your app time for limits and stats. Nothing leaves your device.",
            ),
            HelpSection(
                "Always allowed",
                "Android settings, the phone app, emergency services, your launcher, " +
                    "and FocusForce+ itself can never be blocked.",
            ),
        ),
    )

    val FOCUS = FeatureHelp(
        key = "focus",
        title = "How focus sessions work",
        intro = "A focus session is a distraction-free countdown: silence interruptions, " +
            "block tempting apps, and commit to a block of deep work.",
        sections = listOf(
            HelpSection(
                "Session types",
                "Study, Work, Creative, and Custom are presets: picking one fills in a " +
                    "fitting duration and toggle setup (Study 45 min with visible " +
                    "notifications, Work 50 min of total silence, Creative 90 min flow) " +
                    "and color-codes the session in your list. Every field stays freely " +
                    "adjustable - the behavior always comes from the toggles, not the label.",
            ),
            HelpSection(
                "Do Not Disturb",
                "\"Enable DND\" silences everything except priority interruptions; " +
                    "\"Block all notifications\" is total silence. Both need the Do Not " +
                    "Disturb access you grant once in Android settings. DND is always " +
                    "restored when the session ends - even if you swipe the app away.",
            ),
            HelpSection(
                "Invincible Mode",
                "An invincible session cannot be paused or ended early - it simply runs " +
                    "to the end of the timer (capped at 4 hours). Toggle it only while " +
                    "the session is not running.",
            ),
            HelpSection(
                "Scheduling",
                "Scheduled sessions send a reminder with a \"Start now\" button at the " +
                    "chosen time. Nothing ever starts by itself - DND switching on " +
                    "unannounced would be worse than the distraction.",
            ),
            HelpSection(
                "What gets blocked",
                "With App Blocker on, a session blocks all your configured apps by default, " +
                    "or just the blocker groups you pick. Choosing a preset type also " +
                    "suggests fitting groups automatically.",
            ),
            HelpSection(
                "Quick start",
                "The Quick start button runs an unsaved one-off session - pick a " +
                    "duration, go. It is never invincible.",
            ),
        ),
    )
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class FeatureHelpViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /** null while loading — prevents the bubble from flashing for returning users. */
    val seenKeys: StateFlow<Set<String>?> = settingsRepository.helpHintsSeen
        .map { it as Set<String>? }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun markSeen(key: String) {
        viewModelScope.launch { settingsRepository.markHelpHintSeen(key) }
    }
}

// ── Composables ───────────────────────────────────────────────────────────────

/**
 * Question-mark button that opens the feature guide. Until the user has seen it
 * once (tapped it or dismissed the bubble), the icon pulses and a small speech
 * bubble below invites a first look.
 */
@Composable
fun FeatureHelpAction(
    help: FeatureHelp,
    viewModel: FeatureHelpViewModel = hiltViewModel(),
) {
    val seenKeys by viewModel.seenKeys.collectAsState()
    val unseen = seenKeys != null && help.key !in seenKeys!!
    var showDialog by remember { mutableStateOf(false) }

    val pulse = rememberInfiniteTransition(label = "help_pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (unseen) 1.18f else 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "help_scale",
    )

    Box {
        IconButton(
            onClick = {
                viewModel.markSeen(help.key)
                showDialog = true
            },
        ) {
            Icon(
                Icons.AutoMirrored.Filled.HelpOutline,
                contentDescription = "About this feature",
                tint = if (unseen) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.scale(scale),
            )
        }

        if (unseen) {
            val yOffset = with(LocalDensity.current) { 44.dp.roundToPx() }
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, yOffset),
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 4.dp,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable {
                                viewModel.markSeen(help.key)
                                showDialog = true
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            "New here? Tap for a quick guide",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Dismiss hint",
                            tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { viewModel.markSeen(help.key) },
                        )
                    }
                }
            }
        }
    }

    if (showDialog) {
        FeatureHelpDialog(help = help, onDismiss = { showDialog = false })
    }
}

@Composable
fun FeatureHelpDialog(help: FeatureHelp, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(help.title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    help.intro,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                help.sections.forEach { section ->
                    Spacer(Modifier.height(14.dp))
                    Text(
                        section.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        section.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        },
    )
}

/**
 * A generic icon button that pulses with a one-time speech bubble until the user
 * interacts with it (same first-run affordance as [FeatureHelpAction]). Used to
 * point new users at the settings entry point.
 */
@Composable
fun FirstRunHintButton(
    hintKey: String,
    bubbleText: String,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    viewModel: FeatureHelpViewModel = hiltViewModel(),
) {
    val seenKeys by viewModel.seenKeys.collectAsState()
    val unseen = seenKeys != null && hintKey !in seenKeys!!

    val pulse = rememberInfiniteTransition(label = "hint_pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (unseen) 1.18f else 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "hint_scale",
    )

    Box {
        IconButton(onClick = {
            viewModel.markSeen(hintKey)
            onClick()
        }) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = if (unseen) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.scale(scale),
            )
        }
        if (unseen) {
            val yOffset = with(LocalDensity.current) { 44.dp.roundToPx() }
            Popup(alignment = Alignment.TopEnd, offset = IntOffset(0, yOffset)) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 4.dp,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable {
                                viewModel.markSeen(hintKey)
                                onClick()
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            bubbleText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Dismiss hint",
                            tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { viewModel.markSeen(hintKey) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Slim header used at the top of the main feature tabs: title left, help right.
 */
@Composable
fun FeatureHeader(
    title: String,
    help: FeatureHelp,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        FeatureHelpAction(help)
    }
}
