package com.focusforceplus.app.ui.theme

import androidx.compose.ui.platform.InspectorInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.findNearestAncestor
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.focusforceplus.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The photo behind the Aero style, and the credit its licence asks for. */
object AeroPhoto {
    const val TITLE = "\"Wandern am Untreusee\" by PantheraLeo1359531"
    const val LICENSE = "CC BY 4.0"
    const val CREDIT = "Background photo: $TITLE, $LICENSE (creativecommons.org/licenses/by/4.0), " +
        "cropped and scaled, with bubbles drawn over it."
    const val SOURCE = "https://commons.wikimedia.org/wiki/File:Wandern_am_Untreusee_20240608_HOF7926_RAW-Export.png"
}

/**
 * What a whole screen sits on: the theme's background colour or, in the Aero style,
 * a photo of a green hill under a summer sky (see [AeroPhoto]) with a few soap
 * bubbles drawn over it. Glass inside it shows a blurred copy (see [aeroGlass]).
 *
 * The scene is put together once per size, away from the main thread, and kept for
 * the next screen; until it is ready a gradient in its colours stands in.
 */
@Composable
fun Modifier.ffBackdrop(): Modifier {
    val colors = FfTheme.colors
    if (!colors.isAero) return this.background(colors.background)
    return this.then(BackdropElement(dark = colors.isDark, night = colors.background))
}

/**
 * Frosted glass: the blurred backdrop behind this element, a light [tint], a sheen
 * across the top and a bright [edge] ([sheen] off leaves just the frosting, for a
 * button that draws its own shine). Only in the Aero style. Outside a backdrop, in
 * a dialog for example, it is the tint, sheen and edge alone.
 */
@Composable
fun Modifier.aeroGlass(
    shape: Shape,
    tint: Color = FfTheme.colors.surface.copy(alpha = 0.30f),
    sheen: Boolean = true,
    edge: Boolean = sheen,
): Modifier = if (FfTheme.colors.isAero) this.then(GlassElement(shape, tint, sheen, edge)) else this

/**
 * A glossy Aero button: deep colour, lighter at the top, a crisp white highlight
 * over the upper half, a glow rising from the bottom and a darker rim. Draws
 * nothing unless [enabled]; use it with a see-through container.
 */
fun Modifier.glossyFill(base: Color, shape: Shape, enabled: Boolean): Modifier {
    if (!enabled) return this
    return this.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this@drawWithCache)
        val path = Path().apply { addOutline(outline) }
        val inset = 1.5.dp.toPx()
        val radius = (outline as? Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: (size.height / 2)
        val highlight = Path().apply {
            addRoundRect(
                RoundRect(
                    left = inset, top = inset, right = size.width - inset, bottom = size.height * 0.52f,
                    radiusX = max(0f, radius - inset), radiusY = max(0f, radius - inset),
                )
            )
        }
        val body = Brush.verticalGradient(
            0f to lerp(base, Color.White, 0.22f).copy(alpha = 0.86f),
            0.55f to base.copy(alpha = 0.80f),
            1f to lerp(base, Color.Black, 0.18f).copy(alpha = 0.92f),
        )
        val gloss = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.78f),
            1f to Color.White.copy(alpha = 0.10f),
            startY = inset,
            endY = size.height * 0.52f,
        )
        val glow = Brush.radialGradient(
            0f to lerp(base, Color.White, 0.65f).copy(alpha = 0.60f),
            1f to Color.Transparent,
            center = Offset(size.width / 2, size.height * 1.08f),
            radius = size.width * 0.55f,
        )
        val rim = lerp(base, Color.Black, 0.35f)
        val stroke = Stroke(1.dp.toPx())
        onDrawBehind {
            drawPath(path, body)
            clipPath(path) { drawRect(glow) }
            drawPath(highlight, gloss)
            drawPath(path, rim, alpha = 0.55f, style = stroke)
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
                    0f to Color.White.copy(alpha = 0.70f),
                    0.48f to Color.White.copy(alpha = 0.16f),
                    0.5f to Color.Transparent,
                ),
            )
            drawRect(
                Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.30f),
                    1f to Color.Transparent,
                    center = Offset(size.width / 2, size.height * 1.05f),
                    radius = size.width * 0.6f,
                ),
            )
        }
        drawPath(path, Color.White.copy(alpha = 0.55f), style = Stroke(1.dp.toPx()))
    }
}

/** The sheen and the light edge of Aero glass. */
internal val GlassSheen = Brush.verticalGradient(
    0f to Color.White.copy(alpha = 0.45f),
    0.40f to Color.White.copy(alpha = 0.08f),
    1f to Color.Transparent,
)
internal val GlassEdge = Brush.verticalGradient(
    0f to Color.White.copy(alpha = 0.95f),
    1f to Color.White.copy(alpha = 0.40f),
)

// ── Backdrop and glass ───────────────────────────────────────────────────────────

private object BackdropKey

private data class BackdropElement(val dark: Boolean, val night: Color) : ModifierNodeElement<BackdropNode>() {
    override fun create() = BackdropNode(dark, night)
    override fun update(node: BackdropNode) = node.update(dark, night)
    override fun InspectorInfo.inspectableProperties() {
        name = "ffBackdrop"
        properties["dark"] = dark
    }
}

/** Draws the scene, and is what glass inside it looks through. */
private class BackdropNode(private var dark: Boolean, private var night: Color) :
    Modifier.Node(), DrawModifierNode, LayoutAwareModifierNode, TraversableNode,
    CompositionLocalConsumerModifierNode {

    override val traverseKey: Any get() = BackdropKey

    /** Where the backdrop is, for glass inside it to line up with. */
    var coordinates: LayoutCoordinates? = null
        private set
    var measured = IntSize.Zero
        private set

    /**
     * The scene, once it is ready; a previous one stays up while a new one is made.
     * State, so that everything drawn with it (glass inside, too) is drawn again when it
     * changes, whichever layer it sits in.
     */
    var image: AeroBackdrop.Image? by mutableStateOf(null)
        private set
    private var loading: Job? = null

    fun update(dark: Boolean, night: Color) {
        if (dark == this.dark && night == this.night) return
        this.dark = dark
        this.night = night
        invalidateDraw()
        load()
    }

    override fun onAttach() = load()

    override fun onDetach() {
        loading = null
        coordinates = null
    }

    override fun onRemeasured(size: IntSize) {
        if (size == measured) return
        measured = size
        load()
    }

    override fun onPlaced(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
    }

    private fun load() {
        if (!isAttached || measured.width <= 0 || measured.height <= 0) return
        val spec = AeroBackdrop.Spec(measured.width, measured.height, if (dark) night.toArgb() else 0)
        if (image?.spec == spec) return
        loading?.cancel()
        val ready = AeroBackdrop.cached(spec)
        if (ready != null) {
            image = ready
            return
        }
        val context = currentValueOf(LocalContext).applicationContext
        loading = coroutineScope.launch { image = AeroBackdrop.render(context, spec) }
    }

    override fun ContentDrawScope.draw() {
        val scene = image?.scene
        if (scene != null) {
            drawImage(
                scene,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.Medium,
            )
        } else {
            drawRect(AeroBackdrop.placeholder)
            if (dark) drawRect(night.copy(alpha = AeroBackdrop.NIGHT))
        }
        drawContent()
    }
}

private data class GlassElement(val shape: Shape, val tint: Color, val sheen: Boolean, val edge: Boolean) :
    ModifierNodeElement<GlassNode>() {
    override fun create() = GlassNode(shape, tint, sheen, edge)
    override fun update(node: GlassNode) = node.update(shape, tint, sheen, edge)
    override fun InspectorInfo.inspectableProperties() {
        name = "aeroGlass"
        properties["shape"] = shape
        properties["tint"] = tint
    }
}

private class GlassNode(
    private var shape: Shape,
    private var tint: Color,
    private var sheen: Boolean,
    private var edge: Boolean,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {

    private var backdrop: BackdropNode? = null
    private var looked = false

    /** Where this element sits inside the backdrop. */
    private var offset = Offset.Zero
    private val path = Path()
    private var pathSize = Size.Unspecified
    private var pathDirection: LayoutDirection? = null

    fun update(shape: Shape, tint: Color, sheen: Boolean, edge: Boolean) {
        if (shape != this.shape) {
            this.shape = shape
            pathSize = Size.Unspecified
        }
        this.tint = tint
        this.sheen = sheen
        this.edge = edge
        invalidateDraw()
    }

    override fun onDetach() {
        backdrop = null
        looked = false
    }

    private fun backdrop(): BackdropNode? {
        if (!looked && isAttached) {
            backdrop = findNearestAncestor(BackdropKey) as? BackdropNode
            looked = true
        }
        return backdrop
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val origin = backdrop()?.coordinates ?: return
        if (!origin.isAttached || !coordinates.isAttached) return
        val at = origin.localPositionOf(coordinates, Offset.Zero)
        if (at != offset) {
            offset = at
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        if (size != pathSize || layoutDirection != pathDirection) {
            path.reset()
            path.addOutline(shape.createOutline(size, layoutDirection, this))
            pathSize = size
            pathDirection = layoutDirection
        }
        val backdrop = backdrop()
        clipPath(path) {
            val frosted = backdrop?.image?.frosted
            if (backdrop != null && frosted != null) {
                drawImage(
                    frosted,
                    dstOffset = IntOffset(-offset.x.roundToInt(), -offset.y.roundToInt()),
                    dstSize = backdrop.measured,
                    filterQuality = FilterQuality.Low,
                )
            }
            drawRect(tint)
            if (sheen) drawRect(GlassSheen)
        }
        if (edge) drawPath(path, GlassEdge, style = Stroke(1.dp.toPx()))
        drawContent()
    }
}

// ── The scene ─────────────────────────────────────────────────────────────────

/** Scenes put together for a few sizes at once (a screen and a small preview, say). */
internal object AeroBackdrop {
    /** One scene: its size in pixels and, for a dark theme, its background (0 by day). */
    data class Spec(val width: Int, val height: Int, val night: Int)

    class Image(val spec: Spec, val scene: ImageBitmap, val frosted: ImageBitmap)

    /** How far a dark theme dims the day. */
    const val NIGHT = 0.62f

    /** Stands in for the moment the photo takes: its colours, roughly. */
    val placeholder = Brush.verticalGradient(
        0f to Color(0xFFE4EEF8),
        0.22f to Color(0xFF6FA3E2),
        0.45f to Color(0xFF9CC6EE),
        0.47f to Color(0xFF8CC653),
        1f to Color(0xFF4E972B),
    )

    private const val KEEP = 3
    private val lock = Any()
    private val recent = ArrayDeque<Image>()
    private val pending = HashMap<Spec, Deferred<Image>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun cached(spec: Spec): Image? = synchronized(lock) { find(spec) }

    private fun find(spec: Spec): Image? = recent.firstOrNull { it.spec == spec }?.also {
        recent.remove(it)
        recent.addFirst(it)
    }

    suspend fun render(context: Context, spec: Spec): Image {
        val job = synchronized(lock) {
            find(spec)?.let { return it }
            pending.getOrPut(spec) {
                scope.async {
                    val image = build(context, spec)
                    synchronized(lock) {
                        pending.remove(spec)
                        recent.addFirst(image)
                        while (recent.size > KEEP) recent.removeLast()
                    }
                    image
                }
            }
        }
        return job.await()
    }

    private fun build(context: Context, spec: Spec): Image {
        // The photo has about 850 px of detail across a phone screen; more pixels than
        // that would only cost memory.
        val scale = min(1f, 864f / spec.width)
        val w = max(1, (spec.width * scale).roundToInt())
        val h = max(1, (spec.height * scale).roundToInt())
        val scene = try {
            paint(context, w, h, spec.night)
        } catch (e: OutOfMemoryError) {
            gradient(max(1, w / 4), max(1, h / 4), spec.night)
        } catch (e: RuntimeException) {
            gradient(max(1, w / 4), max(1, h / 4), spec.night)
        }
        return Image(spec, scene.asImageBitmap(), frost(scene).asImageBitmap())
    }

    private fun paint(context: Context, w: Int, h: Int, night: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val photo = decode(context, w, h)
        val placed = drawCover(canvas, photo, w, h)
        photo.recycle()
        softenForeground(canvas, bmp, placed)
        BUBBLES.forEach { drawBubble(canvas, it, w, h) }
        if (night != 0) canvas.drawColor(Color(night).copy(alpha = NIGHT).toArgb())
        return bmp
    }

    /** The photo, decoded no larger than it needs to be to cover [w] x [h]. */
    private fun decode(context: Context, w: Int, h: Int): Bitmap {
        val res = context.resources
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(res, R.drawable.aero_backdrop, bounds)
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Aero photo missing" }
        val cover = max(w / bounds.outWidth.toFloat(), h / bounds.outHeight.toFloat())
        var sample = 1
        while (cover * sample * 2 <= 1f) sample *= 2
        return checkNotNull(
            BitmapFactory.decodeResource(res, R.drawable.aero_backdrop, BitmapFactory.Options().apply { inSampleSize = sample })
        ) { "Aero photo unreadable" }
    }

    /** Draws the photo to fill [w] x [h], cut evenly; returns where it went. */
    private fun drawCover(c: Canvas, photo: Bitmap, w: Int, h: Int): RectF {
        val scale = max(w / photo.width.toFloat(), h / photo.height.toFloat())
        val dw = photo.width * scale
        val dh = photo.height * scale
        val left = (w - dw) / 2f
        val top = (h - dh) / 2f
        val placed = RectF(left, top, left + dw, top + dh)
        c.drawBitmap(photo, null, placed, Paint(Paint.FILTER_BITMAP_FLAG))
        return placed
    }

    /** Where the crest of the hill runs in the photo, as a share of its height. */
    private const val RIDGE = 0.62f

    /**
     * The grass in front goes out of focus, as a camera close to the ground would
     * leave it, under a little sun haze; the crest stays sharp. Text and glass over
     * the grass then sit on something calm.
     */
    private fun softenForeground(c: Canvas, scene: Bitmap, photo: RectF) {
        val w = scene.width
        val h = scene.height
        val from = photo.top + photo.height() * (RIDGE + 0.04f)
        val full = photo.top + photo.height() * (RIDGE + 0.22f)
        if (from >= h) return
        val sw = max(1, w / 8)
        val sh = max(1, h / 8)
        val small = Bitmap.createScaledBitmap(scene, sw, sh, true)
        val px = IntArray(sw * sh)
        small.getPixels(px, 0, sw, 0, 0, sw, sh)
        if (small !== scene) small.recycle()
        repeat(3) { boxBlur(px, sw, sh, 2) }
        val blurred = Bitmap.createBitmap(px, sw, sh, Bitmap.Config.ARGB_8888)
        val image = BitmapShader(blurred, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply { setScale(w / sw.toFloat(), h / sh.toFloat()) })
        }
        val fade = LinearGradient(0f, from, 0f, full, 0x00000000, 0xFF000000.toInt(), Shader.TileMode.CLAMP)
        val soft = Paint(Paint.FILTER_BITMAP_FLAG).apply { shader = ComposeShader(image, fade, PorterDuff.Mode.DST_IN) }
        c.drawRect(0f, from, w.toFloat(), h.toFloat(), soft)
        blurred.recycle()
        val haze = Paint().apply {
            shader = LinearGradient(0f, from, 0f, h.toFloat(), 0x00FFFFFF, 0x3DFFFFFF, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, from, w.toFloat(), h.toFloat(), haze)
    }

    /** If the photo cannot be had: its colours as a gradient. */
    private fun gradient(w: Int, h: Int, night: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val p = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                intArrayOf(0xFFE4EEF8.toInt(), 0xFF6FA3E2.toInt(), 0xFF9CC6EE.toInt(), 0xFF8CC653.toInt(), 0xFF4E972B.toInt()),
                floatArrayOf(0f, 0.22f, 0.45f, 0.47f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        if (night != 0) canvas.drawColor(Color(night).copy(alpha = NIGHT).toArgb())
        return bmp
    }

    private fun drawBubble(c: Canvas, b: Bubble, w: Int, h: Int) {
        val cx = b.x * w
        val cy = b.y * h
        val r = b.r * w
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // The skin: clear in the middle, catching light at the edge.
        p.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(0x00FFFFFF, 0x00FFFFFF, 0x18FFFFFF, 0x5CFFFFFF, 0x1CFFFFFF),
            floatArrayOf(0f, 0.55f, 0.82f, 0.97f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawCircle(cx, cy, r, p)
        // Soap-film colours along the rim.
        p.shader = SweepGradient(cx, cy, intArrayOf(0x5060E0FF, 0x3CFF7AD9, 0x3CFFE27A, 0x3C78FFC8, 0x5060E0FF), null)
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1.2f, r * 0.07f)
        c.drawCircle(cx, cy, r * 0.94f, p)
        p.shader = null
        p.color = 0xA8FFFFFF.toInt()
        p.strokeWidth = max(1f, r * 0.025f)
        c.drawCircle(cx, cy, r, p)
        // The window it reflects, and a small glint opposite.
        p.style = Paint.Style.FILL
        p.maskFilter = BlurMaskFilter(max(1f, r * 0.05f), BlurMaskFilter.Blur.NORMAL)
        p.color = 0xE0FFFFFF.toInt()
        c.save()
        c.rotate(-32f, cx - r * 0.42f, cy - r * 0.46f)
        c.drawOval(RectF(cx - r * 0.66f, cy - r * 0.58f, cx - r * 0.18f, cy - r * 0.34f), p)
        c.restore()
        p.color = 0x99FFFFFF.toInt()
        c.drawCircle(cx + r * 0.45f, cy + r * 0.42f, max(1f, r * 0.08f), p)
        p.maskFilter = null
    }

    /** The scene, small and blurred: what shows through glass. */
    private fun frost(scene: Bitmap): Bitmap {
        val w = max(1, scene.width / 12)
        val h = max(1, scene.height / 12)
        val small = Bitmap.createScaledBitmap(scene, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== scene) small.recycle()
        repeat(3) { boxBlur(px, w, h, 3) }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun boxBlur(px: IntArray, w: Int, h: Int, r: Int) {
        val tmp = IntArray(px.size)
        fun pass(src: IntArray, dst: IntArray, horizontal: Boolean) {
            val outer = if (horizontal) h else w
            val inner = if (horizontal) w else h
            for (o in 0 until outer) {
                for (i in 0 until inner) {
                    var a = 0
                    var red = 0
                    var green = 0
                    var blue = 0
                    for (k in -r..r) {
                        val j = (i + k).coerceIn(0, inner - 1)
                        val v = if (horizontal) src[o * w + j] else src[j * w + o]
                        a += v ushr 24
                        red += (v shr 16) and 0xFF
                        green += (v shr 8) and 0xFF
                        blue += v and 0xFF
                    }
                    val n = 2 * r + 1
                    val idx = if (horizontal) o * w + i else i * w + o
                    dst[idx] = ((a / n) shl 24) or ((red / n) shl 16) or ((green / n) shl 8) or (blue / n)
                }
            }
        }
        pass(px, tmp, horizontal = true)
        pass(tmp, px, horizontal = false)
    }
}

private class Bubble(val x: Float, val y: Float, val r: Float)

/** Fixed places, kept off the top left where titles and greetings sit. */
private val BUBBLES = listOf(
    Bubble(0.84f, 0.138f, 0.040f), Bubble(0.64f, 0.108f, 0.014f), Bubble(0.93f, 0.40f, 0.032f),
    Bubble(0.07f, 0.37f, 0.030f), Bubble(0.33f, 0.335f, 0.015f), Bubble(0.62f, 0.665f, 0.050f),
    Bubble(0.18f, 0.62f, 0.040f), Bubble(0.55f, 0.71f, 0.022f), Bubble(0.91f, 0.78f, 0.028f),
    Bubble(0.34f, 0.86f, 0.034f), Bubble(0.67f, 0.93f, 0.019f),
)
