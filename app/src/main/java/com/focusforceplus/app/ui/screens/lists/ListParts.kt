package com.focusforceplus.app.ui.screens.lists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focusforceplus.app.data.db.entity.ListEntity
import com.focusforceplus.app.data.db.entity.ListItemEntity
import com.focusforceplus.app.ui.components.CompletionRing
import com.focusforceplus.app.ui.components.FfPaddedCard
import com.focusforceplus.app.ui.components.TextAction
import com.focusforceplus.app.ui.theme.FfTheme
import com.focusforceplus.app.ui.theme.FfType

/** A list as it hangs off a todo or a routine step: the list and what is still open. */
data class LinkedList(val list: ListEntity, val open: List<ListItemEntity>)

/**
 * The open items of a linked list, to tick off right where you are: on a todo's
 * alarm, or while a routine step runs. Ticking here ticks the item in the list.
 */
@Composable
fun ListTickCard(
    linked: LinkedList,
    onTick: (ListItemEntity) -> Unit,
    modifier: Modifier = Modifier,
    onOpen: (() -> Unit)? = null,
    onStartFresh: (() -> Unit)? = null,
    maxItems: Int = 12,
) {
    val colors = FfTheme.colors
    FfPaddedCard(modifier = modifier, spacing = 4.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(linked.list.name, style = MaterialTheme.typography.titleSmall, color = colors.text)
                Text(
                    when (linked.open.size) {
                        0 -> "All ticked off"
                        1 -> "1 item to go"
                        else -> "${linked.open.size} items to go"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
            onStartFresh?.let { TextAction("Start fresh", onClick = it, color = colors.textSecondary) }
            onOpen?.let { TextAction("Open", onClick = it) }
        }
        val shown = linked.open.take(maxItems)
        if (shown.isNotEmpty()) Spacer(Modifier.padding(top = 2.dp))
        shown.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTick(item) }
                    .padding(vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                CompletionRing(
                    checked = false,
                    onClick = { onTick(item) },
                    color = colors.textFaint,
                    contentDescription = "Tick off ${item.text}",
                )
                Spacer(Modifier.width(4.dp))
                Text(item.text, style = FfType.rowTitle, color = colors.text, modifier = Modifier.weight(1f))
                item.quantity?.let {
                    Text(it, style = MaterialTheme.typography.labelLarge, color = colors.accent)
                }
            }
        }
        if (linked.open.size > shown.size) {
            Text(
                "and ${linked.open.size - shown.size} more",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }
    }
}
