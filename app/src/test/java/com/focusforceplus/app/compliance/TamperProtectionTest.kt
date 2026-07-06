package com.focusforceplus.app.compliance

import com.focusforceplus.app.data.db.entity.BlockedAppEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TamperProtectionTest {

    private fun config(enabled: Boolean = true, start: Int = 8 * 60, duration: Int = 30) =
        TamperProtectionConfig(enabled, start, duration)

    // ── isInTpWindow ──────────────────────────────────────────────────────────

    @Test
    fun `disabled protection means always changeable`() {
        assertTrue(isInTpWindow(config(enabled = false), 3 * 60))
    }

    @Test
    fun `inside the daily window changes are allowed`() {
        assertTrue(isInTpWindow(config(), 8 * 60))
        assertTrue(isInTpWindow(config(), 8 * 60 + 29))
    }

    @Test
    fun `outside the daily window changes are blocked`() {
        assertFalse(isInTpWindow(config(), 7 * 60 + 59))
        assertFalse(isInTpWindow(config(), 8 * 60 + 30))
        assertFalse(isInTpWindow(config(), 20 * 60))
    }

    @Test
    fun `window wrapping past midnight works`() {
        val c = config(start = 23 * 60 + 45, duration = 30) // 23:45-00:15
        assertTrue(isInTpWindow(c, 23 * 60 + 50))
        assertTrue(isInTpWindow(c, 10))
        assertFalse(isInTpWindow(c, 1 * 60))
    }

    // ── validateTpWindowDuration ──────────────────────────────────────────────

    @Test
    fun `duration bounds follow ComplianceLimits`() {
        assertNotNull(validateTpWindowDuration(9))
        assertNull(validateTpWindowDuration(10))
        assertNull(validateTpWindowDuration(60))
        assertNotNull(validateTpWindowDuration(61))
    }

    // ── isWallClockPlausible ──────────────────────────────────────────────────

    @Test
    fun `honest clock passes`() {
        assertTrue(isWallClockPlausible(1_000_000, 500_000, 1_060_000, 560_000))
    }

    @Test
    fun `forward time jump beyond tolerance fails`() {
        // Wall clock jumped 3 hours while only 1 minute of real time passed.
        assertFalse(
            isWallClockPlausible(1_000_000, 500_000, 1_000_000 + 3 * 3_600_000, 560_000)
        )
    }

    @Test
    fun `backward time jump beyond tolerance fails`() {
        assertFalse(
            isWallClockPlausible(1_000_000, 500_000, 1_000_000 - 3 * 3_600_000, 560_000)
        )
    }

    @Test
    fun `reboot resets validation instead of locking the user out`() {
        // Monotonic clock restarted (now < anchor) — must be accepted.
        assertTrue(isWallClockPlausible(1_000_000, 500_000, 5_000_000, 10_000))
    }

    @Test
    fun `missing anchor is accepted`() {
        assertTrue(isWallClockPlausible(0, 0, 123, 456))
    }

    // ── formatTpWindow ────────────────────────────────────────────────────────

    @Test
    fun `window label formats start and end`() {
        assertEquals("08:00-08:30", formatTpWindow(8 * 60, 30))
        assertEquals("23:45-00:15", formatTpWindow(23 * 60 + 45, 30))
    }

    // ── weakensInvincibleRule ─────────────────────────────────────────────────

    private fun rule(
        invincible: Boolean = true,
        isBlocked: Boolean = true,
        limit: Int? = 30,
        windowStart: Int? = null,
        windowEnd: Int? = null,
    ) = BlockedAppEntity(
        id = 1, packageName = "x", appName = "X",
        dailyLimitMinutes = limit, isBlocked = isBlocked,
        invincibleMode = invincible,
        windowStartMinutes = windowStart, windowEndMinutes = windowEnd,
    )

    @Test
    fun `non-invincible rules are never gated`() {
        assertFalse(weakensInvincibleRule(rule(invincible = false), rule(invincible = false, limit = null)))
    }

    @Test
    fun `turning invincible or the rule off weakens`() {
        assertTrue(weakensInvincibleRule(rule(), rule(invincible = false)))
        assertTrue(weakensInvincibleRule(rule(), rule(isBlocked = false)))
    }

    @Test
    fun `raising or removing the limit weakens, lowering does not`() {
        assertTrue(weakensInvincibleRule(rule(limit = 30), rule(limit = 60)))
        assertTrue(weakensInvincibleRule(rule(limit = 30), rule(limit = null)))
        assertFalse(weakensInvincibleRule(rule(limit = 30), rule(limit = 15)))
    }

    @Test
    fun `removing or shortening the window weakens, extending does not`() {
        val withWindow = rule(limit = null, windowStart = 9 * 60, windowEnd = 17 * 60)
        assertTrue(weakensInvincibleRule(withWindow, rule(limit = null)))
        assertTrue(
            weakensInvincibleRule(withWindow, rule(limit = null, windowStart = 9 * 60, windowEnd = 12 * 60))
        )
        assertFalse(
            weakensInvincibleRule(withWindow, rule(limit = null, windowStart = 9 * 60, windowEnd = 18 * 60))
        )
    }

    // ── weakensInvincibleGroup ────────────────────────────────────────────────

    private fun group(invincible: Boolean = true, limit: Int? = 120) =
        com.focusforceplus.app.data.db.entity.BlockerGroupEntity(
            id = 1, name = "G", sharedDailyLimitMinutes = limit, invincibleMode = invincible,
        )

    @Test
    fun `non-invincible groups are never gated`() {
        assertFalse(weakensInvincibleGroup(group(invincible = false), group(invincible = false, limit = null)))
    }

    @Test
    fun `turning group invincibility off weakens`() {
        assertTrue(weakensInvincibleGroup(group(), group(invincible = false)))
    }

    @Test
    fun `raising or removing the shared limit weakens, lowering does not`() {
        assertTrue(weakensInvincibleGroup(group(limit = 120), group(limit = 180)))
        assertTrue(weakensInvincibleGroup(group(limit = 120), group(limit = null)))
        assertFalse(weakensInvincibleGroup(group(limit = 120), group(limit = 60)))
    }
}
