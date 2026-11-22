package com.focusforceplus.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationStyleTest {

    @Test
    fun `phones that draw the launcher icon get the module badge`() {
        listOf("samsung", "Xiaomi", "OPPO", "realme", "OnePlus", "vivo", "HUAWEI", "HONOR").forEach {
            assertTrue(it, drawsLauncherIcon(it))
        }
    }

    @Test
    fun `plain Android keeps the small icon and needs no second copy`() {
        listOf("Google", "motorola", "Sony", "Fairphone", "HMD Global", "Nothing", "").forEach {
            assertFalse(it, drawsLauncherIcon(it))
        }
    }

    @Test
    fun `an unknown maker is taken as plain Android`() {
        assertFalse(drawsLauncherIcon(null))
        assertFalse(drawsLauncherIcon("Unihertz"))
    }
}
