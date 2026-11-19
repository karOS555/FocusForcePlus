package com.focusforceplus.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * Every colour the app draws with, as one flat set of roles.
 *
 * Screens read these through [FfTheme.colors] instead of naming a concrete colour,
 * which is what lets a user-made theme restyle the whole app. The roles are
 * deliberately few: one fill colour for actions, one tonal pair for quieter
 * surfaces, and [accent] for anything blue that is *text or an icon*. Keeping fill
 * and accent apart matters on a dark background, where a blue strong enough to
 * carry white button text is too dark to read as a link.
 */
/** How surfaces are drawn: flat colour, or Frutiger Aero glass and gloss. */
enum class ThemeStyle { FLAT, AERO }

@Immutable
data class AppColors(
    val isDark: Boolean,
    /** Screen background. */
    val background: Color,
    /** Cards and bars. */
    val surface: Color,
    /** Fields and anything resting on a card. */
    val surfaceVariant: Color,
    /** Tracks, pressed states, the highest tonal step. */
    val surfaceHigh: Color,
    val text: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textFaint: Color,
    val outline: Color,
    val divider: Color,
    /** Filled buttons, switches, the FAB. Always carries [onPrimary]. */
    val primary: Color,
    val onPrimary: Color,
    /** Tonal buttons, icon circles, the selected pill in the bar. */
    val container: Color,
    val onContainer: Color,
    /** Links, selected labels, active icons: blue that has to be readable. */
    val accent: Color,
    val error: Color,
    val warning: Color,
    val success: Color,
    /** FLAT, or AERO: glass cards, glossy buttons and a photo behind everything (see ffBackdrop). */
    val style: ThemeStyle = ThemeStyle.FLAT,
) {
    val isAero: Boolean get() = style == ThemeStyle.AERO

    /** What a screen fills itself with: see-through in Aero, so the photo shows. */
    val screen: Color get() = if (isAero) Color.Transparent else background

    /** A bar over scrolling content (a scrolled top bar, a sticky footer). */
    val bar: Color get() = if (isAero) surface.copy(alpha = 0.72f) else background

    /** The bottom bar of the app. */
    val dock: Color get() = if (isAero) surface.copy(alpha = 0.72f) else surface

    /** The logo's blue: between the fill and the accent, so it works on any surface. */
    val brand: Color get() = lerp(primary, accent, 0.45f)

    /** Colour of a priority marker: 0 low, 1 medium, 2 high. */
    fun priority(priority: Int): Color = when (priority) {
        2 -> error
        1 -> warning
        else -> textFaint
    }

    fun toColorScheme(): ColorScheme {
        val errorContainer = error.copy(alpha = if (isDark) 0.22f else 0.16f).compositeOver(surface)
        val base = if (isDark) darkColorScheme() else lightColorScheme()
        return base.copy(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = container,
            onPrimaryContainer = onContainer,
            secondary = accent,
            onSecondary = onPrimary,
            secondaryContainer = container,
            onSecondaryContainer = onContainer,
            tertiary = accent,
            onTertiary = onPrimary,
            tertiaryContainer = container,
            onTertiaryContainer = onContainer,
            background = background,
            onBackground = text,
            surface = surface,
            onSurface = textSecondary,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = textMuted,
            surfaceTint = Color.Transparent,
            inverseSurface = text,
            inverseOnSurface = background,
            inversePrimary = primary,
            error = error,
            onError = if (error.luminance() > 0.5f) Color(0xFF3B0A0A) else Color.White,
            errorContainer = errorContainer,
            onErrorContainer = if (isDark) Color(0xFFFECACA) else Color(0xFF7F1D1D),
            outline = outline,
            outlineVariant = divider,
            scrim = Color.Black,
            surfaceBright = surfaceHigh,
            surfaceDim = background,
            surfaceContainerLowest = background,
            surfaceContainerLow = surface,
            surfaceContainer = surface,
            surfaceContainerHigh = surfaceVariant,
            surfaceContainerHighest = surfaceHigh,
        )
    }
}

/** The app's own look: near-black, slate cards, one blue. */
val DefaultAppColors = AppColors(
    isDark = true,
    background = Background,
    surface = Surface,
    surfaceVariant = Surface2,
    surfaceHigh = Surface3,
    text = OnBackground,
    textSecondary = OnSurface,
    textMuted = OnSurfaceVar,
    textFaint = Color(0xFF64748B),
    outline = Outline,
    divider = OutlineVariant,
    primary = Blue700,
    onPrimary = OnPrimary,
    container = SecondaryContainer,
    onContainer = OnSecondaryContainer,
    accent = Color(0xFF60A5FA),
    error = Error,
    warning = Warning,
    success = Color(0xFF34D399),
)

val LocalAppColors = staticCompositionLocalOf { DefaultAppColors }
