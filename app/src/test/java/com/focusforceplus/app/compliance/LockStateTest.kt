package com.focusforceplus.app.compliance

import com.focusforceplus.app.data.db.entity.BlockedAppEntity
import com.focusforceplus.app.data.db.entity.BlockerGroupEntity
import com.focusforceplus.app.data.repository.SleepSchedule
import com.focusforceplus.app.service.ActiveFocusSession
import com.focusforceplus.app.service.ActiveRoutineSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Golden Rule #11: nothing that Invincible Mode holds can be freed while it is active. */
class LockStateTest {

    private val noon = 12 * 60

    private fun rule(
        pkg: String = "com.example.app",
        group: String? = null,
        blocked: Boolean = true,
        invincible: Boolean = false,
        limit: Int? = null,
        used: Int = 0,
        duringRoutines: Boolean = true,
        duringFocus: Boolean = true,
    ) = BlockedAppEntity(
        packageName = pkg,
        appName = pkg.substringAfterLast('.'),
        groupName = group,
        isBlocked = blocked,
        invincibleMode = invincible,
        dailyLimitMinutes = limit,
        usedTodayMinutes = used,
        blockDuringRoutines = duringRoutines,
        blockDuringFocus = duringFocus,
    )

    private fun social(invincible: Boolean = true, limit: Int? = null, from: Int? = 9 * 60, to: Int? = 17 * 60) =
        BlockerGroupEntity(
            name = "Social",
            invincibleMode = invincible,
            sharedDailyLimitMinutes = limit,
            windowStartMinutes = from,
            windowEndMinutes = to,
        )

    private fun state(
        rules: List<BlockedAppEntity>,
        groups: List<BlockerGroupEntity> = emptyList(),
        minutes: Int = noon,
        routine: ActiveRoutineSession? = null,
        focus: ActiveFocusSession? = null,
        sleep: SleepSchedule = SleepSchedule(),
    ) = LockState(rules, groups, minutes, "MO", routine, focus, sleep)

    private fun focus(invincible: Boolean = true, blocks: Boolean = true, groups: Set<String>? = null) =
        ActiveFocusSession(1, "Deep work", 0L, blocks, invincible, blockedGroups = groups)

    // ── Blocks ────────────────────────────────────────────────────────────────

    @Test
    fun `a member of a locked block is held, although its own rule has no lock`() {
        val member = rule(group = "Social")
        val locked = state(listOf(member), listOf(social()))
        assertTrue(locked.isLocked(social()))
        assertNotNull(locked.holdOn(member))
        assertEquals(1, locked.heldRules)
    }

    @Test
    fun `outside its schedule the block lets go`() {
        val member = rule(group = "Social")
        assertNull(state(listOf(member), listOf(social()), minutes = 20 * 60).holdOn(member))
    }

    @Test
    fun `a block without invincible mode holds nothing`() {
        val member = rule(group = "Social")
        assertNull(state(listOf(member), listOf(social(invincible = false))).holdOn(member))
    }

    @Test
    fun `a shared limit that is used up holds every member`() {
        val a = rule("a.one", group = "Social", used = 40)
        val b = rule("a.two", group = "Social", used = 25)
        val group = social(limit = 60, from = null, to = null)
        val locked = state(listOf(a, b), listOf(group))
        assertEquals(2, locked.heldRules)
        assertEquals(0, state(listOf(a.copy(usedTodayMinutes = 10), b), listOf(group)).heldRules)
    }

    @Test
    fun `a rule that is switched off is not held`() {
        val member = rule(group = "Social", blocked = false)
        assertNull(state(listOf(member), listOf(social())).holdOn(member))
    }

    // ── A rule's own lock ─────────────────────────────────────────────────────

    @Test
    fun `an invincible rule over its limit is held until midnight`() {
        val over = rule(invincible = true, limit = 30, used = 30)
        assertNotNull(state(listOf(over)).holdOn(over))
        val under = over.copy(usedTodayMinutes = 10)
        assertNull(state(listOf(under)).holdOn(under))
    }

    // ── Sessions ──────────────────────────────────────────────────────────────

    @Test
    fun `an invincible focus session holds what it blocks`() {
        val app = rule()
        assertNotNull(state(listOf(app), focus = focus()).holdOn(app))
        assertNull(state(listOf(app), focus = focus(invincible = false)).holdOn(app))
        assertNull(state(listOf(app), focus = focus(blocks = false)).holdOn(app))
    }

    @Test
    fun `a focus session narrowed to blocks holds only those`() {
        val inside = rule("a.in", group = "Social")
        val outside = rule("a.out")
        val running = state(listOf(inside, outside), listOf(social(invincible = false)), focus = focus(groups = setOf("Social")))
        assertNotNull(running.holdOn(inside))
        assertNull(running.holdOn(outside))
    }

    @Test
    fun `a rule that sits out focus sessions is not held by one`() {
        val app = rule(duringFocus = false)
        assertNull(state(listOf(app), focus = focus()).holdOn(app))
    }

    @Test
    fun `an invincible routine holds what it blocks`() {
        val app = rule()
        val routine = ActiveRoutineSession(routineId = 1, blocksApps = true, invincible = true)
        assertNotNull(state(listOf(app), routine = routine).holdOn(app))
        assertNull(state(listOf(app), routine = routine.copy(invincible = false)).holdOn(app))
        assertNull(state(listOf(app.copy(blockDuringRoutines = false)), routine = routine).holdOn(app.copy(blockDuringRoutines = false)))
    }

    @Test
    fun `an invincible night holds its blocks until wake-up`() {
        val member = rule(group = "Social")
        val night = SleepSchedule(
            enabled = true, bedtimeMinutes = 23 * 60, wakeMinutes = 7 * 60,
            blockedGroupsCsv = "Social", invincibleMode = true,
        )
        val groups = listOf(social(invincible = false, from = null, to = null))
        assertNotNull(state(listOf(member), groups, minutes = 2 * 60, sleep = night).holdOn(member))
        assertNull(state(listOf(member), groups, minutes = noon, sleep = night).holdOn(member))
        assertNull(state(listOf(member), groups, minutes = 2 * 60, sleep = night.copy(invincibleMode = false)).holdOn(member))
    }

    // ── Tamper Protection only matters when something is invincible ───────────

    @Test
    fun `invincible blocking exists when a block or a rule has it switched on`() {
        assertFalse(state(listOf(rule())).hasInvincibleBlocking)
        assertTrue(state(listOf(rule(invincible = true, limit = 30))).hasInvincibleBlocking)
        assertTrue(state(listOf(rule(group = "Social")), listOf(social())).hasInvincibleBlocking)
        // A block whose apps are all switched off blocks nothing.
        assertFalse(state(listOf(rule(group = "Social", blocked = false)), listOf(social())).hasInvincibleBlocking)
    }
}
