package com.focusforceplus.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusforceplus.app.R
import com.focusforceplus.app.ui.theme.Background
import com.focusforceplus.app.ui.theme.Blue700

/** Compact height — every dp saved here is content the user actually sees. */
private val BAR_HEIGHT = 52.dp

/**
 * The one and only header of the main tabs. It carries the screen title itself
 * (instead of every screen repeating it below the bar), so the first list row
 * starts right under the status bar.
 *
 * [screenTitle] null renders the brand wordmark — used on Home, which is where
 * the app introduces itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusForceTopBar(
    screenTitle: String? = null,
    onSettingsClick: () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        expandedHeight = BAR_HEIGHT,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Background,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (screenTitle == null) 8.dp else 10.dp),
            ) {
                Icon(
                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(if (screenTitle == null) 40.dp else 34.dp),
                    tint = Color.Unspecified,
                )
                if (screenTitle == null) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = Color.White)) { append("FOCUSFORCE") }
                            withStyle(SpanStyle(color = Blue700)) { append("+") }
                        },
                        fontWeight = FontWeight.Black,
                        fontSize = 19.sp,
                        letterSpacing = 1.8.sp,
                        maxLines = 1,
                    )
                } else {
                    Text(
                        text = screenTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
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
                icon = Icons.Outlined.Settings,
                contentDescription = "Settings",
                onClick = onSettingsClick,
            )
        },
    )
}
