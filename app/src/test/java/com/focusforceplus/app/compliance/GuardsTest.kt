package com.focusforceplus.app.compliance

import com.focusforceplus.app.data.db.entity.TodoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardsTest {

    private fun todo(priority: Int, guards: String? = null) =
        TodoEntity(id = 1L, title = "Test", priority = priority, createdAt = 0L, guards = guards)

    @Test
    fun `every part survives a round trip through text`() {
        val guard = Guard(confirm = true, waitSeconds = 10, challenge = Challenge.PATH)
        assertEquals("confirm+wait10+path", guard.encode())
        assertEquals(guard, Guard.decode(guard.encode()))
    }

    @Test
    fun `an empty guard is stored as none and read back as none`() {
        assertEquals("none", Guard.NONE.encode())
        assertEquals(Guard.NONE, Guard.decode("none"))
        assertTrue(Guard.decode("none")!!.isNone)
    }

    @Test
    fun `text that was not written by encode is ignored`() {
        assertNull(Guard.decode(null))
        assertNull(Guard.decode(""))
        assertNull(Guard.decode("teleport"))
        assertNull(Guard.decode("waitlong"))
    }

    @Test
    fun `the countdown can never exceed the compliance cap`() {
        val tooLong = Guard.decode("wait600")!!
        assertEquals(ComplianceLimits.MAX_GUARD_WAIT_SECONDS, tooLong.waitSeconds)
        val viaCopy = Guard(waitSeconds = 999).clamped()
        assertEquals(ComplianceLimits.MAX_GUARD_WAIT_SECONDS, viaCopy.waitSeconds)
    }

    @Test
    fun `overrides keep each action separately`() {
        val overrides = GuardOverrides()
            .with(GuardAction.DONE, Guard(confirm = true, challenge = Challenge.HOLD))
            .with(GuardAction.SNOOZE, Guard(waitSeconds = 5, challenge = Challenge.MATH))
        val text = overrides.encode()
        assertEquals("done=confirm+hold;snooze=wait5+math", text)
        val back = GuardOverrides.decode(text)
        assertEquals(overrides, back)
        assertNull(back[GuardAction.RESCHEDULE])
    }

    @Test
    fun `removing the last override leaves nothing to store`() {
        val overrides = GuardOverrides().with(GuardAction.DONE, Guard.NONE).with(GuardAction.DONE, null)
        assertNull(overrides.encode())
    }

    @Test
    fun `a todo without overrides follows the defaults of its priority`() {
        val settings = GuardSettings()
        assertEquals(GuardSlot.TODO_HIGH_DONE.default, settings.forTodo(todo(2), GuardAction.DONE))
        assertEquals(GuardSlot.TODO_MEDIUM_SNOOZE.default, settings.forTodo(todo(1), GuardAction.SNOOZE))
        assertTrue(settings.forTodo(todo(0), GuardAction.DONE).isNone)
    }

    @Test
    fun `high priority asks again and then wants the path by default`() {
        val done = GuardSettings().forTodo(todo(2), GuardAction.DONE)
        assertTrue(done.confirm)
        assertEquals(Challenge.PATH, done.challenge)
    }

    @Test
    fun `a todo's own choice wins over the default`() {
        val own = todo(2, guards = "done=none;reschedule=wait30")
        val settings = GuardSettings()
        assertTrue(settings.forTodo(own, GuardAction.DONE).isNone)
        assertEquals(30, settings.forTodo(own, GuardAction.RESCHEDULE).waitSeconds)
        assertEquals(GuardSlot.TODO_HIGH_SNOOZE.default, settings.forTodo(own, GuardAction.SNOOZE))
    }

    @Test
    fun `the master switch turns off every speed bump, own choices included`() {
        val off = GuardSettings(enabled = false)
        assertTrue(off.forTodo(todo(2), GuardAction.DONE).isNone)
        assertTrue(off.forTodo(todo(2, guards = "done=confirm+path"), GuardAction.DONE).isNone)
        assertTrue(off[GuardSlot.FOCUS_END].isNone)
        // What the user set is kept for when it is switched back on.
        assertEquals(GuardSlot.FOCUS_END.default, off.configured(GuardSlot.FOCUS_END))
    }
}
