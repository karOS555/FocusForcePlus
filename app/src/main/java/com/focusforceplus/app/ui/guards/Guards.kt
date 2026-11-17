package com.focusforceplus.app.ui.guards

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.focusforceplus.app.compliance.Challenge
import com.focusforceplus.app.compliance.ComplianceLimits
import com.focusforceplus.app.compliance.Guard
import com.focusforceplus.app.ui.components.FfChip
import com.focusforceplus.app.ui.components.FfSwitch
import com.focusforceplus.app.ui.components.ListRow
import com.focusforceplus.app.ui.components.OutlineButton
import com.focusforceplus.app.ui.components.PrimaryButton
import com.focusforceplus.app.ui.components.RowPosition
import com.focusforceplus.app.ui.components.TextAction
import com.focusforceplus.app.ui.theme.FfShapes
import com.focusforceplus.app.ui.theme.FfTheme
import com.focusforceplus.app.ui.theme.FfType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

// ── Wording ───────────────────────────────────────────────────────────────────

/** The words a speed bump uses for one kind of action. */
data class GuardCopy(
    /** Asked when the speed bump asks again: "Is it really done?" */
    val question: String,
    val yes: String,
    /** The way back out, on every step. */
    val no: String,
    /** The final button after a countdown that has no task after it. */
    val proceed: String,
    /** Ends the task hints: "Drag along the path to [finish]". */
    val finish: String,
) {
    companion object {
        val DONE = GuardCopy("Is it really done?", "Yes, it's done", "Not yet", "Mark as done", "mark it done")
        val SNOOZE = GuardCopy("Snooze it?", "Yes, snooze", "Never mind", "Snooze", "snooze")
        val RESCHEDULE = GuardCopy("Move it to later?", "Yes, move it", "Never mind", "Move it", "move it")
        val FOCUS_END = GuardCopy("End the session now?", "Yes, end it", "Keep going", "End session", "end the session")
        val BLOCKER_EXCEPTION = GuardCopy("Open it anyway?", "Yes, a few minutes", "Never mind", "Open", "open it")
        val BLOCKER_PAUSE = GuardCopy("Pause the blocker?", "Yes, pause", "Never mind", "Pause", "pause the blocker")
    }
}

// ── The flow ──────────────────────────────────────────────────────────────────

private enum class Step { CONFIRM, WAIT, CHALLENGE }

private fun Guard.steps(): List<Step> = buildList {
    if (confirm) add(Step.CONFIRM)
    if (waitSeconds > 0) add(Step.WAIT)
    if (challenge != Challenge.NONE) add(Step.CHALLENGE)
}

/**
 * Walks through the parts of [guard] one after the other, in place, and calls
 * [onPassed] at the end. [onCancel] is offered on every step. A guard with no
 * parts passes straight away.
 *
 * It has no frame of its own, so it can sit inline (the alarm screen), in a sheet
 * (the todo list) or in a dialog ([GuardDialog]).
 */
@Composable
fun GuardFlow(
    guard: Guard,
    copy: GuardCopy,
    onPassed: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val steps = remember(guard) { guard.steps() }
    var index by remember(guard) { mutableIntStateOf(0) }
    val passed by rememberUpdatedState(onPassed)

    if (steps.isEmpty()) {
        LaunchedEffect(Unit) { passed() }
        return
    }
    val current = index.coerceAtMost(steps.lastIndex)
    val isLast = current == steps.lastIndex
    val advance: () -> Unit = { if (isLast) passed() else index = current + 1 }

    Column(modifier.fillMaxWidth()) {
        key(current) {
            when (steps[current]) {
                Step.CONFIRM -> ConfirmStep(copy, onYes = advance, onNo = onCancel)
                Step.WAIT -> WaitStep(
                    seconds = guard.waitSeconds,
                    isLast = isLast,
                    next = guard.challenge,
                    copy = copy,
                    onDone = advance,
                    onCancel = onCancel,
                )
                Step.CHALLENGE -> ChallengeStep(guard.challenge, copy, onDone = advance, onCancel = onCancel)
            }
        }
    }
}

/**
 * [GuardFlow] in a dialog. [subject] names what the action is about (a todo's
 * title, say) above the steps. With no parts it does not show at all.
 */
@Composable
fun GuardDialog(
    guard: Guard,
    copy: GuardCopy,
    onPassed: () -> Unit,
    onDismiss: () -> Unit,
    subject: String? = null,
) {
    val passed by rememberUpdatedState(onPassed)
    if (guard.isNone) {
        LaunchedEffect(Unit) { passed() }
        return
    }
    val colors = FfTheme.colors
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surfaceVariant)
                .padding(20.dp),
        ) {
            if (subject != null) {
                Text(
                    text = subject,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textMuted,
                    maxLines = 1,
                )
                Spacer(Modifier.height(10.dp))
            }
            GuardFlow(guard = guard, copy = copy, onPassed = onPassed, onCancel = onDismiss)
        }
    }
}

@Composable
private fun StepTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, color = FfTheme.colors.text)
}

@Composable
private fun CancelAction(label: String, onCancel: () -> Unit) {
    // Pulled left by its own padding, so the text lines up with the step above.
    TextAction(label, onClick = onCancel, color = FfTheme.colors.textSecondary, modifier = Modifier.offset(x = (-8).dp))
}

@Composable
private fun ConfirmStep(copy: GuardCopy, onYes: () -> Unit, onNo: () -> Unit) {
    StepTitle(copy.question)
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlineButton(text = copy.no, onClick = onNo, modifier = Modifier.weight(1f))
        PrimaryButton(text = copy.yes, onClick = onYes, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun WaitStep(
    seconds: Int,
    isLast: Boolean,
    next: Challenge,
    copy: GuardCopy,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = FfTheme.colors
    val total = seconds.coerceIn(1, ComplianceLimits.MAX_GUARD_WAIT_SECONDS)
    var left by remember { mutableIntStateOf(total) }
    val progress = remember { Animatable(0f) }
    val done by rememberUpdatedState(onDone)

    LaunchedEffect(total) {
        launch { progress.animateTo(1f, tween(total * 1_000, easing = LinearEasing)) }
        while (left > 0) {
            delay(1_000)
            left--
        }
        // With a task still to come, the wait simply hands over to it.
        if (!isLast) done()
    }

    StepTitle(if (left > 0) "Wait $left s" else "Ready")
    if (!isLast) {
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Then: " + challengeHint(next, copy).replaceFirstChar { it.lowercase() } + ".",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
        )
    }
    Spacer(Modifier.height(14.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(colors.surfaceHigh),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.value)
                .background(colors.accent),
        )
    }
    Spacer(Modifier.height(16.dp))
    if (isLast) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineButton(text = copy.no, onClick = onCancel, modifier = Modifier.weight(1f))
            PrimaryButton(
                text = copy.proceed,
                onClick = onDone,
                enabled = left == 0,
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        CancelAction(copy.no, onCancel)
    }
}

private fun challengeHint(challenge: Challenge, copy: GuardCopy): String = when (challenge) {
    Challenge.HOLD -> "Hold the button to ${copy.finish}"
    Challenge.SLIDE -> "Slide to ${copy.finish}"
    Challenge.PATH -> "Drag along the path to ${copy.finish}"
    Challenge.MATH -> "Solve the sum to ${copy.finish}"
    Challenge.NONE -> copy.proceed
}

@Composable
private fun ChallengeStep(challenge: Challenge, copy: GuardCopy, onDone: () -> Unit, onCancel: () -> Unit) {
    StepTitle(challengeHint(challenge, copy))
    Spacer(Modifier.height(14.dp))
    when (challenge) {
        Challenge.HOLD -> HoldToConfirm(text = "Press and hold", onConfirm = onDone)
        Challenge.SLIDE -> PathSlider(
            spec = remember { PathSpec.randomLine() },
            onComplete = onDone,
            height = 60.dp,
            actionLabel = copy.proceed,
        )
        Challenge.PATH -> PathSlider(
            spec = remember { PathSpec.random() },
            onComplete = onDone,
            actionLabel = copy.proceed,
        )
        Challenge.MATH -> MathChallenge(cancelLabel = copy.no, onSolved = onDone, onCancel = onCancel)
        Challenge.NONE -> PrimaryButton(text = copy.proceed, onClick = onDone, modifier = Modifier.fillMaxWidth())
    }
    if (challenge != Challenge.MATH) {
        Spacer(Modifier.height(8.dp))
        CancelAction(copy.no, onCancel)
    }
}

// ── Hold ──────────────────────────────────────────────────────────────────────

/** A button that only fires after being held down for [holdMillis]. */
@Composable
fun HoldToConfirm(
    text: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    holdMillis: Int = 1_200,
) {
    val colors = FfTheme.colors
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val fill = remember { Animatable(0f) }
    val currentOnConfirm by rememberUpdatedState(onConfirm)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(FfShapes.button)
            .background(if (enabled) colors.container else colors.surfaceVariant)
            .semantics {
                role = Role.Button
                if (enabled) onClick(label = text) {
                    currentOnConfirm()
                    true
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        val filling = scope.launch {
                            fill.animateTo(1f, tween(holdMillis, easing = LinearEasing))
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnConfirm()
                        }
                        tryAwaitRelease()
                        if (fill.value < 1f) {
                            filling.cancel()
                            scope.launch { fill.animateTo(0f, tween(180)) }
                        }
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fill.value)
                .background(colors.primary),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) colors.text else colors.textFaint,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

// ── Sum ───────────────────────────────────────────────────────────────────────

/** A sum of two numbers. Small enough for anyone, large enough to need a thought. */
private data class Sum(val a: Int, val b: Int, val plus: Boolean) {
    val answer: Int get() = if (plus) a + b else a - b
    val text: String get() = if (plus) "$a + $b" else "$a − $b"

    companion object {
        fun random(): Sum {
            val plus = Random.nextBoolean()
            return if (plus) {
                Sum(Random.nextInt(12, 60), Random.nextInt(11, 40), true)
            } else {
                val b = Random.nextInt(11, 40)
                Sum(b + Random.nextInt(12, 50), b, false)
            }
        }
    }
}

@Composable
private fun MathChallenge(cancelLabel: String, onSolved: () -> Unit, onCancel: () -> Unit) {
    val colors = FfTheme.colors
    var sum by remember { mutableStateOf(Sum.random()) }
    var entry by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "${sum.text} =",
            style = FfType.number.copy(fontSize = MaterialTheme.typography.headlineMedium.fontSize),
            color = colors.text,
        )
        Spacer(Modifier.size(12.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(FfShapes.field)
                .background(colors.surfaceHigh)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = entry.ifEmpty { "?" },
                style = FfType.number.copy(fontSize = MaterialTheme.typography.headlineSmall.fontSize),
                color = if (entry.isEmpty()) colors.textFaint else colors.text,
            )
        }
    }
    if (wrong) {
        Spacer(Modifier.height(8.dp))
        Text("Not quite. Here is a new one.", style = MaterialTheme.typography.bodySmall, color = colors.error)
    }
    Spacer(Modifier.height(14.dp))

    // Its own keypad: the system keyboard is awkward on top of a lock screen.
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "back")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(FfShapes.button)
                            .then(
                                if (key.isEmpty()) {
                                    Modifier
                                } else {
                                    Modifier
                                        .background(colors.surface)
                                        .clickable {
                                            wrong = false
                                            entry = when (key) {
                                                "back" -> entry.dropLast(1)
                                                else -> if (entry.length < 4) entry + key else entry
                                            }
                                        }
                                }
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (key) {
                            "" -> Unit
                            "back" -> Icon(
                                Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Delete",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                            else -> Text(
                                key,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = colors.text,
                            )
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlineButton(text = cancelLabel, onClick = onCancel, modifier = Modifier.weight(1f))
        PrimaryButton(
            text = "Check",
            enabled = entry.isNotEmpty(),
            modifier = Modifier.weight(1f),
            onClick = {
                if (entry.toIntOrNull() == sum.answer) {
                    onSolved()
                } else {
                    wrong = true
                    entry = ""
                    sum = Sum.random()
                }
            },
        )
    }
}

// ── Setting them ──────────────────────────────────────────────────────────────

/** A row that shows a speed bump and opens [GuardEditorDialog]. */
@Composable
fun GuardRow(
    title: String,
    guard: Guard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
    enabled: Boolean = true,
    position: RowPosition = RowPosition.InCard,
) {
    ListRow(
        title = title,
        subtitle = guard.describe() + (note?.let { " · $it" } ?: ""),
        modifier = modifier,
        position = position,
        titleColor = if (enabled) FfTheme.colors.text else FfTheme.colors.textMuted,
        onClick = if (enabled) onClick else null,
        trailing = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = FfTheme.colors.textFaint,
            )
        },
    )
}

/**
 * Edits one speed bump: asking again, a countdown, and a task, each on its own and
 * in any combination. With [allowDefault] the first switch hands the choice back to
 * the default, which [onSave] reports as null.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GuardEditorDialog(
    title: String,
    current: Guard?,
    default: Guard,
    onSave: (Guard?) -> Unit,
    onDismiss: () -> Unit,
    allowDefault: Boolean = true,
) {
    val colors = FfTheme.colors
    var useDefault by remember { mutableStateOf(allowDefault && current == null) }
    var draft by remember { mutableStateOf(current ?: default) }
    val shown = if (useDefault) default else draft
    val editable = !useDefault

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceVariant,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = colors.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (allowDefault) {
                    EditorSwitch(
                        title = "Use the default",
                        detail = default.describe(),
                        checked = useDefault,
                        onChange = {
                            useDefault = it
                            if (!it) draft = default
                        },
                    )
                    HorizontalDivider(color = colors.outline, modifier = Modifier.padding(vertical = 8.dp))
                }
                EditorSwitch(
                    title = "Ask again first",
                    detail = "One more tap to say you mean it.",
                    checked = shown.confirm,
                    enabled = editable,
                    onChange = { draft = draft.copy(confirm = it) },
                )
                Spacer(Modifier.height(12.dp))
                Text("Countdown", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Guard.WAIT_CHOICES.forEach { seconds ->
                        FfChip(
                            label = if (seconds == 0) "Off" else "$seconds s",
                            selected = shown.waitSeconds == seconds,
                            enabled = editable,
                            onClick = { draft = draft.copy(waitSeconds = seconds) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Task", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Challenge.entries.forEach { challenge ->
                        FfChip(
                            label = challenge.label,
                            selected = shown.challenge == challenge,
                            enabled = editable,
                            onClick = { draft = draft.copy(challenge = challenge) },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = shown.challenge.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(if (useDefault) null else draft.clamped()) }) {
                Text("Save", style = MaterialTheme.typography.labelLarge, color = colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
            }
        },
    )
}

@Composable
private fun EditorSwitch(
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val colors = FfTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) colors.text else colors.textMuted,
            )
            Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        Spacer(Modifier.size(12.dp))
        FfSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
