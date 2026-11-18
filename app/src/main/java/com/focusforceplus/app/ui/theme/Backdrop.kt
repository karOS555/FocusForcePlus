package com.focusforceplus.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/**
 * What a whole screen sits on: the theme's background colour or, in the Aero
 * style, a scene in the spirit of Frutiger Aero: a bright sky with a sun glow,
 * swooshes of light, soap bubbles and green hills. All of it is drawn from code;
 * there is no picture in the app.
 */
@Composable
fun Modifier.ffBackdrop(): Modifier {
    val colors = FfTheme.colors
    if (!colors.isAero) return this.background(colors.background)
    return this.drawWithCache {
        val scene = AeroScene(size, colors, density)
        onDrawBehind { scene.draw(this) }
    }
}

/**
 * The shine on top of a filled Aero button: lighter at the top, a soft white
 * highlight over the upper half, a slightly darker rim. Draws nothing unless
 * [enabled]; use it with a see-through container.
 */
fun Modifier.glossyFill(base: Color, shape: Shape, enabled: Boolean): Modifier {
    if (!enabled) return this
    return this.drawWithCache {
        val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
        val body = Brush.verticalGradient(
            0f to lerp(base, Color.White, 0.32f),
            0.5f to base,
            1f to lerp(base, Color.Black, 0.10f),
        )
        val gloss = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.62f),
            0.46f to Color.White.copy(alpha = 0.14f),
            0.5f to Color.Transparent,
        )
        val rim = lerp(base, Color.Black, 0.28f)
        val stroke = Stroke(1.dp.toPx())
        onDrawBehind {
            drawPath(path, body)
            clipPath(path) { drawRect(gloss) }
            drawPath(path, rim, alpha = 0.5f, style = stroke)
        }
    }
}

/** A highlight laid over something already filled, like a floating button. */
fun Modifier.glossOverlay(shape: Shape, enabled: Boolean): Modifier {
    if (!enabled) return this
    return this.drawWithContent {
        drawContent()
        val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithContent)) }
        clipPath(path) {
            drawRect(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.55f),
                    0.46f to Color.White.copy(alpha = 0.12f),
                    0.5f to Color.Transparent,
                ),
            )
        }
    }
}

/** The glass edge and sheen of an Aero card. */
internal val GlassSheen = Brush.verticalGradient(
    0f to Color.White.copy(alpha = 0.50f),
    0.42f to Color.White.copy(alpha = 0.10f),
    1f to Color.Transparent,
)
internal val GlassEdge = Brush.verticalGradient(
    0f to Color.White.copy(alpha = 0.95f),
    1f to Color.White.copy(alpha = 0.35f),
)
internal val GlassShadow = Color(0x40104A70)

private class Bubble(val x: Float, val y: Float, val r: Float)

/** Fixed places, so the sky looks the same every time it is drawn. */
private val BUBBLES = listOf(
    // Kept off the top left, where titles and greetings sit.
    Bubble(0.86f, 0.22f, 0.055f), Bubble(0.24f, 0.29f, 0.024f), Bubble(0.80f, 0.36f, 0.075f),
    Bubble(0.66f, 0.24f, 0.030f), Bubble(0.90f, 0.53f, 0.040f), Bubble(0.07f, 0.52f, 0.036f),
    Bubble(0.36f, 0.61f, 0.052f), Bubble(0.58f, 0.112f, 0.020f), Bubble(0.94f, 0.69f, 0.026f),
    Bubble(0.22f, 0.76f, 0.030f), Bubble(0.62f, 0.68f, 0.022f), Bubble(0.45f, 0.40f, 0.017f),
    Bubble(0.72f, 0.46f, 0.014f), Bubble(0.44f, 0.028f, 0.014f), Bubble(0.15f, 0.90f, 0.028f),
    Bubble(0.52f, 0.86f, 0.020f),
)

private class AeroScene(size: Size, private val colors: AppColors, density: Float) {
    private val w = size.width
    private val h = size.height
    private val dp = density
    private val night = colors.isDark

    private val sky = Brush.verticalGradient(
        0f to lerp(colors.primary, Color.White, 0.10f),
        0.40f to lerp(colors.primary, Color.White, 0.52f),
        0.70f to lerp(colors.background, Color.White, if (night) 0.08f else 0.45f),
        1f to lerp(colors.background, Color.White, if (night) 0.04f else 0.30f),
        startY = 0f,
        endY = h,
    )
    private val sunCenter = Offset(w * 0.86f, h * 0.05f)
    private val sun = Brush.radialGradient(
        0f to Color.White.copy(alpha = if (night) 0.35f else 0.90f),
        0.18f to Color.White.copy(alpha = if (night) 0.14f else 0.40f),
        1f to Color.Transparent,
        center = sunCenter,
        radius = w * 0.85f,
    )
    private val rays: List<Path> = listOf(200f, 214f, 228f, 243f, 258f).map { angle ->
        val spread = 3.2f
        val len = h * 1.2f
        fun at(a: Float) = Offset(
            sunCenter.x + len * kotlin.math.cos(Math.toRadians(a.toDouble())).toFloat(),
            sunCenter.y - len * kotlin.math.sin(Math.toRadians(a.toDouble())).toFloat(),
        )
        val a = at(angle - spread)
        val b = at(angle + spread)
        Path().apply { moveTo(sunCenter.x, sunCenter.y); lineTo(a.x, a.y); lineTo(b.x, b.y); close() }
    }
    private val rayBrush = Brush.radialGradient(
        0f to Color.White.copy(alpha = if (night) 0.08f else 0.20f),
        1f to Color.Transparent,
        center = sunCenter,
        radius = h * 0.9f,
    )

    private val swooshes: List<Path> = listOf(
        floatArrayOf(0.46f, 0.30f, 0.16f, 0.52f, 0.26f),
        floatArrayOf(0.56f, 0.38f, 0.30f, 0.62f, 0.33f),
        floatArrayOf(0.36f, 0.20f, 0.08f, 0.40f, 0.16f),
    ).map { (y0, c1, c2, y1, y2) ->
        Path().apply {
            moveTo(-0.05f * w, y0 * h)
            cubicTo(0.30f * w, c1 * h, 0.62f * w, c2 * h + 0.25f * h, 1.05f * w, y2 * h)
            // a second, flatter pass makes the swoosh a slim crescent
            cubicTo(0.66f * w, c2 * h + 0.22f * h, 0.34f * w, c1 * h + 0.02f * h, -0.05f * w, y1 * h)
            close()
        }
    }
    private val swooshBrush = Brush.horizontalGradient(
        0f to Color.Transparent,
        0.30f to Color.White.copy(alpha = if (night) 0.12f else 0.50f),
        0.70f to Color.White.copy(alpha = if (night) 0.08f else 0.32f),
        1f to Color.Transparent,
    )

    private fun hill(base: Color) = if (night) lerp(base, colors.background, 0.55f) else base

    private val backHill = Path().apply {
        moveTo(0f, 0.80f * h)
        cubicTo(0.18f * w, 0.735f * h, 0.36f * w, 0.715f * h, 0.55f * w, 0.770f * h)
        cubicTo(0.72f * w, 0.820f * h, 0.86f * w, 0.760f * h, w, 0.730f * h)
        lineTo(w, h); lineTo(0f, h); close()
    }
    private val backRim = Path().apply {
        moveTo(0f, 0.80f * h)
        cubicTo(0.18f * w, 0.735f * h, 0.36f * w, 0.715f * h, 0.55f * w, 0.770f * h)
        cubicTo(0.72f * w, 0.820f * h, 0.86f * w, 0.760f * h, w, 0.730f * h)
    }
    private val backBrush = Brush.verticalGradient(
        0f to hill(Color(0xFFB4EC6E)),
        1f to hill(Color(0xFF4EA72E)),
        startY = 0.71f * h,
        endY = h,
    )
    private val frontHill = Path().apply {
        moveTo(0f, 0.885f * h)
        cubicTo(0.22f * w, 0.820f * h, 0.42f * w, 0.830f * h, 0.62f * w, 0.890f * h)
        cubicTo(0.78f * w, 0.935f * h, 0.90f * w, 0.880f * h, w, 0.860f * h)
        lineTo(w, h); lineTo(0f, h); close()
    }
    private val frontRim = Path().apply {
        moveTo(0f, 0.885f * h)
        cubicTo(0.22f * w, 0.820f * h, 0.42f * w, 0.830f * h, 0.62f * w, 0.890f * h)
        cubicTo(0.78f * w, 0.935f * h, 0.90f * w, 0.880f * h, w, 0.860f * h)
    }
    private val frontBrush = Brush.verticalGradient(
        0f to hill(Color(0xFF98DE58)),
        1f to hill(Color(0xFF2F8F2A)),
        startY = 0.82f * h,
        endY = h,
    )
    private val hillSheen = Brush.radialGradient(
        0f to Color.White.copy(alpha = if (night) 0.08f else 0.32f),
        1f to Color.Transparent,
        center = Offset(0.34f * w, 0.74f * h),
        radius = 0.42f * w,
    )

    fun draw(scope: DrawScope) = with(scope) {
        drawRect(sky)
        rays.forEach { drawPath(it, rayBrush) }
        drawRect(sun)
        swooshes.forEach { drawPath(it, swooshBrush) }
        BUBBLES.forEach { drawBubble(it) }
        drawPath(backHill, backBrush)
        drawRect(hillSheen, topLeft = Offset(0f, 0.70f * h), size = Size(w, 0.30f * h))
        drawPath(backRim, Color.White.copy(alpha = if (night) 0.12f else 0.45f), style = Stroke(2f * dp))
        drawPath(frontHill, frontBrush)
        drawPath(frontRim, Color.White.copy(alpha = if (night) 0.14f else 0.55f), style = Stroke(2.5f * dp))
    }

    private fun DrawScope.drawBubble(b: Bubble) {
        val c = Offset(b.x * w, b.y * h)
        val r = b.r * w
        val strength = if (night) 0.5f else 1f
        drawCircle(
            Brush.radialGradient(
                0f to Color.Transparent,
                0.62f to Color.White.copy(alpha = 0.04f * strength),
                0.90f to Color.White.copy(alpha = 0.26f * strength),
                1f to Color.White.copy(alpha = 0.55f * strength),
                center = c,
                radius = r,
            ),
            radius = r,
            center = c,
        )
        drawCircle(Color.White.copy(alpha = 0.60f * strength), radius = r, center = c, style = Stroke(maxOf(1.1f * dp, r * 0.035f)))
        drawCircle(Color.White.copy(alpha = 0.85f * strength), radius = r * 0.17f, center = Offset(c.x - r * 0.40f, c.y - r * 0.42f))
        drawCircle(Color.White.copy(alpha = 0.45f * strength), radius = r * 0.07f, center = Offset(c.x + r * 0.44f, c.y + r * 0.40f))
    }
}
