package com.focusforceplus.app.compliance

import com.focusforceplus.app.data.db.entity.BlockedAppEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockDecisionTest {

    private fun app(
        packageName: String = "com.example.app",
        isBlocked: Boolean = true,
        dailyLimitMinutes: Int? = null,
        usedTodayMinutes: Int = 0,
        windowStartMinutes: Int? = null,
        windowEndMinutes: Int? = null,
        invincibleMode: Boolean = false,
        blockDuringRoutines: Boolean = true,
        blockDuringFocus: Boolean = true,
        exceptionUntilMillis: Long = 0,
        exceptionsUsedToday: Int = 0,
    ) = BlockedAppEntity(
        id = 1L,
        packageName = packageName,
        appName = "Example",
        dailyLimitMinutes = dailyLimitMinutes,
        usedTodayMinutes = usedTodayMinutes,
        isBlocked = isBlocked,
        blockDuringRoutines = blockDuringRoutines,
        blockDuringFocus = blockDuringFocus,
        invincibleMode = invincibleMode,
        windowStartMinutes = windowStartMinutes,
        windowEndMinutes = windowEndMinutes,
        exceptionUntilMillis = exceptionUntilMillis,
        exceptionsUsedToday = exceptionsUsedToday,
    )

    private val now = 1_000_000L

    // ── evaluateBlock ─────────────────────────────────────────────────────────

    @Test
    fun `manual rule blocks whenever enabled`() {
        assertEquals(BlockReason.MANUAL, evaluateBlock(app(), now, 600, false, false))
    }

    @Test
    fun `disabled rule never blocks`() {
        assertNull(evaluateBlock(app(isBlocked = false), now, 600, false, false))
    }

    @Test
    fun `limited app is allowed below the limit and blocked at the limit`() {
        val below = app(dailyLimitMinutes = 30, usedTodayMinutes = 29)
        val at = app(dailyLimitMinutes = 30, usedTodayMinutes = 30)
        assertNull(evaluateBlock(below, now, 600, false, false))
        assertEquals(BlockReason.DAILY_LIMIT, evaluateBlock(at, now, 600, false, false))
    }

    @Test
    fun `window rule blocks only inside the window`() {
        val a = app(windowStartMinutes = 9 * 60, windowEndMinutes = 17 * 60)
        assertNull(evaluateBlock(a, now, 8 * 60, false, false))
        assertEquals(BlockReason.TIME_WINDOW, evaluateBlock(a, now, 12 * 60, false, false))
        assertNull(evaluateBlock(a, now, 17 * 60, false, false))
    }

    @Test
    fun `overnight window blocks across midnight and releases in the morning`() {
        val a = app(windowStartMinutes = 22 * 60, windowEndMinutes = 6 * 60)
        assertEquals(BlockReason.TIME_WINDOW, evaluateBlock(a, now, 23 * 60, false, false))
        assertEquals(BlockReason.TIME_WINDOW, evaluateBlock(a, now, 2 * 60, false, false))
        assertNull(evaluateBlock(a, now, 6 * 60, false, false))
        assertNull(evaluateBlock(a, now, 12 * 60, false, false))
    }

    @Test
    fun `active exception suspends every block`() {
        val a = app(exceptionUntilMillis = now + 1)
        assertNull(evaluateBlock(a, now, 600, true, true))
    }

    @Test
    fun `routine blocking wins over limit state`() {
        val a = app(dailyLimitMinutes = 30, usedTodayMinutes = 0)
        assertEquals(BlockReason.ROUTINE_ACTIVE, evaluateBlock(a, now, 600, true, false))
    }

    @Test
    fun `session blocking respects the per-app opt-out`() {
        val a = app(dailyLimitMinutes = 30, blockDuringRoutines = false, blockDuringFocus = false)
        assertNull(evaluateBlock(a, now, 600, true, true))
    }

    @Test
    fun `protected system packages are never blocked`() {
        val settings = app(packageName = "com.android.settings")
        assertNull(evaluateBlock(settings, now, 600, true, true))
    }

    // ── isRuleLocked (invincible state machine) ───────────────────────────────

    @Test
    fun `manual invincible rule never locks — no natural end exists`() {
        assertFalse(isRuleLocked(app(invincibleMode = true), 600))
    }

    @Test
    fun `invincible rule locks when the daily limit is reached`() {
        val a = app(invincibleMode = true, dailyLimitMinutes = 30, usedTodayMinutes = 30)
        assertTrue(isRuleLocked(a, 600))
    }

    @Test
    fun `invincible rule is togglable while below the limit (IDLE state)`() {
        val a = app(invincibleMode = true, dailyLimitMinutes = 30, usedTodayMinutes = 10)
        assertFalse(isRuleLocked(a, 600))
    }

    @Test
    fun `invincible rule locks inside its window and releases after`() {
        val a = app(invincibleMode = true, windowStartMinutes = 9 * 60, windowEndMinutes = 17 * 60)
        assertTrue(isRuleLocked(a, 12 * 60))
        assertFalse(isRuleLocked(a, 18 * 60))
    }

    @Test
    fun `invincible overnight window locks across midnight with a natural end`() {
        val a = app(invincibleMode = true, windowStartMinutes = 22 * 60, windowEndMinutes = 6 * 60)
        assertTrue(isRuleLocked(a, 23 * 60))
        assertTrue(isRuleLocked(a, 3 * 60))
        assertFalse(isRuleLocked(a, 8 * 60)) // released after window end
    }

    @Test
    fun `non-invincible rule never locks`() {
        val a = app(dailyLimitMinutes = 30, usedTodayMinutes = 30)
        assertFalse(isRuleLocked(a, 600))
    }

    // ── canGrantException ─────────────────────────────────────────────────────

    @Test
    fun `exception available for plain limit block`() {
        val a = app(dailyLimitMinutes = 30, usedTodayMinutes = 30)
        assertTrue(canGrantException(a, BlockReason.DAILY_LIMIT, 600, sessionIsInvincible = false))
    }

    @Test
    fun `exception denied once the daily budget is used`() {
        val a = app(dailyLimitMinutes = 30, usedTodayMinutes = 30, exceptionsUsedToday = MAX_BLOCK_EXCEPTIONS_PER_DAY)
        assertFalse(canGrantException(a, BlockReason.DAILY_LIMIT, 600, sessionIsInvincible = false))
    }

    @Test
    fun `exception denied while the rule is invincible-locked`() {
        val a = app(invincibleMode = true, dailyLimitMinutes = 30, usedTodayMinutes = 30)
        assertFalse(canGrantException(a, BlockReason.DAILY_LIMIT, 600, sessionIsInvincible = false))
    }

    @Test
    fun `exception denied when an invincible session drives the block`() {
        val a = app()
        assertFalse(canGrantException(a, BlockReason.ROUTINE_ACTIVE, 600, sessionIsInvincible = true))
        assertTrue(canGrantException(a, BlockReason.ROUTINE_ACTIVE, 600, sessionIsInvincible = false))
    }

    // ── validateBlockWindow ───────────────────────────────────────────────────

    @Test
    fun `twelve hour window is accepted`() {
        assertNull(validateBlockWindow(9 * 60, 21 * 60))
    }

    @Test
    fun `window longer than the cap is rejected with a Google Play rationale`() {
        val msg = validateBlockWindow(8 * 60, 21 * 60)
        assertNotNull(msg)
        assertTrue(msg!!.contains("Google Play"))
    }

    @Test
    fun `overnight window within the cap is accepted`() {
        assertNull(validateBlockWindow(22 * 60, 6 * 60)) // 8h across midnight
    }

    @Test
    fun `overnight window longer than the cap is rejected`() {
        assertNotNull(validateBlockWindow(18 * 60, 9 * 60)) // 15h across midnight
    }

    @Test
    fun `zero-length window is rejected`() {
        assertNotNull(validateBlockWindow(9 * 60, 9 * 60))
    }

    // ── Groups ────────────────────────────────────────────────────────────────

    private fun group(invincible: Boolean = false, limit: Int? = 120) =
        com.focusforceplus.app.data.db.entity.BlockerGroupEntity(
            id = 1, name = "Social Media",
            sharedDailyLimitMinutes = limit, invincibleMode = invincible,
        )

    @Test
    fun `grouped app without own rules is allowed until the shared limit hits`() {
        val member = app() // no own limit/window
        assertNull(
            evaluateBlock(member, now, 600, false, false, inGroup = true, groupLimitReached = false)
        )
        assertEquals(
            BlockReason.GROUP_LIMIT,
            evaluateBlock(member, now, 600, false, false, inGroup = true, groupLimitReached = true),
        )
    }

    @Test
    fun `grouping an app removes the plain always-block, own limit still applies`() {
        val member = app(dailyLimitMinutes = 30, usedTodayMinutes = 30)
        assertEquals(
            BlockReason.DAILY_LIMIT,
            evaluateBlock(member, now, 600, false, false, inGroup = true, groupLimitReached = false),
        )
    }

    @Test
    fun `exception also suspends a group-limit block`() {
        val member = app(exceptionUntilMillis = now + 1)
        assertNull(
            evaluateBlock(member, now, 600, false, false, inGroup = true, groupLimitReached = true)
        )
    }

    @Test
    fun `group locks only when invincible with the shared limit reached`() {
        assertFalse(isGroupLocked(group(invincible = false), 500))
        assertFalse(isGroupLocked(group(invincible = true, limit = null), 500))
        assertFalse(isGroupLocked(group(invincible = true, limit = 120), 119))
        assertTrue(isGroupLocked(group(invincible = true, limit = 120), 120))
    }

    @Test
    fun `locked group denies the exception`() {
        val member = app()
        assertFalse(
            canGrantException(member, BlockReason.GROUP_LIMIT, 600, sessionIsInvincible = false, groupIsLocked = true)
        )
        assertTrue(
            canGrantException(member, BlockReason.GROUP_LIMIT, 600, sessionIsInvincible = false, groupIsLocked = false)
        )
    }
}
