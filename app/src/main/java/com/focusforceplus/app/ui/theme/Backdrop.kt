package com.focusforceplus.app.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
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
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
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

/**
 * The picture behind the Aero style and the credit its parts ask for. It is put
 * together from four photos; the full note with links ships in
 * assets/licenses/aero-photo.txt and can be read in the app.
 */
object AeroPhoto {
    /** Who made the parts, in one line, for a settings row. */
    const val SUMMARY = "From photos by Lee Ann Ratledge (CC BY 4.0), Marco Rosanova (CC BY 3.0), " +
        "Sam Renold Joshua (CC0) and NASA (public domain)"

    const val CREDIT = "Background: put together from \"Cumulus Humilis Clouds - Tennessee, USA\" by " +
        "Lee Ann Ratledge (CC BY 4.0), \"Xp Reloaded\" by Marco Rosanova (CC BY 3.0), \"Tall grass 404 HDR\" by " +
        "Sam Renold Joshua (CC0) and \"The Blue Marble\" by NASA (public domain). Cropped and recoloured, " +
        "with bubbles and light drawn in. Tap for the links."

    /** The note in the APK: every part with its source and licence. */
    const val NOTE_ASSET = "licenses/aero-photo.txt"
}

/**
 * What a whole screen sits on: the theme's background colour or, in the Aero style,
 * a picture in the manner of the wallpapers of that time: a summer sky, a green
 * meadow, the Earth as a glass ball and soap bubbles (see [AeroPhoto]). Glass
 * inside it shows a blurred copy ([aeroGlass]) or bends it like a lens
 * ([liquidGlass]).
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

/** Which edges of a piece of glass catch the light. Rows that make up one card share theirs. */
enum class GlassEdges { None, All, Top, Sides, Bottom }

/**
 * Aero glass: the blurred backdrop behind this element, a light [tint], soft light
 * from above ([sheen]) and a thin rim that catches the light on its [edges]. Only
 * in the Aero style. Outside a backdrop, in a dialog for example, it is the tint,
 * light and rim alone.
 */
@Composable
fun Modifier.aeroGlass(
    shape: Shape,
    tint: Color = FfTheme.colors.surface.copy(alpha = 0.32f),
    sheen: Boolean = true,
    edges: GlassEdges = if (sheen) GlassEdges.All else GlassEdges.None,
): Modifier {
    val colors = FfTheme.colors
    return if (colors.isAero) this.then(GlassElement(shape, tint, sheen, edges, colors.isDark, liquid = false)) else this
}

/**
 * Liquid glass, what buttons are made of in the Aero style: clear rather than
 * frosted. The backdrop shows through sharply and a little enlarged, the way a
 * thick lens bends what is behind it, under a [tint]. The body is a touch lighter
 * at the top and darker at the bottom, light pools along the lower edge, and a
 * rim catches it where it enters at the top left and leaves at the bottom right.
 * No band of gloss across the middle, which is what dated the old buttons.
 *
 * Outside a backdrop, in a dialog for example, it is the tint, the shading and the
 * rim alone.
 */
@Composable
fun Modifier.liquidGlass(shape: Shape, tint: Color): Modifier {
    val colors = FfTheme.colors
    return if (colors.isAero) {
        this.then(GlassElement(shape, tint, sheen = false, edges = GlassEdges.None, dark = colors.isDark, liquid = true))
    } else {
        this
    }
}

/**
 * A bar along an edge of the screen, like an editor's save bar: frosted glass in the
 * Aero style, the plain [AppColors.bar] colour otherwise.
 */
@Composable
fun Modifier.ffBar(): Modifier {
    val colors = FfTheme.colors
    return if (colors.isAero) {
        this.then(GlassElement(RectangleShape, colors.surface.copy(alpha = 0.40f), true, GlassEdges.None, colors.isDark, liquid = false))
    } else {
        this.background(colors.bar)
    }
}

/**
 * In the Aero style, content fades out over its last [height] instead of ending in a
 * hard line, where it stops above the floating bar.
 */
@Composable
fun Modifier.aeroFadeOut(height: Dp): Modifier {
    if (!FfTheme.colors.isAero) return this
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val fade = min(height.toPx(), size.height)
            val top = size.height - fade
            val mask = Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent, startY = top, endY = size.height)
            onDrawWithContent {
                drawContent()
                drawRect(mask, topLeft = Offset(0f, top), size = Size(size.width, fade), blendMode = BlendMode.DstIn)
            }
        }
}

/** A pill: the shape of buttons in the Aero style. */
val AeroPill = RoundedCornerShape(percent = 50)

// How much bigger the backdrop looks through liquid glass.
private const val LENS = 1.14f

// Liquid glass is lighter where the light comes in and darker where it has gone
// through the most glass. Soft enough that no line shows.
private val LiquidDepth = Brush.verticalGradient(
    0f to Color.White.copy(alpha = 0.20f),
    0.42f to Color.Transparent,
    1f to Color.Black.copy(alpha = 0.10f),
)

private fun glassLight(dark: Boolean) = Brush.verticalGradient(
    0f to Color.White.copy(alpha = if (dark) 0.10f else 0.26f),
    0.5f to Color.Transparent,
)

private fun rim(edges: GlassEdges, dark: Boolean): Brush {
    val k = if (dark) 0.5f else 1f
    fun white(a: Float) = Color.White.copy(alpha = a * k)
    return when (edges) {
        GlassEdges.All -> Brush.verticalGradient(0f to white(0.85f), 0.35f to white(0.26f), 0.7f to white(0.18f), 1f to white(0.44f))
        GlassEdges.Top -> Brush.verticalGradient(0f to white(0.85f), 0.6f to white(0.22f), 1f to white(0.18f))
        GlassEdges.Bottom -> Brush.verticalGradient(0f to white(0.18f), 0.5f to white(0.2f), 1f to white(0.44f))
        GlassEdges.Sides, GlassEdges.None -> SolidColor(white(0.18f))
    }
}

private val DayLight = glassLight(dark = false)
private val NightLight = glassLight(dark = true)
private val DayRims = GlassEdges.entries.associateWith { rim(it, dark = false) }
private val NightRims = GlassEdges.entries.associateWith { rim(it, dark = true) }

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

private data class GlassElement(
    val shape: Shape,
    val tint: Color,
    val sheen: Boolean,
    val edges: GlassEdges,
    val dark: Boolean,
    /** Clear glass that bends the backdrop (buttons) instead of frosting it (cards). */
    val liquid: Boolean,
) : ModifierNodeElement<GlassNode>() {
    override fun create() = GlassNode(shape, tint, sheen, edges, dark, liquid)
    override fun update(node: GlassNode) = node.update(shape, tint, sheen, edges, dark, liquid)
    override fun InspectorInfo.inspectableProperties() {
        name = "aeroGlass"
        properties["shape"] = shape
        properties["tint"] = tint
        properties["edges"] = edges
    }
}

private class GlassNode(
    private var shape: Shape,
    private var tint: Color,
    private var sheen: Boolean,
    private var edges: GlassEdges,
    private var dark: Boolean,
    private var liquid: Boolean,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {

    private var backdrop: BackdropNode? = null
    private var looked = false

    /** Where this element sits inside the backdrop. */
    private var offset = Offset.Zero
    private val path = Path()
    private val rimPath = Path()
    private var pathSize = Size.Unspecified
    private var pathDirection: LayoutDirection? = null

    // Liquid glass: light that depends on the size of the piece.
    private var liquidRim: Brush? = null
    private var liquidPool: Brush? = null
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun update(shape: Shape, tint: Color, sheen: Boolean, edges: GlassEdges, dark: Boolean, liquid: Boolean) {
        if (shape != this.shape || edges != this.edges || liquid != this.liquid) pathSize = Size.Unspecified
        this.shape = shape
        this.tint = tint
        this.sheen = sheen
        this.edges = edges
        this.dark = dark
        this.liquid = liquid
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
            val outline = shape.createOutline(size, layoutDirection, this)
            path.reset()
            path.addOutline(outline)
            rimPath.reset()
            rimPath.addRim(outline, edges)
            pathSize = size
            pathDirection = layoutDirection
            liquidRim = null
            liquidPool = null
        }
        val backdrop = backdrop()
        if (liquid) {
            drawLiquid(backdrop)
            return
        }
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
            if (sheen) drawRect(if (dark) NightLight else DayLight)
        }
        drawContent()
        // The rim goes over the content, so whatever sits at the edge (the arrow at
        // the end of the bar, say) does not cut it.
        if (edges != GlassEdges.None) drawRim()
    }

    private fun DrawScope.drawRim() {
        val brush = (if (dark) NightRims else DayRims).getValue(edges)
        drawPath(rimPath, brush, style = Stroke(1.dp.toPx()))
    }

    /** See [liquidGlass]. */
    private fun ContentDrawScope.drawLiquid(backdrop: BackdropNode?) {
        val clear = backdrop?.image?.clear
        // A soft shadow lifts the piece off the picture. Only over a backdrop: the
        // lens image covers what would otherwise show through the glass.
        if (backdrop != null && clear != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            shadowPaint.color = android.graphics.Color.argb(if (dark) 96 else 56, 0, 20, 40)
            shadowPaint.maskFilter = BlurMaskFilter(7.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
            drawIntoCanvas { canvas ->
                canvas.save()
                canvas.translate(0f, 2.5.dp.toPx())
                canvas.nativeCanvas.drawPath(path.asAndroidPath(), shadowPaint)
                canvas.restore()
            }
        }
        clipPath(path) {
            if (backdrop != null && clear != null) {
                scale(LENS, LENS, pivot = center) {
                    drawImage(
                        clear,
                        dstOffset = IntOffset(-offset.x.roundToInt(), -offset.y.roundToInt()),
                        dstSize = backdrop.measured,
                        filterQuality = FilterQuality.Medium,
                    )
                }
            }
            // On a sheet or dialog there is no picture to show through, only a plain
            // surface: a coloured button is then nearly solid, or its light label
            // would sit on something almost as light.
            val solid = (backdrop == null || clear == null) && tint.alpha >= 0.5f
            drawRect(if (solid) tint.copy(alpha = 0.94f) else tint)
            drawRect(LiquidDepth)
            // Light pools along the lower edge, where it leaves the glass.
            val pool = liquidPool ?: Brush.radialGradient(
                0f to Color.White.copy(alpha = if (dark) 0.14f else 0.26f),
                1f to Color.Transparent,
                center = Offset(size.width / 2f, size.height * 1.08f),
                radius = max(size.width * 0.46f, size.height * 0.9f),
            ).also { liquidPool = it }
            drawRect(pool)
        }
        drawContent()
        // The rim: bright where the light comes in (top left) and where it leaves
        // (bottom right), faint in between. Angles run clockwise from the right.
        val k = if (dark) 0.62f else 1f
        val rim = liquidRim ?: Brush.sweepGradient(
            0.000f to Color.White.copy(alpha = 0.22f * k),
            0.125f to Color.White.copy(alpha = 0.78f * k),
            0.250f to Color.White.copy(alpha = 0.34f * k),
            0.500f to Color.White.copy(alpha = 0.30f * k),
            0.625f to Color.White.copy(alpha = 0.98f * k),
            0.750f to Color.White.copy(alpha = 0.62f * k),
            0.875f to Color.White.copy(alpha = 0.24f * k),
            1.000f to Color.White.copy(alpha = 0.22f * k),
            center = center,
        ).also { liquidRim = it }
        drawPath(path, rim, style = Stroke(1.25.dp.toPx()))
    }
}

/**
 * The part of an outline that [edges] asks for, open where rows of one card meet so
 * that the rim runs on around the whole card instead of drawing a line between rows.
 */
private fun Path.addRim(outline: Outline, edges: GlassEdges) {
    if (edges == GlassEdges.None) return
    val rr = when (outline) {
        is Outline.Rounded -> outline.roundRect
        is Outline.Rectangle -> RoundRect(outline.rect, CornerRadius.Zero)
        is Outline.Generic -> {
            if (edges == GlassEdges.All) addPath(outline.path)
            return
        }
    }
    if (edges == GlassEdges.All) {
        addRoundRect(rr)
        return
    }
    val l = rr.left
    val t = rr.top
    val r = rr.right
    val b = rr.bottom
    when (edges) {
        GlassEdges.Top -> {
            moveTo(l, b)
            lineTo(l, t + rr.topLeftCornerRadius.y)
            corner(Rect(l, t, l + 2 * rr.topLeftCornerRadius.x, t + 2 * rr.topLeftCornerRadius.y), 180f, 90f)
            lineTo(r - rr.topRightCornerRadius.x, t)
            corner(Rect(r - 2 * rr.topRightCornerRadius.x, t, r, t + 2 * rr.topRightCornerRadius.y), 270f, 90f)
            lineTo(r, b)
        }
        GlassEdges.Bottom -> {
            moveTo(l, t)
            lineTo(l, b - rr.bottomLeftCornerRadius.y)
            corner(Rect(l, b - 2 * rr.bottomLeftCornerRadius.y, l + 2 * rr.bottomLeftCornerRadius.x, b), 180f, -90f)
            lineTo(r - rr.bottomRightCornerRadius.x, b)
            corner(Rect(r - 2 * rr.bottomRightCornerRadius.x, b - 2 * rr.bottomRightCornerRadius.y, r, b), 90f, -90f)
            lineTo(r, t)
        }
        else -> {
            moveTo(l, t)
            lineTo(l, b)
            moveTo(r, t)
            lineTo(r, b)
        }
    }
}

/** A rounded corner of a rim; nothing for a square one. */
private fun Path.corner(oval: Rect, start: Float, sweep: Float) {
    if (oval.width > 0f && oval.height > 0f) arcTo(oval, start, sweep, forceMoveTo = false)
}

// ── The scene ─────────────────────────────────────────────────────────────────

/** Scenes put together for a few sizes at once (a screen and a small preview, say). */
internal object AeroBackdrop {
    /** One scene: its size in pixels and, for a dark theme, its background (0 by day). */
    data class Spec(val width: Int, val height: Int, val night: Int)

    /**
     * [scene] is what the screen shows; [frosted] is what frosted glass shows, small
     * and blurred; [clear] is what liquid glass looks through, only a little soft.
     */
    class Image(val spec: Spec, val scene: ImageBitmap, val frosted: ImageBitmap, val clear: ImageBitmap)

    /** How far a dark theme dims the day. */
    const val NIGHT = 0.62f

    /** The picture's colours, top to bottom: sky, haze at the horizon, meadow. Stands in while it loads. */
    private val COLOURS = intArrayOf(
        0xFF3892D9.toInt(), 0xFFAACDE7.toInt(), 0xFFC9E8F7.toInt(), 0xFF9FD243.toInt(), 0xFF6DA00D.toInt(), 0xFF82C53F.toInt(),
    )
    private val STOPS = floatArrayOf(0f, 0.3f, 0.59f, 0.63f, 0.8f, 1f)

    val placeholder = Brush.verticalGradient(*STOPS.indices.map { STOPS[it] to Color(COLOURS[it]) }.toTypedArray())

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
        return Image(spec, scene.asImageBitmap(), frost(scene).asImageBitmap(), clear(scene).asImageBitmap())
    }

    private fun paint(context: Context, w: Int, h: Int, night: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val photo = decode(context, w, h)
        drawCover(canvas, photo, w, h)
        photo.recycle()
        if (night != 0) canvas.drawColor(Color(night).copy(alpha = NIGHT).toArgb())
        return bmp
    }

    /** The photo, decoded no larger than it needs to be to cover [w] x [h]. */
    private fun decode(context: Context, w: Int, h: Int): Bitmap {
        val res = context.resources
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(res, R.drawable.aero_backdrop, bounds)
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Aero picture missing" }
        val cover = max(w / bounds.outWidth.toFloat(), h / bounds.outHeight.toFloat())
        var sample = 1
        while (cover * sample * 2 <= 1f) sample *= 2
        return checkNotNull(
            BitmapFactory.decodeResource(res, R.drawable.aero_backdrop, BitmapFactory.Options().apply { inSampleSize = sample })
        ) { "Aero picture unreadable" }
    }

    /** Draws the photo to fill [w] x [h], cut evenly. */
    private fun drawCover(c: Canvas, photo: Bitmap, w: Int, h: Int) {
        val scale = max(w / photo.width.toFloat(), h / photo.height.toFloat())
        val dw = photo.width * scale
        val dh = photo.height * scale
        val left = (w - dw) / 2f
        val top = (h - dh) / 2f
        c.drawBitmap(photo, null, RectF(left, top, left + dw, top + dh), Paint(Paint.FILTER_BITMAP_FLAG))
    }

    /** If the photo cannot be had: its colours as a gradient. */
    private fun gradient(w: Int, h: Int, night: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val p = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), COLOURS, STOPS, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        if (night != 0) canvas.drawColor(Color(night).copy(alpha = NIGHT).toArgb())
        return bmp
    }

    /**
     * The scene, small and blurred: what shows through frosted glass. Blurred far
     * enough that text on it reads calmly, with the shapes behind still there as
     * soft colour, and a little more vivid, the way coloured glass deepens what is
     * seen through it.
     */
    private fun frost(scene: Bitmap): Bitmap = soften(scene, shrink = 8, passes = 3, radius = 2, saturation = 1.25f)

    /** What liquid glass looks through: softer than the scene, clearer than frosted glass. */
    private fun clear(scene: Bitmap): Bitmap = soften(scene, shrink = 5, passes = 2, radius = 2, saturation = 1.15f)

    private fun soften(scene: Bitmap, shrink: Int, passes: Int, radius: Int, saturation: Float): Bitmap {
        val w = max(1, scene.width / shrink)
        val h = max(1, scene.height / shrink)
        val small = Bitmap.createScaledBitmap(scene, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== scene) small.recycle()
        repeat(passes) { boxBlur(px, w, h, radius) }
        val blurred = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        val vivid = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val saturate = Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(saturation) }) }
        Canvas(vivid).drawBitmap(blurred, 0f, 0f, saturate)
        blurred.recycle()
        return vivid
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
