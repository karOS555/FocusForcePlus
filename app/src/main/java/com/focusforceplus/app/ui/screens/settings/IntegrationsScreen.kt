package com.focusforceplus.app.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.focusforceplus.app.ui.components.FfCard
import com.focusforceplus.app.ui.components.FfPaddedCard
import com.focusforceplus.app.ui.components.FormSection
import com.focusforceplus.app.ui.components.ListRow
import com.focusforceplus.app.ui.components.PageScaffold
import com.focusforceplus.app.ui.components.RowDivider
import com.focusforceplus.app.ui.components.SwitchRow
import com.focusforceplus.app.ui.theme.FfTheme
import com.focusforceplus.app.ui.theme.FfType

private data class LinkExample(val what: String, val link: String)

private val EXAMPLES = listOf(
    LinkExample("Quick focus for 25 minutes", "focusforceplus://focus?minutes=25"),
    LinkExample("Quick focus with Do Not Disturb", "focusforceplus://focus?minutes=50&dnd=1"),
    LinkExample("A saved focus session", "focusforceplus://focus?session=Deep%20work"),
    LinkExample("A routine", "focusforceplus://routine?name=Morning%20routine"),
    LinkExample("A todo", "focusforceplus://todo?title=Call%20the%20dentist&priority=high"),
    LinkExample("Items on a list", "focusforceplus://list?name=Groceries&item=Milk,Bread"),
)

/**
 * How FocusForce+ works with other apps: sharing text into it, and start links for
 * automation apps, home-screen shortcuts and NFC tags.
 */
@Composable
fun IntegrationsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val colors = FfTheme.colors
    val context = LocalContext.current

    PageScaffold(title = "Other apps", onBack = onNavigateBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            FormSection(
                title = "Sharing",
                hint = "Share text from any app and pick FocusForce+: it becomes a todo, or items " +
                    "on one of your lists. Nothing to set up.",
            ) {}

            FormSection(
                title = "Start links",
                hint = "Links can only start and add things. Nothing from outside can end a " +
                    "session, cancel a routine or change a setting. Saved sessions start as they " +
                    "are, invincible ones included.",
            ) {
                FfCard {
                    SwitchRow(
                        title = "Allow start links",
                        subtitle = "For automation apps like Tasker, home-screen shortcuts and NFC tags.",
                        checked = state.externalStartsAllowed,
                        onCheckedChange = viewModel::setExternalStartsAllowed,
                    )
                }
            }

            FormSection(
                title = "Links you can use",
                hint = "Names are matched without caring about upper and lower case. Write a link " +
                    "onto an NFC tag with any tag-writing app to start something with a tap.",
            ) {
                FfCard {
                    EXAMPLES.forEachIndexed { i, example ->
                        if (i > 0) RowDivider()
                        ListRow(
                            title = example.what,
                            subtitle = example.link,
                            subtitleMaxLines = 2,
                            trailing = {
                                IconButton(onClick = { copy(context, example.link) }) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy link", tint = colors.textMuted)
                                }
                            },
                        )
                    }
                }
            }

            FfPaddedCard(spacing = 4.dp) {
                Text("Priorities", style = MaterialTheme.typography.titleSmall, color = colors.text)
                Text(
                    "low, medium or high. Without one, a todo is medium.",
                    style = FfType.rowDetail,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("FocusForce+ link", text))
    Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
}
