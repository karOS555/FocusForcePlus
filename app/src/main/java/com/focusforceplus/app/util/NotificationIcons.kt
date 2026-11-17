package com.focusforceplus.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.core.graphics.drawable.IconCompat

/**
 * Turns icons from the in-app catalogue into what a notification needs: a small
 * white glyph for the status bar and a round badge for the large icon.
 *
 * Drawing them from the same vectors the app shows means a todo with a phone icon
 * has that phone in its notification too, without a second set of drawables to
 * keep in step.
 */
object NotificationIcons {

    private val glyphCache = HashMap<String, IconCompat>()
    private val badgeCache = HashMap<String, Bitmap>()

    /** Status-bar icon: the bare glyph, white on transparent. */
    @Synchronized
    fun small(context: Context, key: String, vector: ImageVector): IconCompat =
        glyphCache.getOrPut(key) {
            val size = (24 * context.resources.displayMetrics.density).toInt().coerceAtLeast(48)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            drawVector(Canvas(bitmap), vector, size.toFloat(), 0f, 0xFFFFFFFF.toInt())
            IconCompat.createWithBitmap(bitmap)
        }

    /** Large icon: the glyph in [foreground] on a filled circle of [background]. */
    @Synchronized
    fun badge(
        context: Context,
        key: String,
        vector: ImageVector,
        background: Int,
        foreground: Int,
    ): Bitmap = badgeCache.getOrPut("$key/$background/$foreground") {
        val size = (48 * context.resources.displayMetrics.density).toInt().coerceAtLeast(96)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        drawVector(canvas, vector, size.toFloat(), size * 0.24f, foreground)
        bitmap
    }

    private fun drawVector(canvas: Canvas, vector: ImageVector, size: Float, padding: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        val inner = size - padding * 2
        canvas.save()
        canvas.translate(padding, padding)
        canvas.scale(inner / vector.viewportWidth, inner / vector.viewportHeight)
        drawNode(canvas, vector.root, paint)
        canvas.restore()
    }

    private fun drawNode(canvas: Canvas, node: VectorNode, paint: Paint) {
        when (node) {
            is VectorPath -> {
                val path = PathParser().addPathNodes(node.pathData).toPath().asAndroidPath()
                path.fillType = if (node.pathFillType == PathFillType.EvenOdd) {
                    Path.FillType.EVEN_ODD
                } else {
                    Path.FillType.WINDING
                }
                canvas.drawPath(path, paint)
            }
            is VectorGroup -> {
                canvas.save()
                canvas.translate(node.translationX, node.translationY)
                canvas.rotate(node.rotation, node.pivotX, node.pivotY)
                canvas.scale(node.scaleX, node.scaleY, node.pivotX, node.pivotY)
                for (child in node) drawNode(canvas, child, paint)
                canvas.restore()
            }
        }
    }
}
