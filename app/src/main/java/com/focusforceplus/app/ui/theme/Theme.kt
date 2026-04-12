package com.focusforceplus.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable

private val FocusForceDarkColorScheme = darkColorScheme(
    primary               = Blue700,
    onPrimary             = OnPrimary,
    primaryContainer      = PrimaryContainer,
    onPrimaryContainer    = OnPrimaryContainer,
    secondary             = Blue800,
    onSecondary           = OnPrimary,
    secondaryContainer    = SecondaryContainer,
    onSecondaryContainer  = OnSecondaryContainer,
    tertiary              = Blue850,
    onTertiary            = OnPrimary,
    background            = Background,
    onBackground          = OnBackground,
    surface               = Surface,
    onSurface             = OnSurface,
    surfaceVariant        = Surface2,
    onSurfaceVariant      = OnSurfaceVar,
    error                 = Error,
    onError               = OnError,
    errorContainer        = ErrorContainer,
    onErrorContainer      = OnErrorContainer,
    outline               = Outline,
    outlineVariant        = OutlineVariant,
    scrim                 = Color(0xFF000000),
)

@Composable
fun FocusForceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FocusForceDarkColorScheme,
        typography = Typography,
        content = content
    )
}
