package com.focusforceplus.app.ui.navigation

import androidx.compose.ui.graphics.RectangleShape
import com.focusforceplus.app.ui.theme.aeroGlass
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusforceplus.app.ui.theme.FfTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.launch

/** How many entries are fully visible when the bar has more than it can hold. */
private const val VISIBLE_WHEN_SCROLLING = 5.3f
private val DOCK_HEIGHT = 64.dp
private val EDGE_WIDTH = 22.dp
private const val GITHUB_URL = "https://github.com/karOS555/FocusForcePlus"

/**
 * The navigation bar. It looks and works like a normal bottom bar with up to five
 * entries; with more than that it scrolls sideways.
 *
 * Three things make the scrolling obvious rather than a secret: the sixth entry is
 * cut off at the edge instead of hidden, an arrow sits on whichever side has more,
 * and for the first few launches the bar slides a little and settles back.
 *
 * The last entry is always "Edit", which opens the screen for reordering, hiding
 * and switching off modules. Long-pressing any entry does the same.
 */
@Composable
fun Dock(
    entries: List<AppModule>,
    currentRoute: String?,
    onSelect: (AppModule) -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    playHint: Boolean = false,
    onHintPlayed: () -> Unit = {},
    onUserScrolled: () -> Unit = {},
) {
    val colors = FfTheme.colors
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val itemCount = entries.size + 1 // plus the Edit entry
    val owner = AppModule.owning(currentRoute)
    val selectedIndex = entries.indexOfFirst { it == owner }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(DOCK_HEIGHT)
            .then(if (colors.isAero) Modifier.aeroGlass(RectangleShape, tint = colors.surface.copy(alpha = 0.50f)) else Modifier.background(colors.dock)),
    ) {
        val scrolls = itemCount > 5
        val itemWidth: Dp = if (scrolls) maxWidth / VISIBLE_WHEN_SCROLLING else maxWidth / itemCount
        val itemWidthPx = with(density) { itemWidth.toPx() }

        // Keep the selected entry on screen when navigation happens elsewhere
        // (a card on Home, a notification).
        LaunchedEffect(selectedIndex, scrolls) {
            if (!scrolls || selectedIndex < 0) return@LaunchedEffect
            val visible = state.layoutInfo.visibleItemsInfo
            val info = visible.firstOrNull { it.index == selectedIndex }
            val viewportEnd = state.layoutInfo.viewportEndOffset
            val fullyVisible = info != null && info.offset >= 0 && info.offset + info.size <= viewportEnd
            if (!fullyVisible) state.animateScrollToItem((selectedIndex - 2).coerceAtLeast(0))
        }

        // Demonstrate the scrolling: slide over and back. Whatever interrupts it
        // (navigation, another scroll), the bar always returns to where it was,
        // unless the user has taken over with a finger.
        val hint by rememberUpdatedState(playHint)
        var userDragged by remember { mutableStateOf(false) }
        LaunchedEffect(scrolls) {
            if (!scrolls) return@LaunchedEffect
            delay(900)
            if (hint && state.canScrollForward && !state.isScrollInProgress && !userDragged) {
                val startIndex = state.firstVisibleItemIndex
                val startOffset = state.firstVisibleItemScrollOffset
                try {
                    state.animateScrollBy(itemWidthPx * 1.1f, tween(520))
                    delay(260)
                } finally {
                    withContext(NonCancellable) {
                        if (!userDragged) {
                            runCatching { state.animateScrollToItem(startIndex, startOffset) }
                        }
                    }
                }
                onHintPlayed()
            }
        }
        LaunchedEffect(state) {
            state.interactionSource.interactions.collect { interaction ->
                if (interaction is DragInteraction.Start) {
                    userDragged = true
                    onUserScrolled()
                }
            }
        }

        LazyRow(
            state = state,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            userScrollEnabled = scrolls,
        ) {
            itemsIndexed(entries, key = { _, module -> module.key }) { index, module ->
                DockItem(
                    icon = module.icon,
                    label = module.label,
                    selected = index == selectedIndex,
                    width = itemWidth,
                    onClick = { onSelect(module) },
                    onLongClick = onEdit,
                )
            }
            item(key = "edit") {
                DockItem(
                    icon = Icons.Filled.Tune,
                    label = "Edit",
                    selected = false,
                    width = itemWidth,
                    onClick = onEdit,
                    onLongClick = onEdit,
                )
            }
        }

        DockEdge(
            visible = scrolls && state.canScrollBackward,
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            description = "More to the left",
            modifier = Modifier.align(Alignment.CenterStart),
            onClick = { scope.launch { state.animateScrollBy(-itemWidthPx * 3) } },
        )
        DockEdge(
            visible = scrolls && state.canScrollForward,
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            description = "More to the right",
            modifier = Modifier.align(Alignment.CenterEnd),
            onClick = { scope.launch { state.animateScrollBy(itemWidthPx * 3) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    width: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = FfTheme.colors
    val tint = if (selected) colors.onContainer else colors.textMuted
    Column(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .combinedClickable(
                role = Role.Tab,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(28.dp)
                .clip(CircleShape)
                .background(if (selected) colors.container else androidx.compose.ui.graphics.Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            softWrap = false,
        )
    }
}

/** The arrow on a side of the bar that still has entries beyond the edge. */
@Composable
private fun DockEdge(
    visible: Boolean,
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FfTheme.colors
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(150)),
    ) {
        Box(
            modifier = Modifier
                .width(EDGE_WIDTH)
                .fillMaxHeight()
                .background(colors.dock)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = description, tint = colors.textFaint, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * One line under the bar: what the app is and where its source lives. It also
 * carries the inset for the system gesture area, so the bar itself can stay flush.
 */
@Composable
fun DockFooter(
    modifier: Modifier = Modifier,
    onRate: () -> Unit = {},
) {
    val colors = FfTheme.colors
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (colors.isAero) Modifier.aeroGlass(RectangleShape, tint = colors.surface.copy(alpha = 0.50f)) else Modifier.background(colors.dock))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Free & open source · karOS555",
            fontSize = 10.sp,
            lineHeight = 12.sp,
            color = colors.textFaint,
            maxLines = 1,
        )
        FooterLink("Source") { uriHandler.openUri(GITHUB_URL) }
        FooterLink("Rate", onClick = onRate)
    }
}

@Composable
private fun FooterLink(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.Medium,
        color = FfTheme.colors.accent,
        maxLines = 1,
        modifier = Modifier
            .padding(start = 4.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}
