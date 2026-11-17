package com.focusforceplus.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeSpecTest {

    @Test
    fun midnightIsExactlyTheDefaultLook() {
        assertSame(DefaultAppColors, ThemePresets.MIDNIGHT.toAppColors())
    }

    @Test
    fun hexRoundTrip() {
        val c = Color(0xFF1D4ED8)
        assertEquals("#1D4ED8", ThemeSpec.hex(c))
        assertEquals(c, ThemeSpec.parseColor("#1d4ed8"))
        assertEquals(c, ThemeSpec.parseColor(" 1D4ED8 "))
        assertNull(ThemeSpec.parseColor("#12345"))
        assertNull(ThemeSpec.parseColor("not a colour"))
    }

    @Test
    fun lightPresetsAreLightAndDarkOnesDark() {
        assertFalse(ThemePresets.PAPER.toAppColors().isDark)
        assertFalse(ThemePresets.AERO.toAppColors().isDark)
        assertTrue(ThemePresets.FOREST.toAppColors().isDark)
    }

    @Test
    fun buttonTextStaysReadable() {
        val pale = ThemePresets.MIDNIGHT.copy(primary = Color(0xFFFDE68A))
        assertTrue(pale.toAppColors().onPrimary.luminance() < 0.1f)
        val strong = ThemePresets.MIDNIGHT.copy(primary = Color(0xFF7C3AED))
        assertEquals(Color.White, strong.toAppColors().onPrimary)
    }
}
