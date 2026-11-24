package com.focusforceplus.app.ui.components

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import com.focusforceplus.app.ui.theme.FfTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A note that ships inside the app (a licence, a credit), shown word for word with
 * its links working. The app has no internet access itself, so a credit that only
 * lived on a web page could not be read from here.
 */
@Composable
fun AssetNoteDialog(title: String, assetPath: String, onDismiss: () -> Unit) {
    val colors = FfTheme.colors
    val context = LocalContext.current
    val text by produceState("", assetPath) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open(assetPath).bufferedReader().use { it.readText() } }
                .getOrDefault("This note could not be read.")
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceVariant,
        titleContentColor = colors.text,
        textContentColor = colors.textSecondary,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                withLinks(text, colors.accent),
                // On a dialog, not on the picture: no Aero glow behind small print.
                style = MaterialTheme.typography.bodySmall.copy(shadow = null),
                modifier = Modifier.verticalScrollWithHint(),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", style = MaterialTheme.typography.labelLarge, color = colors.accent)
            }
        },
    )
}

private val URL = Regex("https?://[^\\s)]+")

private fun withLinks(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    val style = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
    var at = 0
    for (match in URL.findAll(text)) {
        append(text.substring(at, match.range.first))
        withLink(LinkAnnotation.Url(match.value, style)) { append(match.value) }
        at = match.range.last + 1
    }
    append(text.substring(at))
}
