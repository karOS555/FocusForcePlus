package com.focusforceplus.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

/**
 * Corner radii. Material components pick these up by role: fields and menus use
 * extraSmall, chips small, the FAB large, dialogs and sheets extraLarge. Cards and
 * buttons set theirs explicitly through [FfShapes].
 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

object FfShapes {
    val card = RoundedCornerShape(16.dp)
    val button = RoundedCornerShape(12.dp)
    val field = RoundedCornerShape(12.dp)
    val chip = RoundedCornerShape(8.dp)
}

/** Shared measurements, so every screen lines up on the same edges. */
object FfDimens {
    /** Left and right margin of every screen. */
    val screenPadding = 16.dp
    /** Inner padding of a card and of a list row. */
    val cardPadding = 14.dp
    val rowMinHeight = 62.dp
    val buttonHeight = 48.dp
    val iconCircle = 38.dp
}

/** Entry point for theme values that Material's own theme does not carry. */
object FfTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current
}

@Composable
fun FocusForceTheme(
    colors: AppColors = DefaultAppColors,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(),
            typography = Typography,
            shapes = AppShapes,
            content = content,
        )
    }
}
