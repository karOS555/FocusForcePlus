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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex

/**
 * A column of equally tall rows that can be reordered by dragging a handle.
 *
 * Because every row has the same height, the drop position is plain arithmetic:
 * whenever the dragged row has travelled more than half a row, it swaps with its
 * neighbour and the remaining offset carries over. [row] receives the modifier to
 * put on its drag handle.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    key: (T) -> Any,
    rowHeight: Dp,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    row: @Composable (item: T, handle: Modifier, dragging: Boolean) -> Unit,
) {
    val rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() }
    var draggingKey by remember { mutableStateOf<Any?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val currentItems by rememberUpdatedState(items)
    val currentOnMove by rememberUpdatedState(onMove)

    Column(modifier = modifier.fillMaxWidth()) {
        items.forEach { item ->
            val itemKey = key(item)
            key(itemKey) {
                val dragging = draggingKey == itemKey
                val handle = Modifier.pointerInput(itemKey) {
                    detectDragGestures(
                        onDragStart = {
                            draggingKey = itemKey
                            offset = 0f
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            offset += amount.y
                            var index = currentItems.indexOfFirst { key(it) == itemKey }
                            while (offset > rowHeightPx / 2 && index in 0 until currentItems.lastIndex) {
                                currentOnMove(index, index + 1)
                                index++
                                offset -= rowHeightPx
                            }
                            while (offset < -rowHeightPx / 2 && index > 0) {
                                currentOnMove(index, index - 1)
                                index--
                                offset += rowHeightPx
                            }
                        },
                        onDragEnd = { draggingKey = null; offset = 0f },
                        onDragCancel = { draggingKey = null; offset = 0f },
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
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
