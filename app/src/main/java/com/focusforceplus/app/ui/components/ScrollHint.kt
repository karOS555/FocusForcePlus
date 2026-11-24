package com.focusforceplus.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import com.focusforceplus.app.ui.theme.FfTheme

/**
 * A small arrow at the bottom edge for as long as there is more below, bobbing
 * gently. A menu that ends exactly between two rows looks finished, and what is
 * under it goes unseen; the arrow goes away at the end of the content.
 *
 * It is only drawn, never in the way of a tap, and has to stand before the
 * modifier that scrolls, so that it stays put while the content moves.
 */
fun Modifier.scrollHint(moreBelow: Boolean): Modifier = composed {
    val colors = FfTheme.colors
    val arrow = rememberVectorPainter(Icons.Filled.KeyboardArrowDown)
    val shown by animateFloatAsState(if (moreBelow) 1f else 0f, tween(220), label = "scroll_hint_shown")
    val bob = if (moreBelow) {
        val nudge = rememberInfiniteTransition(label = "scroll_hint")
        nudge.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
            label = "scroll_hint_bob",
        ).value
    } else {
        0f
    }
    drawWithContent {
        drawContent()
        if (shown <= 0f) return@drawWithContent
        val disc = 26.dp.toPx()
        val icon = 20.dp.toPx()
        val centre = Offset(size.width / 2f, size.height - disc / 2f - 6.dp.toPx())
        drawCircle(colors.surfaceHigh.copy(alpha = 0.94f * shown), radius = disc / 2f, center = centre)
        translate(centre.x - icon / 2f, centre.y - icon / 2f + bob * 3.dp.toPx()) {
            with(arrow) { draw(Size(icon, icon), alpha = shown, colorFilter = ColorFilter.tint(colors.text)) }
        }
    }
}

fun Modifier.scrollHint(state: ScrollState): Modifier = scrollHint(state.canScrollForward)

fun Modifier.scrollHint(state: LazyListState): Modifier = scrollHint(state.canScrollForward)

/** A column that scrolls and says so while there is more below. */
fun Modifier.verticalScrollWithHint(): Modifier = composed {
    val state = rememberScrollState()
    scrollHint(state).verticalScroll(state)
}
