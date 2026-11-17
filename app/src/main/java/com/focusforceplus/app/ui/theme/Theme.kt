package com.focusforceplus.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

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

/**
 * Wraps every screen. Without [colors] it follows the theme the user picked, and it
 * keeps the status and navigation bar icons readable on light themes too.
 */
@Composable
fun FocusForceTheme(
    colors: AppColors? = null,
    content: @Composable () -> Unit,
) {
    val picked by ThemeStore.colors.collectAsState()
    val colors = colors ?: picked
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !colors.isDark
                isAppearanceLightNavigationBars = !colors.isDark
            }
        }
    }
    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(),
            typography = Typography,
            shapes = AppShapes,
            content = content,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
