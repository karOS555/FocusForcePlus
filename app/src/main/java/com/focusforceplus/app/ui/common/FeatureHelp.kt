package com.focusforceplus.app.ui.common

import com.focusforceplus.app.ui.components.verticalScrollWithHint
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.ui.theme.FfTheme
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
        intro = "A routine is a row of timed steps, like getting ready in the morning. " +
            "It starts with a real alarm and then walks you through it step by step.",
        sections = listOf(
            HelpSection(
                "The alarm",
                "On the days you pick, the routine rings at its start time, over the lock " +
                    "screen. A quiet heads-up comes before it, 15 minutes by default; each " +
                    "routine can change that or turn it off.",
            ),
            HelpSection(
                "Running late on a step",
                "When a step's time is up, the timer keeps counting and reminds you at an " +
                    "interval you set. You can always add time or mark the step done.",
            ),
            HelpSection(
                "While it runs",
                "The notification shows every step as a timeline with the current " +
                    "countdown, and works from the lock screen: Done, +5 min and Cancel " +
                    "without unlocking. A routine can block apps (all, or only some " +
                    "groups) and switch on Do Not Disturb while it runs.",
            ),
            HelpSection(
                "Snooze and reschedule",
                "Each routine has its own number of snoozes and reschedules. By default " +
                    "a small sum stands in front of them, so they are a decision and not " +
                    "a reflex. Change that in Settings > Speed bumps or per routine.",
            ),
            HelpSection(
                "Invincible mode",
                "A running invincible routine has no Cancel, only Done or Reschedule. The " +
                    "setting can only change while the routine is not running.",
            ),
            HelpSection(
                "Copy what you have",
                "Long-press a routine to duplicate it. In the editor you can copy steps " +
                    "from another routine or start from a few common ones.",
            ),
        ),
    )

    val TODOS = FeatureHelp(
        key = "todos",
        title = "How todos work",
        intro = "A todo list that does not let things slip quietly. How pushy a todo is " +
            "depends on its priority.",
        sections = listOf(
            HelpSection(
                "Priorities",
                "Low: one ordinary notification when it is due. Medium: comes up on " +
                    "screen without sound, stays as a notification and comes back a " +
                    "couple of times. High: rings, stays, and rings again every few minutes.",
            ),
            HelpSection(
                "It always stops",
                "After its repeats a todo rests as a notification until your next daily " +
                    "reminder. Nothing rings forever, and every todo can always be edited, " +
                    "moved or deleted from the list.",
            ),
            HelpSection(
                "Ticking off",
                "On the alarm screen you press Done first. High priority asks once more, " +
                    "then a slider finishes it. These speed bumps are adjustable in Settings " +
                    "> Speed bumps, and each todo can have its own.",
            ),
            HelpSection(
                "Snoozes and moves",
                "Each todo has a number of snoozes and moves. When they are used up, a " +
                    "high-priority alarm only offers Done. Lower priorities can still be " +
                    "dismissed until the next reminder.",
            ),
            HelpSection(
                "Daily reminders",
                "Todos without a date and overdue ones come back at fixed times each day. " +
                    "Set the times in Settings > Todos.",
            ),
            HelpSection(
                "Recurring todos and checklists",
                "Ticking off a recurring todo creates the next one. A todo can carry a " +
                    "checklist that you tick off on the alarm screen.",
            ),
            HelpSection(
                "Deleting",
                "Deleted todos go to the trash first and can be restored from Settings > Trash.",
            ),
        ),
    )

    val LISTS = FeatureHelp(
        key = "lists",
        title = "How lists work",
        intro = "Lists are for things you gather rather than do at a set time. There are two " +
            "kinds, and you choose when you make one: a shopping list and a checklist.",
        sections = listOf(
            HelpSection(
                "A shopping list",
                "Type what you need, several things at once if you like: \"milk, 2 bread, " +
                    "flour 500 g\". The amount shows next to the item, and common things go to " +
                    "their aisle on their own. What you bought before waits under \"Buy again\" " +
                    "for one tap.",
            ),
            HelpSection(
                "In the shop",
                "\"Go shopping\" shows only what is left, aisle by aisle, in big rows, and keeps " +
                    "the screen on. A tap puts an item in the basket. \"Done shopping\" takes " +
                    "what is in the basket off the list; what you did not get stays.",
            ),
            HelpSection(
                "Your way through the shop",
                "\"Aisles\" lets you drag the aisles into the order of your shop, and the list " +
                    "follows it. Press and hold an item to move it to another aisle; it goes " +
                    "there again next time.",
            ),
            HelpSection(
                "A checklist",
                "For the same things every time: packing, leaving the house, the gym bag. " +
                    "Ticked items stay where they are, the top shows how far along you are, and " +
                    "\"Start over\" unticks everything for the next time. A few ready-made ones " +
                    "come with their items. What you type goes on it as it is, commas and " +
                    "numbers included, and with sections you pick where it goes.",
            ),
            HelpSection(
                "Reminders and routines",
                "\"Remind me\" makes a todo for the list. When it rings, the open items are " +
                    "right there on the alarm to tick off. A routine step can carry a list too, " +
                    "like a packing list for \"Pack the bag\".",
            ),
            HelpSection(
                "Sharing and pasting",
                "\"Share\" sends the open items as text. A list someone sends you can be " +
                    "pasted into the field as it is: every line becomes an item.",
            ),
        ),
    )

    val BLOCKER = FeatureHelp(
        key = "blocker",
        title = "How the blocker works",
        intro = "A block is a set of apps, or a single app, with a rule for when it is " +
            "closed. When you open a blocked app, a blocking screen covers it.",
        sections = listOf(
            HelpSection(
                "When a block applies",
                "Always, after a daily time limit, or during a schedule of up to 12 hours " +
                    "(overnight works too). A block can also apply during routines and " +
                    "focus sessions. A shared limit counts all apps in the block together.",
            ),
            HelpSection(
                "A few more minutes",
                "Twice a day per app you can allow yourself 5 more minutes from the " +
                    "blocking screen, after a short wait. Not while the block is invincible.",
            ),
            HelpSection(
                "Invincible blocks",
                "Only blocks with a limit or a schedule can be invincible, because they " +
                    "end on their own: at midnight or at the end of the schedule. Until " +
                    "then the block cannot be weakened or switched off.",
            ),
            HelpSection(
                "Short videos",
                "Instead of blocking Instagram or YouTube completely, a separate optional " +
                    "service can leave just Reels and Shorts: as soon as they open, or after a " +
                    "daily time you allow. Reels and Shorts can also go into a block like an app: " +
                    "pick them under \"Add or remove apps\", and the block's limit, schedule, " +
                    "routines and focus sessions count for them too. Apps that are nothing but " +
                    "short videos, like TikTok, show up there too once they are installed; those " +
                    "are blocked as a whole.",
            ),
            HelpSection(
                "Permissions",
                "The blocking service only learns which app is in front and reads nothing " +
                    "on the screen. Usage access measures app time for limits and statistics. " +
                    "Nothing leaves your phone.",
            ),
            HelpSection(
                "Never blocked",
                "Android settings, the phone app, emergency calls, your home screen, your " +
                    "clock app and FocusForce+ itself.",
            ),
        ),
    )

    val FOCUS = FeatureHelp(
        key = "focus",
        title = "How focus sessions work",
        intro = "A focus session is a countdown for one piece of work, with notifications " +
            "and distracting apps out of the way.",
        sections = listOf(
            HelpSection(
                "Quick focus",
                "Pick a length and start. Nothing is saved, and it is never invincible.",
            ),
            HelpSection(
                "Saved sessions",
                "Study, Work and Creative fill in a fitting length and settings, which you " +
                    "can change freely. A session can remind you at fixed times; it never " +
                    "starts by itself.",
            ),
            HelpSection(
                "Do Not Disturb",
                "A session can switch on Do Not Disturb, or silence everything. Both need " +
                    "Do Not Disturb access, granted once in Android settings. Your previous " +
                    "setting comes back when the session ends, even if the app is closed.",
            ),
            HelpSection(
                "Blocking apps",
                "With app blocking on, a session closes every block that applies during " +
                    "focus, or only the ones you pick.",
            ),
            HelpSection(
                "Ending early",
                "Ending before the timer is up asks first and then makes you wait a moment. " +
                    "An invincible session cannot end early at all and runs for at most 4 hours.",
            ),
        ),
    )

    val SLEEP = FeatureHelp(
        key = "sleep",
        title = "How sleep mode works",
        intro = "One schedule for the night: a reminder to wind down, a quiet phone, " +
            "distracting apps closed until morning, and routines for bedtime and wake-up.",
        sections = listOf(
            HelpSection(
                "Bedtime and wake-up",
                "Pick both times and the nights. A night belongs to the evening it starts " +
                    "on, so Friday means Friday night into Saturday morning.",
            ),
            HelpSection(
                "During the night",
                "Do Not Disturb keeps it quiet, and the blocks you pick stay closed until " +
                    "wake-up. Blocking needs the blocker to be on.",
            ),
            HelpSection(
                "Routines",
                "Bedtime and wake-up can each start one of your routines with its full " +
                    "alarm. You can create one right from the picker.",
            ),
            HelpSection(
                "Backup alarms",
                "Up to three alarms in your phone's own clock app, a few minutes apart, so " +
                    "you wake up even if FocusForce+ is not running. Delete them there any time.",
            ),
            HelpSection(
                "Invincible mode",
                "While the night runs, an invincible schedule cannot be weakened or " +
                    "switched off until wake-up. You can still make it stricter.",
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
    val colors = FfTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceVariant,
        titleContentColor = colors.text,
        title = { Text(help.title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(modifier = Modifier.verticalScrollWithHint()) {
                Text(help.intro, style = MaterialTheme.typography.bodyMedium, color = colors.text)
                help.sections.forEach { section ->
                    Spacer(Modifier.height(16.dp))
                    Text(section.title, style = MaterialTheme.typography.titleSmall, color = colors.text)
                    Spacer(Modifier.height(2.dp))
                    Text(section.body, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it", style = MaterialTheme.typography.labelLarge, color = colors.accent)
            }
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
