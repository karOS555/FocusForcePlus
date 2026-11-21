package com.focusforceplus.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.focusforceplus.app.ui.theme.FfShapes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.focusforceplus.app.ui.theme.FfTheme
import com.focusforceplus.app.ui.theme.FfType

/**
 * Keeps a field on screen while it is typed into. Compose scrolls a field into view
 * when it gets the focus, but that happens before the keyboard is up: a field low
 * on the screen ended behind it. This asks again once the keyboard has risen, and
 * whenever the field grows by a line.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
fun Modifier.staysVisibleWhenFocused(): Modifier = composed {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    val keyboardUp = WindowInsets.isImeVisible
    LaunchedEffect(focused, keyboardUp) {
        if (focused && keyboardUp) {
            delay(KEYBOARD_RISE_MILLIS)
            requester.bringIntoView()
        }
    }
    bringIntoViewRequester(requester)
        .onFocusEvent { focused = it.isFocused }
        .onSizeChanged { if (focused) scope.launch { requester.bringIntoView() } }
}

/** About as long as the keyboard takes to slide in. */
private const val KEYBOARD_RISE_MILLIS = 280L

/**
 * For a field with several lines that still stands for one entry: Enter on a
 * keyboard with real keys sends it instead of starting a new line. (The keyboard
 * on screen gets its "done" key from the field's keyboard options.)
 */
fun Modifier.submitsOnEnter(onSubmit: () -> Unit): Modifier = onPreviewKeyEvent { event ->
    if (event.key == Key.Enter || event.key == Key.NumPadEnter) {
        if (event.type == KeyEventType.KeyDown) onSubmit()
        true
    } else {
        false
    }
}

/**
 * The app's text field: a filled slate box with no outline until it has focus.
 * It reads as part of the flat cards around it instead of as a drawn rectangle.
 */
@Composable
fun FfTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
    isError: Boolean = false,
    supportingText: String? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val colors = FfTheme.colors
    // Aero: milk glass rather than a solid box, so the picture is not cut off by it.
    val box = if (colors.isAero) colors.surface.copy(alpha = if (colors.isDark) 0.62f else 0.70f) else colors.surface
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().staysVisibleWhenFocused(),
        enabled = enabled,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it, color = colors.textFaint) } },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = FfShapes.field,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = colors.text,
            unfocusedTextColor = colors.text,
            disabledTextColor = colors.textMuted,
            focusedContainerColor = box,
            unfocusedContainerColor = box,
            disabledContainerColor = box,
            errorContainerColor = box,
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent,
            errorBorderColor = colors.error,
            cursorColor = colors.accent,
            focusedLabelColor = colors.accent,
            unfocusedLabelColor = colors.textMuted,
            errorLabelColor = colors.error,
            errorSupportingTextColor = colors.error,
            focusedSupportingTextColor = colors.textMuted,
            unfocusedSupportingTextColor = colors.textMuted,
        ),
    )
}

/** A row with a number that is changed by minus and plus. */
@Composable
fun StepperRow(
    title: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    valueLabel: (Int) -> String = { it.toString() },
    step: Int = 1,
    enabled: Boolean = true,
) {
    val colors = FfTheme.colors
    ListRow(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        titleColor = if (enabled) colors.text else colors.textMuted,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepButton(Icons.Filled.Remove, "Less", enabled && value - step >= range.first) {
                    onChange((value - step).coerceIn(range))
                }
                Text(
                    text = valueLabel(value),
                    style = FfType.rowTitle,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) colors.text else colors.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 44.dp).padding(horizontal = 6.dp),
                )
                StepButton(Icons.Filled.Add, "More", enabled && value + step <= range.last) {
                    onChange((value + step).coerceIn(range))
                }
            }
        },
    )
}

@Composable
internal fun StepButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = FfTheme.colors
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(colors.surfaceHigh)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (enabled) colors.text else colors.textFaint,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** One option of a [ChoiceDialog]. */
data class Choice<T>(
    val value: T,
    val label: String,
    val detail: String? = null,
)

/** Pick one of a few options, each with a line saying what it means. */
@Composable
fun <T> ChoiceDialog(
    title: String,
    choices: List<Choice<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FfTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceVariant,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = colors.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                choices.forEach { choice ->
                    val isSelected = choice.value == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(FfShapes.button)
                            .clickable { onSelect(choice.value) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                choice.label,
                                style = FfType.rowTitle,
                                color = if (isSelected) colors.accent else colors.text,
                            )
                            if (choice.detail != null) {
                                Text(choice.detail, style = FfType.rowDetail, color = colors.textMuted)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        if (isSelected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = colors.accent,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Spacer(Modifier.size(20.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
            }
        },
    )
}

/** A time of day in a dialog, in the app's colours. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FfTimePickerDialog(
    title: String,
    hour: Int,
    minute: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = "Set",
) {
    val colors = FfTheme.colors
    val state = androidx.compose.material3.rememberTimePickerState(
        initialHour = hour,
        initialMinute = minute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceVariant,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = colors.text) },
        text = {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                androidx.compose.material3.TimePicker(
                    state = state,
                    colors = androidx.compose.material3.TimePickerDefaults.colors(
                        clockDialColor = colors.surface,
                        selectorColor = colors.primary,
                        timeSelectorSelectedContainerColor = colors.container,
                        timeSelectorSelectedContentColor = colors.onContainer,
                        timeSelectorUnselectedContainerColor = colors.surface,
                        timeSelectorUnselectedContentColor = colors.text,
                        clockDialSelectedContentColor = colors.onPrimary,
                        clockDialUnselectedContentColor = colors.textSecondary,
                        containerColor = Color.Transparent,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text(confirmLabel, style = MaterialTheme.typography.labelLarge, color = colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
            }
        },
    )
}
