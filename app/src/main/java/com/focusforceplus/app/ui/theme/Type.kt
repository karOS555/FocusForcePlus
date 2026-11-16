package com.focusforceplus.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.focusforceplus.app.R

/**
 * Inter, bundled as one variable font file (SIL Open Font License, see
 * assets/licenses/inter-ofl.txt). Every weight below is an instance of the same
 * file, so the app looks the same on every phone instead of following whatever
 * font the manufacturer ships.
 */
@OptIn(ExperimentalTextApi::class)
private fun inter(weight: FontWeight, opticalSize: Float) = Font(
    resId = R.font.inter,
    weight = weight,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
        FontVariation.Setting("opsz", opticalSize),
    ),
)

private val weights = listOf(
    FontWeight.ExtraLight,
    FontWeight.Light,
    FontWeight.Normal,
    FontWeight.Medium,
    FontWeight.SemiBold,
    FontWeight.Bold,
    FontWeight.ExtraBold,
)

/** Text cut: sturdier shapes for sizes people read at. */
val Inter = FontFamily(weights.map { inter(it, 14f) })

/** Display cut: tighter, finer shapes for headings and big numerals. */
val InterDisplay = FontFamily(weights.map { inter(it, 32f) })

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = InterDisplay, fontWeight = FontWeight.ExtraLight,
        fontSize = 76.sp, lineHeight = 78.sp, letterSpacing = (-0.05).em,
        fontFeatureSettings = "tnum",
    ),
    displayMedium = TextStyle(
        fontFamily = InterDisplay, fontWeight = FontWeight.Light,
        fontSize = 52.sp, lineHeight = 56.sp, letterSpacing = (-0.04).em,
        fontFeatureSettings = "tnum",
    ),
    displaySmall = TextStyle(
        fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.025).em,
    ),
    headlineLarge = TextStyle(
        fontFamily = InterDisplay, fontWeight = FontWeight.Bold,
        fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.025).em,
    ),
    headlineMedium = TextStyle(
        fontFamily = InterDisplay, fontWeight = FontWeight.Bold,
        fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.025).em,
    ),
    headlineSmall = TextStyle(
        fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.02).em,
    ),
    titleLarge = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.015).em,
    ),
    titleMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = (-0.01).em,
    ),
    titleSmall = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.005).em,
    ),
    bodyLarge = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 14.5.sp, lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 12.5.sp, lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 14.sp,
    ),
)

/** Text styles the Material scale has no slot for. */
object FfType {
    /** Title of a list row. */
    val rowTitle = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.005).em,
    )

    /** The line under a row title. */
    val rowDetail = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 18.sp,
    )

    /** Big stat numbers and countdowns: fixed-width digits so they do not jitter. */
    val number = TextStyle(
        fontFamily = InterDisplay, fontWeight = FontWeight.Bold,
        fontSize = 21.sp, lineHeight = 26.sp, letterSpacing = (-0.02).em,
        fontFeatureSettings = "tnum",
    )
}
