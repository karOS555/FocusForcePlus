package com.focusforceplus.app.ui.components

import com.focusforceplus.app.ui.theme.aeroGlass
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.focusforceplus.app.ui.theme.FfDimens
import com.focusforceplus.app.ui.theme.FfShapes
import com.focusforceplus.app.ui.theme.FfTheme
import com.focusforceplus.app.ui.theme.FfType

// ── Card ──────────────────────────────────────────────────────────────────────

/**
 * The one card of the app: flat, no border, no shadow. Everything that groups
 * content sits on this, so a screen never mixes outlined boxes with filled ones.
 */
@Composable
fun FfCard(
    modifier: Modifier = Modifier,
    color: Color = FfTheme.colors.surface,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = FfTheme.colors.isAero
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Aero: frosted glass over the photo, lightly tinted with the card colour.
            .then(if (glass) Modifier.aeroGlass(FfShapes.card, tint = color.copy(alpha = color.alpha * 0.42f)) else Modifier)
            .clip(FfShapes.card)
            .then(if (glass) Modifier else Modifier.background(color))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

/** A card that lays its children out with the standard inner padding. */
@Composable
fun FfPaddedCard(
    modifier: Modifier = Modifier,
    color: Color = FfTheme.colors.surface,
    spacing: Dp = 12.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    FfCard(modifier = modifier, color = color, onClick = onClick) {
        Column(
            modifier = Modifier.padding(FfDimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

/** Renders [items] as rows of one card, separated by hairlines. */
@Composable
fun <T> CardList(
    items: List<T>,
    modifier: Modifier = Modifier,
    row: @Composable (T) -> Unit,
) {
    FfCard(modifier = modifier) {
        items.forEachIndexed { index, item ->
            if (index > 0) RowDivider()
            row(item)
        }
    }
}

@Composable
fun RowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = FfTheme.colors.divider)
}

// ── Row ───────────────────────────────────────────────────────────────────────

/**
 * Where a row sits when rows of a lazy list are meant to read as one card.
 * [InCard] draws no background at all, for rows placed inside an [FfCard].
 */
enum class RowPosition { InCard, Single, First, Middle, Last }

fun rowPosition(index: Int, count: Int): RowPosition = when {
    count <= 1 -> RowPosition.Single
    index == 0 -> RowPosition.First
    index == count - 1 -> RowPosition.Last
    else -> RowPosition.Middle
}

private fun RowPosition.shape(): Shape = when (this) {
    RowPosition.InCard, RowPosition.Middle -> RoundedCornerShape(0.dp)
    RowPosition.Single -> FfShapes.card
    RowPosition.First -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    RowPosition.Last -> RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
}

/**
 * The one list row of the app: an optional leading element, a title, one line of
 * detail, and one trailing action. Routines, todos, blocks, sessions and settings
 * all use it, which is what makes the app read as one piece.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleColor: Color = FfTheme.colors.textMuted,
    titleColor: Color = FfTheme.colors.text,
    struck: Boolean = false,
    subtitleMaxLines: Int = 2,
    position: RowPosition = RowPosition.InCard,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = FfTheme.colors
    val shape = position.shape()
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Aero: a stack of rows is one piece of glass, lit at the top.
            .then(
                when {
                    position == RowPosition.InCard -> Modifier
                    colors.isAero -> Modifier.aeroGlass(
                        shape,
                        tint = colors.surface.copy(alpha = colors.surface.alpha * 0.42f),
                        sheen = position == RowPosition.First || position == RowPosition.Single,
                        edge = position == RowPosition.Single,
                    )
                    else -> Modifier
                }
            )
            .clip(shape)
            .then(
                if (position == RowPosition.InCard || colors.isAero) Modifier else Modifier.background(colors.surface)
            )
            .then(
                when {
                    onClick != null && onLongClick != null ->
                        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                    onClick != null -> Modifier.clickable(onClick = onClick)
                    else -> Modifier
                }
            ),
    ) {
        if (position == RowPosition.Middle || position == RowPosition.Last) RowDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = FfDimens.rowMinHeight)
                .padding(horizontal = FfDimens.cardPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = FfType.rowTitle,
                    color = if (struck) colors.textMuted else titleColor,
                    textDecoration = if (struck) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = subtitle,
                        style = FfType.rowDetail,
                        color = subtitleColor,
                        maxLines = subtitleMaxLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
        }
    }
}

// ── Leading pieces ────────────────────────────────────────────────────────────

/** An icon on a round tonal background: the leading element of most rows. */
@Composable
fun IconCircle(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = FfDimens.iconCircle,
    container: Color = FfTheme.colors.container,
    tint: Color = FfTheme.colors.onContainer,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size * 0.53f),
        )
    }
}

// ── Headers ───────────────────────────────────────────────────────────────────

/** Heading above a card, with an optional text action on the right. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
            color = FfTheme.colors.text,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (actionLabel != null && onAction != null) {
            TextAction(label = actionLabel, onClick = onAction)
        }
    }
}

/** Quiet label that splits one list into groups (Overdue, Today, No date). */
@Composable
fun GroupLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = FfTheme.colors.textMuted,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

/** A number with a short label under it, for rows of key figures. */
@Composable
fun NumberTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = FfTheme.colors
    FfPaddedCard(modifier = modifier, spacing = 2.dp, onClick = onClick) {
        Text(
            value,
            style = FfType.number.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize),
            color = colors.text,
            maxLines = 1,
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 2)
    }
}
