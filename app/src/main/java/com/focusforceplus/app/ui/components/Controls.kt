package com.focusforceplus.app.ui.components

import com.focusforceplus.app.ui.theme.AeroPill
import com.focusforceplus.app.ui.theme.GlassEdges
import com.focusforceplus.app.ui.theme.aeroGlass
import com.focusforceplus.app.ui.theme.liquidGlass
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.focusforceplus.app.ui.theme.FfDimens
import com.focusforceplus.app.ui.theme.FfShapes
import com.focusforceplus.app.ui.theme.FfTheme

// ── Buttons ───────────────────────────────────────────────────────────────────
//
// Three levels and no more: filled for the one thing a screen is about, tonal for
// the alternative next to it, outlined for the way out.

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    containerColor: Color = FfTheme.colors.primary,
    contentColor: Color = FfTheme.colors.onPrimary,
) {
    val aero = FfTheme.colors.isAero
    val shape = if (aero) AeroPill else FfShapes.button
    Button(
        onClick = onClick,
        modifier = modifier
            .height(FfDimens.buttonHeight)
            // Aero: a pill of liquid glass in the button's colour, with the picture
            // behind it showing through, or clear glass while it cannot be pressed.
            .then(
                when {
                    !aero -> Modifier
                    enabled -> Modifier.liquidGlass(shape, tint = containerColor.copy(alpha = AERO_FILL))
                    else -> Modifier.liquidGlass(shape, tint = FfTheme.colors.surface.copy(alpha = 0.16f))
                }
            ),
        enabled = enabled && !loading,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (aero) Color.Transparent else containerColor,
            contentColor = contentColor,
            disabledContainerColor = if (aero) Color.Transparent else FfTheme.colors.surfaceHigh,
            disabledContentColor = if (aero && loading) contentColor else FfTheme.colors.textFaint,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = contentColor,
                strokeWidth = 2.dp,
            )
        } else {
            ButtonContent(text, icon, if (aero && enabled) ButtonLabel.OnColour else ButtonLabel.Plain)
        }
    }
}

@Composable
fun TonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val aero = FfTheme.colors.isAero
    val shape = if (aero) AeroPill else FfShapes.button
    Button(
        onClick = onClick,
        modifier = modifier
            .height(FfDimens.buttonHeight)
            // Aero: a pill of clear liquid glass.
            .then(if (aero) Modifier.liquidGlass(shape, tint = FfTheme.colors.surface.copy(alpha = 0.24f)) else Modifier),
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (aero) Color.Transparent else FfTheme.colors.container,
            contentColor = FfTheme.colors.onContainer,
            disabledContainerColor = if (aero) Color.Transparent else FfTheme.colors.surfaceVariant,
            disabledContentColor = FfTheme.colors.textFaint,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) { ButtonContent(text, icon, if (aero) ButtonLabel.OnGlass else ButtonLabel.Plain) }
}

@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    contentColor: Color = FfTheme.colors.text,
) {
    val aero = FfTheme.colors.isAero
    val shape = if (aero) AeroPill else FfShapes.button
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .height(FfDimens.buttonHeight)
            // Aero: glass with next to no tint; its rim is the outline.
            .then(if (aero) Modifier.liquidGlass(shape, tint = FfTheme.colors.surface.copy(alpha = 0.08f)) else Modifier),
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = contentColor,
            disabledContentColor = FfTheme.colors.textFaint,
        ),
        border = if (aero) null else androidx.compose.foundation.BorderStroke(1.dp, FfTheme.colors.outline),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) { ButtonContent(text, icon, if (aero) ButtonLabel.OnGlass else ButtonLabel.Plain) }
}

/**
 * How strongly a filled button's colour shows in liquid glass: clearly that colour,
 * with the picture behind it still coming through.
 */
private const val AERO_FILL = 0.64f

/** What a button's label sits on, which decides what keeps it readable. */
private enum class ButtonLabel {
    /** A solid button: nothing needed. */
    Plain,

    /** Coloured liquid glass: a soft dark edge under the light label. */
    OnColour,

    /** Clear glass over the picture: the theme's glow, like any text on the picture. */
    OnGlass,
}

/** The round button of a list page that adds a new entry. */
@Composable
fun FfFab(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Add,
) {
    val colors = FfTheme.colors
    val aero = colors.isAero
    val shape = if (aero) CircleShape else MaterialTheme.shapes.large
    FloatingActionButton(
        onClick = onClick,
        // Aero: a drop of liquid glass in the accent colour. It brings its own soft
        // shadow; the usual one would show through the glass.
        modifier = modifier.then(if (aero) Modifier.liquidGlass(shape, tint = colors.primary.copy(alpha = AERO_FILL)) else Modifier),
        containerColor = if (aero) Color.Transparent else colors.primary,
        contentColor = colors.onPrimary,
        shape = shape,
        elevation = if (aero) FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
                    else FloatingActionButtonDefaults.elevation(),
    ) { Icon(icon, contentDescription = contentDescription) }
}

@Composable
private fun ButtonContent(text: String, icon: ImageVector?, label: ButtonLabel = ButtonLabel.Plain) {
    if (icon != null) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
    }
    val base = MaterialTheme.typography.labelLarge
    Text(
        text = text,
        style = when (label) {
            ButtonLabel.Plain -> base.copy(shadow = null)
            ButtonLabel.OnColour -> base.copy(shadow = Shadow(Color.Black.copy(alpha = 0.34f), Offset(0f, 1.5f), 4f))
            ButtonLabel.OnGlass -> base
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** A text link: "Manage", "View all", "New block". */
@Composable
fun TextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = FfTheme.colors.accent,
    enabled: Boolean = true,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            fontSize = androidx.compose.ui.unit.TextUnit(13.5f, androidx.compose.ui.unit.TextUnitType.Sp),
        ),
        color = if (enabled) color else FfTheme.colors.textFaint,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

// ── Switch ────────────────────────────────────────────────────────────────────

@Composable
fun FfSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = FfTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = colors.onPrimary,
            checkedTrackColor = colors.primary,
            checkedBorderColor = colors.primary,
            uncheckedThumbColor = colors.textMuted,
            uncheckedTrackColor = colors.surfaceHigh,
            uncheckedBorderColor = colors.surfaceHigh,
            disabledCheckedTrackColor = colors.primary.copy(alpha = 0.4f),
            disabledCheckedThumbColor = colors.onPrimary.copy(alpha = 0.7f),
            disabledUncheckedTrackColor = colors.surfaceVariant,
            disabledUncheckedThumbColor = colors.textFaint,
            disabledUncheckedBorderColor = colors.surfaceVariant,
        ),
    )
}

// ── Chip ──────────────────────────────────────────────────────────────────────

/** A choice chip: outlined when off, tonal when on. Used for filters and options. */
@Composable
fun FfChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = FfTheme.colors
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        label = { Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = androidx.compose.ui.unit.TextUnit(13f, androidx.compose.ui.unit.TextUnitType.Sp))) },
        shape = FfShapes.chip,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = colors.textSecondary,
            selectedContainerColor = colors.container,
            selectedLabelColor = colors.onContainer,
            disabledLabelColor = colors.textFaint,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = colors.outline,
            selectedBorderColor = Color.Transparent,
            disabledBorderColor = colors.divider,
        ),
    )
}

// ── Completion ring ───────────────────────────────────────────────────────────

/**
 * The round checkbox of a todo. Its colour *is* the priority, so a row needs no
 * extra badge: grey for low, amber for medium, red for high.
 */
@Composable
fun CompletionRing(
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = FfTheme.colors.textFaint,
    enabled: Boolean = true,
    contentDescription: String = "Mark as done",
) {
    val colors = FfTheme.colors
    val fill by animateColorAsState(
        targetValue = if (checked) colors.success else Color.Transparent,
        animationSpec = tween(160),
        label = "ring_fill",
    )
    val checkScale by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(160),
        label = "ring_check",
    )
    // 24dp visible ring inside a 40dp touch target.
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                role = Role.Checkbox,
                interactionSource = remember { MutableInteractionSource() },
                indication = androidx.compose.material3.ripple(bounded = true),
                onClick = onClick,
            )
            .semantics {
                stateDescription = if (checked) "Done" else "Not done"
                role = Role.Checkbox
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(fill)
                .border(2.dp, if (checked) colors.success else color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = contentDescription,
                tint = colors.background,
                modifier = Modifier
                    .size(16.dp)
                    .scale(checkScale),
            )
        }
    }
}

// ── Search field ──────────────────────────────────────────────────────────────

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = FfTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .then(
                if (colors.isAero) Modifier.aeroGlass(FfShapes.field, tint = colors.surface.copy(alpha = 0.40f), sheen = false, edges = GlassEdges.All)
                else Modifier
            )
            .clip(FfShapes.field)
            .then(if (colors.isAero) Modifier else Modifier.background(colors.surface)),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.text),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions.Default,
        decorationBox = { inner ->
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = colors.textFaint,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textFaint,
                        )
                    }
                    inner()
                }
                if (value.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { onValueChange("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Clear search",
                            tint = colors.textMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                if (trailing != null) trailing()
            }
        },
    )
}
