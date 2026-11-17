package com.focusforceplus.app.ui.screens.blocker

import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.focusforceplus.app.ui.components.CompletionRing
import com.focusforceplus.app.ui.components.FfCard
import com.focusforceplus.app.ui.components.ListRow
import com.focusforceplus.app.ui.components.RowDivider
import com.focusforceplus.app.ui.components.SearchField
import com.focusforceplus.app.ui.components.TextAction
import com.focusforceplus.app.ui.theme.FfTheme

internal fun formatMinutes(minutesOfDay: Int): String =
    "%02d:%02d".format(minutesOfDay / 60, minutesOfDay % 60)

/** An installed app's own icon; its initials in a circle until it has loaded. */
@Composable
internal fun InstalledAppIcon(
    packageName: String,
    appName: String,
    loadIcon: suspend () -> Drawable?,
    size: Dp = 38.dp,
) {
    val colors = FfTheme.colors
    val drawable by produceState<Drawable?>(initialValue = null, packageName) { value = loadIcon() }
    val bitmap = remember(drawable) { drawable?.toBitmap(96, 96) }
    if (bitmap != null) {
        Icon(
            painter = BitmapPainter(bitmap.asImageBitmap()),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(size),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(colors.surfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                appName.take(2),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Picks apps from everything that is installed, with a search. Chosen apps come
 * first. [onDone] gets the packages and their names.
 */
@Composable
internal fun AppPickerDialog(
    apps: List<Pair<String, String>>,
    initiallySelected: Set<String>,
    loadIcon: suspend (String) -> Drawable?,
    onDone: (Set<String>, Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FfTheme.colors
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(initiallySelected) }
    val shown = remember(apps, query, selected) {
        apps.filter { (pkg, name) ->
            query.isBlank() || name.contains(query, ignoreCase = true) || pkg.contains(query, ignoreCase = true)
        }.sortedWith(compareByDescending<Pair<String, String>> { it.first in selected }.thenBy { it.second.lowercase() })
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .safeDrawingPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = colors.textSecondary)
                }
                Text(
                    if (selected.isEmpty()) "Choose apps" else "${selected.size} chosen",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.text,
                    modifier = Modifier.weight(1f),
                )
                TextAction("Done", onClick = {
                    onDone(selected, apps.filter { it.first in selected }.toMap())
                })
            }
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search apps",
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                item {
                    FfCard {
                        if (shown.isEmpty()) {
                            ListRow(title = "No app called \"$query\"")
                        }
                        shown.forEachIndexed { index, (pkg, name) ->
                            if (index > 0) RowDivider()
                            val on = pkg in selected
                            ListRow(
                                title = name,
                                onClick = { selected = if (on) selected - pkg else selected + pkg },
                                leading = { InstalledAppIcon(pkg, name, loadIcon = { loadIcon(pkg) }) },
                                trailing = {
                                    CompletionRing(
                                        checked = on,
                                        onClick = { selected = if (on) selected - pkg else selected + pkg },
                                        color = colors.accent,
                                        contentDescription = name,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
