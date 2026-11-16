package com.focusforceplus.app.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.focusforceplus.app.ui.components.LogoMark
import com.focusforceplus.app.ui.theme.FfTheme

/** Compact height: every dp saved here is content the user actually sees. */
private val BAR_HEIGHT = 52.dp

/**
 * The one header of the main tabs. It carries the screen title itself, so the
 * first row of content starts right under the status bar.
 *
 * [screenTitle] null renders the wordmark, which is used on Home.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusForceTopBar(
    screenTitle: String? = null,
    onSettingsClick: () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = FfTheme.colors
    TopAppBar(
        expandedHeight = BAR_HEIGHT,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colors.background,
            scrolledContainerColor = colors.background,
            actionIconContentColor = colors.textMuted,
        ),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LogoMark(size = 28.dp)
                Spacer(Modifier.width(10.dp))
                if (screenTitle == null) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = colors.text)) { append("FOCUSFORCE") }
                            withStyle(SpanStyle(color = colors.accent)) { append("+") }
                        },
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        letterSpacing = 0.1.em,
                        maxLines = 1,
                    )
                } else {
                    Text(
                        text = screenTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        actions = {
            actions()
            FirstRunHintButton(
                hintKey = "settings_entry",
                bubbleText = "Set up permissions & options here",
                icon = Icons.Filled.Settings,
                contentDescription = "Settings",
                onClick = onSettingsClick,
            )
        },
    )
}
