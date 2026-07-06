package com.focusforceplus.app.compliance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineDurationValidatorTest {

    private val maxMinutes = ComplianceLimits.MAX_ROUTINE_DURATION.inWholeMinutes.toInt()

    @Test
    fun `returns null for an empty routine`() {
        assertNull(validateRoutineDuration(0))
    }

    @Test
    fun `returns null well below the cap`() {
        assertNull(validateRoutineDuration(30))
    }

    @Test
    fun `returns null exactly at the cap`() {
        assertNull(validateRoutineDuration(maxMinutes))
    }

    @Test
    fun `returns error one minute above the cap`() {
        val msg = validateRoutineDuration(maxMinutes + 1)
        assertNotNull(msg)
    }

    @Test
    fun `error message names the actual total and the cap`() {
        val total = maxMinutes + 60
        val msg = validateRoutineDuration(total)!!
        assertTrue(
            "Error message should reference the actual total minutes; was: $msg",
            msg.contains(total.toString()),
        )
        val maxHours = ComplianceLimits.MAX_ROUTINE_DURATION.inWholeHours.toInt()
        assertTrue(
            "Error message should reference the cap in hours; was: $msg",
            msg.contains(maxHours.toString()),
        )
    }

    @Test
    fun `error message explains the compliance reason`() {
        val msg = validateRoutineDuration(maxMinutes + 1)!!
        assertTrue(
            "Error message should mention Google Play to make the cap's rationale clear; was: $msg",
            msg.contains("Google Play"),
        )
    }

    @Test
    fun `cap matches ComplianceLimits constant`() {
        assertEquals(4 * 60, maxMinutes)
    }
}
