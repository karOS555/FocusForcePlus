package com.focusforceplus.app.ui.guards

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.focusforceplus.app.ui.theme.FfTheme
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlin.random.Random

/**
 * The layouts a path can take, as corner points in a 0..1 box. Which one comes up,
 * and whether it is mirrored, is random, so the gesture can not turn into a habit:
 * you have to look at where the path goes before you can follow it.
 */
enum class PathLayout(internal val points: List<Offset>) {
    LINE(listOf(Offset(0f, 0.5f), Offset(1f, 0.5f))),
    CORNER(listOf(Offset(0f, 0f), Offset(1f, 0f), Offset(1f, 1f))),
    TURN(listOf(Offset(0f, 0f), Offset(1f, 0f), Offset(1f, 1f), Offset(0.3f, 1f))),
    STEP(listOf(Offset(0f, 0f), Offset(0.5f, 0f), Offset(0.5f, 1f), Offset(1f, 1f))),
}

/** One concrete path: a layout plus how it is flipped. */
data class PathSpec(
    val layout: PathLayout,
    val flipX: Boolean,
    val flipY: Boolean,
) {
    internal fun corners(): List<Offset> = layout.points.map { p ->
        Offset(if (flipX) 1f - p.x else p.x, if (flipY) 1f - p.y else p.y)
    }

    companion object {
        /** A path with bends, different on every call. */
        fun random(random: Random = Random.Default): PathSpec = PathSpec(
            layout = listOf(PathLayout.CORNER, PathLayout.TURN, PathLayout.STEP).random(random),
            flipX = random.nextBoolean(),
            flipY = random.nextBoolean(),
        )

        /** A straight slide whose direction is random. */
        fun randomLine(random: Random = Random.Default): PathSpec =
            PathSpec(PathLayout.LINE, flipX = random.nextBoolean(), flipY = false)
    }
}

/**
 * Drag the knob along the track to confirm.
 *
 * The knob only moves while the finger stays on the track, and letting go before
 * the end sends it back to the start. That makes it something you do on purpose:
 * it cannot be triggered by a stray tap or by a thumb that already knows where the
 * button is.
 *
 * For people using a screen reader or a switch the gesture is not required; the
 * control exposes a plain click action instead.
 */
@Composable
fun PathSlider(
    spec: PathSpec,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 132.dp,
    enabled: Boolean = true,
    actionLabel: String = "Mark as done",
) {
    val colors = FfTheme.colors
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val currentOnComplete by rememberUpdatedState(onComplete)

    val distance = remember(spec) { Animatable(0f) }
    var completed by remember(spec) { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var geometry by remember(spec) { mutableStateOf<Geometry?>(null) }

    val arrow = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowForward)
    val check = rememberVectorPainter(Icons.Filled.Check)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics {
                contentDescription = "$actionLabel: drag the knob along the path"
                if (enabled) onClick(label = actionLabel) {
                    if (!completed) {
                        completed = true
                        currentOnComplete()
                    }
                    true
                }
            }
            .pointerInput(spec, enabled) {
                if (!enabled) return@pointerInput
                var gripping = false
                detectDragGestures(
                    onDragStart = { start ->
                        val g = geometry
                        gripping = g != null && !completed &&
                            (start - g.positionAt(distance.value)).getDistance() <= g.track * 0.9f
                    },
                    onDrag = { change, _ ->
                        val g = geometry
                        if (!gripping || g == null || completed) return@detectDragGestures
                        change.consume()
                        val next = g.nearestDistance(change.position, distance.value)
                        if (next == null) {
                            // The finger left the track: let go of the knob.
                            gripping = false
                            scope.launch { distance.animateTo(0f, spring()) }
                        } else {
                            scope.launch { distance.snapTo(next) }
                            if (next >= g.length * 0.97f) {
                                completed = true
                                gripping = false
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch { distance.snapTo(g.length) }
                                currentOnComplete()
                            }
                        }
                    },
                    onDragEnd = {
                        gripping = false
                        if (!completed) scope.launch { distance.animateTo(0f, spring()) }
                    },
                    onDragCancel = {
                        gripping = false
                        if (!completed) scope.launch { distance.animateTo(0f, spring()) }
                    },
                )
            },
    ) {
        if (size != canvasSize || geometry == null) {
            canvasSize = size
            geometry = Geometry.build(spec, size, track = 52.dp.toPx(), corner = 40.dp.toPx())
        }
        val g = geometry ?: return@Canvas
        val knobRadius = 21.dp.toPx()

        // Track
        drawPath(
            path = g.path,
            color = colors.surfaceVariant,
            style = Stroke(width = g.track, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        // The part already travelled
        if (distance.value > 0.5f) {
            drawPath(
                path = g.segment(distance.value),
                color = colors.container,
                style = Stroke(width = g.track, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        // Where it ends
        if (!completed) {
            drawCircle(
                color = colors.outline,
                radius = 16.dp.toPx(),
                center = g.end,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
        // Knob
        val knob = g.positionAt(distance.value)
        drawCircle(color = if (enabled) colors.primary else colors.surfaceHigh, radius = knobRadius, center = knob)
        val iconSize = 22.dp.toPx()
        val painter = if (completed) check else arrow
        val angle = if (completed) 0f else g.angleAt(distance.value)
        rotate(degrees = angle, pivot = knob) {
            translate(left = knob.x - iconSize / 2, top = knob.y - iconSize / 2) {
                with(painter) {
                    draw(size = Size(iconSize, iconSize), colorFilter = ColorFilter.tint(colors.onPrimary))
                }
            }
        }
    }
}

/** The measured path for one canvas size. */
private class Geometry(
    val path: Path,
    val track: Float,
) {
    private val measure = PathMeasure().apply { setPath(path, false) }
    val length: Float = measure.length
    val end: Offset = measure.getPosition(length)

    fun positionAt(distance: Float): Offset = measure.getPosition(distance.coerceIn(0f, length))

    fun angleAt(distance: Float): Float {
        val tangent = measure.getTangent(distance.coerceIn(0f, length - 0.5f))
        return Math.toDegrees(atan2(tangent.y, tangent.x).toDouble()).toFloat()
    }

    fun segment(distance: Float): Path = Path().also {
        measure.getSegment(0f, distance.coerceIn(0f, length), it, true)
    }

    /**
     * The point on the path closest to [finger], searched only a little behind and
     * some way ahead of [current] so the knob cannot jump across a bend. Null when
     * the finger is too far from the track to still be holding the knob.
     */
    fun nearestDistance(finger: Offset, current: Float): Float? {
        val from = (current - track * 0.6f).coerceAtLeast(0f)
        val to = (current + track * 2.2f).coerceAtMost(length)
        var best = current
        var bestGap = Float.MAX_VALUE
        var d = from
        while (d <= to) {
            val gap = (measure.getPosition(d) - finger).getDistance()
            if (gap < bestGap) {
                bestGap = gap
                best = d
            }
            d += 3f
        }
        return if (bestGap <= track * 1.15f) best else null
    }

    companion object {
        fun build(spec: PathSpec, size: Size, track: Float, corner: Float): Geometry {
            val inset = track / 2
            val w = size.width - track
            val h = size.height - track
            val pts = spec.corners().map { Offset(inset + it.x * w, inset + it.y * h) }
            val path = Path()
            path.moveTo(pts.first().x, pts.first().y)
            for (i in 1 until pts.size) {
                val here = pts[i]
                if (i == pts.lastIndex) {
                    path.lineTo(here.x, here.y)
                } else {
                    // Round the bend: stop short of the corner, curve, continue.
                    val before = pts[i - 1]
                    val after = pts[i + 1]
                    val r = min(corner, min(span(before, here), span(here, after)) / 2f)
                    val entry = toward(here, before, r)
                    val exit = toward(here, after, r)
                    path.lineTo(entry.x, entry.y)
                    path.quadraticTo(here.x, here.y, exit.x, exit.y)
                }
            }
            return Geometry(path, track)
        }

        private fun span(a: Offset, b: Offset) = hypot(b.x - a.x, b.y - a.y)

        private fun toward(from: Offset, to: Offset, by: Float): Offset {
            val length = span(from, to).coerceAtLeast(0.001f)
            return Offset(from.x + (to.x - from.x) / length * by, from.y + (to.y - from.y) / length * by)
        }
    }
}
