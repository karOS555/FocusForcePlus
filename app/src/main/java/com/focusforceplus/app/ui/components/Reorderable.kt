package com.focusforceplus.app.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * A column of rows that can be reordered by dragging a handle.
 *
 * Whenever the dragged row has travelled more than half of its neighbour, the two
 * swap and the rest of the way carries over. With a [rowHeight] every row is that
 * tall; with `null` each row is as tall as its content, so a long text can run
 * over several lines, and the heights are measured. [row] receives the modifier to
 * put on its drag handle.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    key: (T) -> Any,
    rowHeight: Dp?,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    row: @Composable (item: T, handle: Modifier, dragging: Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val fixedPx = rowHeight?.let { with(density) { it.toPx() } }
    val fallbackPx = with(density) { FALLBACK_ROW.toPx() }
    // Plain map on purpose: the heights are only read while a drag is running.
    val measured = remember { HashMap<Any, Int>() }
    var draggingKey by remember { mutableStateOf<Any?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    // A drag outlives the composition it began in: what it reads has to be current.
    val currentItems by rememberUpdatedState(items)
    val currentKey by rememberUpdatedState(key)
    val currentOnMove by rememberUpdatedState(onMove)

    fun heightOf(rowKey: Any): Float = fixedPx ?: measured[rowKey]?.toFloat() ?: fallbackPx

    Column(modifier = modifier.fillMaxWidth()) {
        items.forEach { item ->
            val itemKey = key(item)
            key(itemKey) {
                val dragging = draggingKey == itemKey
                val handle = Modifier.pointerInput(itemKey) {
                    // The order as this drag has left it. It is kept here instead of
                    // being read back from the items, which only catch up with a move
                    // on the next frame: a row that had just passed a taller one was
                    // measured against the wrong neighbour and jumped straight back.
                    var order = mutableListOf<Any>()
                    detectDragGestures(
                        onDragStart = {
                            order = currentItems.map { currentKey(it) }.toMutableList()
                            draggingKey = itemKey
                            offset = 0f
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            offset += amount.y
                            var index = order.indexOf(itemKey)
                            if (index >= 0) {
                                while (index < order.lastIndex) {
                                    val next = heightOf(order[index + 1])
                                    if (offset <= next / 2) break
                                    currentOnMove(index, index + 1)
                                    order.add(index + 1, order.removeAt(index))
                                    index++
                                    offset -= next
                                }
                                while (index > 0) {
                                    val previous = heightOf(order[index - 1])
                                    if (offset >= -previous / 2) break
                                    currentOnMove(index, index - 1)
                                    order.add(index - 1, order.removeAt(index))
                                    index--
                                    offset += previous
                                }
                            }
                        },
                        onDragEnd = { draggingKey = null; offset = 0f },
                        onDragCancel = { draggingKey = null; offset = 0f },
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (rowHeight != null) Modifier.height(rowHeight)
                            else Modifier.onSizeChanged { measured[itemKey] = it.height }
                        )
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (dragging) offset else 0f
                            alpha = if (dragging) 0.92f else 1f
                        },
                ) {
                    row(item, handle, dragging)
                }
            }
        }
    }
}

/** Assumed for a row that has not been measured yet. */
private val FALLBACK_ROW = 52.dp
