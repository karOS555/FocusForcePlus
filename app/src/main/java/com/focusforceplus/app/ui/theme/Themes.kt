package com.focusforceplus.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * A theme as the user sees it: five colours and a name. Everything else the app
 * draws with is worked out from these, so a theme cannot end up with unreadable
 * text in some corner nobody thought to colour.
 */
data class ThemeSpec(
    val name: String,
    val background: Color,
    val surface: Color,
    /** Buttons, switches, the selected item in the bar. */
    val primary: Color,
    /** Links and active icons. */
    val accent: Color,
    val text: Color,
    /** FLAT, or AERO: glass cards, glossy buttons and a drawn sky behind everything. */
    val style: ThemeStyle = ThemeStyle.FLAT,
) {
    val isDark: Boolean get() = background.luminance() < 0.4f

    fun toAppColors(): AppColors {
        if (sameColorsAs(ThemePresets.MIDNIGHT)) return DefaultAppColors
        val dark = isDark
        fun mix(a: Color, b: Color, t: Float) = lerp(a, b, t)
        return AppColors(
            isDark = dark,
            background = background,
            surface = surface,
            surfaceVariant = mix(surface, text, if (dark) 0.035f else 0.04f),
            surfaceHigh = mix(surface, text, if (dark) 0.085f else 0.09f),
            text = text,
            textSecondary = mix(text, background, 0.14f),
            textMuted = mix(text, background, if (dark) 0.40f else 0.36f),
            textFaint = mix(text, background, if (dark) 0.58f else 0.54f),
            outline = mix(surface, text, if (dark) 0.20f else 0.24f),
            divider = mix(surface, text, if (dark) 0.07f else 0.09f),
            primary = primary,
            onPrimary = if (primary.luminance() > 0.45f) Color(0xFF0B0F14) else Color.White,
            container = mix(surface, primary, if (dark) 0.28f else 0.14f),
            onContainer = if (dark) mix(accent, text, 0.35f) else mix(primary, text, 0.25f),
            accent = accent,
            error = if (dark) Color(0xFFF87171) else Color(0xFFDC2626),
            warning = if (dark) Color(0xFFFBBF24) else Color(0xFFB45309),
            success = if (dark) Color(0xFF34D399) else Color(0xFF059669),
            style = style,
        )
    }

    /** Same look: the five colours and the style, whatever the name. */
    fun sameColorsAs(other: ThemeSpec) = background == other.background && surface == other.surface &&
        primary == other.primary && accent == other.accent && text == other.text && style == other.style

    fun toJson(): String = JSONObject().apply {
        put("name", name)
        put("background", hex(background))
        put("surface", hex(surface))
        put("primary", hex(primary))
        put("accent", hex(accent))
        put("text", hex(text))
        put("style", style.name.lowercase())
    }.toString(2)

    companion object {
        /** How many themes of your own the app keeps. */
        const val MAX_SAVED = 24

        fun hex(color: Color): String = "#%06X".format(color.toArgb() and 0xFFFFFF)

        fun parseColor(text: String?): Color? {
            val clean = text?.trim()?.removePrefix("#") ?: return null
            if (clean.length != 6) return null
            return clean.toLongOrNull(16)?.let { Color(0xFF000000 or it) }
        }

        /** Reads a theme someone shared; null when it is not one. */
        fun fromJson(json: String): ThemeSpec? = runCatching {
            val o = JSONObject(json.trim())
            ThemeSpec(
                name = o.optString("name", "Imported").take(40).ifBlank { "Imported" },
                background = parseColor(o.getString("background"))!!,
                surface = parseColor(o.getString("surface"))!!,
                primary = parseColor(o.getString("primary"))!!,
                accent = parseColor(o.getString("accent"))!!,
                text = parseColor(o.getString("text"))!!,
                style = if (o.optString("style") == "aero") ThemeStyle.AERO else ThemeStyle.FLAT,
            )
        }.getOrNull()

        /** A list of saved themes, as kept in the settings and in a backup. */
        fun listFromJson(json: String?): List<ThemeSpec> = runCatching {
            val array = org.json.JSONArray(json ?: return emptyList())
            (0 until array.length()).mapNotNull { fromJson(array.getJSONObject(it).toString()) }
        }.getOrDefault(emptyList())

        fun listToJson(themes: List<ThemeSpec>): String =
            org.json.JSONArray(themes.map { JSONObject(it.toJson()) }).toString()
    }
}

object ThemePresets {
    /** The app's own look. Picking it gives exactly [DefaultAppColors]. */
    val MIDNIGHT = ThemeSpec("Midnight", Background, Surface, Blue700, Color(0xFF60A5FA), OnBackground)
    val GRAPHITE = ThemeSpec("Graphite", Color(0xFF0B0B0C), Color(0xFF161618), Color(0xFF4F5BD5), Color(0xFF9DA6F5), Color(0xFFE7E7EA))
    val FOREST = ThemeSpec("Forest", Color(0xFF060A08), Color(0xFF0F1713), Color(0xFF15803D), Color(0xFF4ADE80), Color(0xFFDCEBE1))
    val EMBER = ThemeSpec("Ember", Color(0xFF0B0706), Color(0xFF1A1210), Color(0xFFC2410C), Color(0xFFFB923C), Color(0xFFF1E4DC))
    val PAPER = ThemeSpec("Paper", Color(0xFFF4F5F7), Color(0xFFFFFFFF), Color(0xFF1D4ED8), Color(0xFF1D4ED8), Color(0xFF111827))
    /** Frutiger Aero: glass, gloss, bubbles and green hills under a bright sky. */
    val AERO = ThemeSpec(
        "Aero", Color(0xFFE6F4FB), Color(0xFFFFFFFF), Color(0xFF0284C7), Color(0xFF0E6E8C), Color(0xFF0C2A3A),
        style = ThemeStyle.AERO,
    )

    val ALL = listOf(MIDNIGHT, GRAPHITE, FOREST, EMBER, PAPER, AERO)
}

/**
 * The theme in use, for every screen and every activity. The app fills it from the
 * stored setting at start and whenever the user changes it.
 */
object ThemeStore {
    private val _spec = MutableStateFlow(ThemePresets.MIDNIGHT)
    val spec: StateFlow<ThemeSpec> = _spec.asStateFlow()

    private val _colors = MutableStateFlow(DefaultAppColors)
    val colors: StateFlow<AppColors> = _colors.asStateFlow()

    fun apply(spec: ThemeSpec) {
        _spec.value = spec
        _colors.value = spec.toAppColors()
    }
}
