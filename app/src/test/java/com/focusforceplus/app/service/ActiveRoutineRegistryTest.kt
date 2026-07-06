package com.focusforceplus.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveRoutineRegistryTest {

    @Test
    fun `starts idle`() {
        val registry = ActiveRoutineRegistry()
        assertNull(registry.activeRoutineId.value)
        assertFalse(registry.isActive(1L))
    }

    @Test
    fun `markActive flips isActive for that id only`() {
        val registry = ActiveRoutineRegistry()
        registry.markActive(42L)
        assertTrue(registry.isActive(42L))
        assertFalse(registry.isActive(7L))
        assertEquals(42L, registry.activeRoutineId.value)
    }

    @Test
    fun `markIdle clears state when ids match`() {
        val registry = ActiveRoutineRegistry()
        registry.markActive(42L)
        registry.markIdle(42L)
        assertNull(registry.activeRoutineId.value)
        assertFalse(registry.isActive(42L))
    }

    @Test
    fun `markIdle is a no-op when id does not match the active routine`() {
        val registry = ActiveRoutineRegistry()
        registry.markActive(42L)
        registry.markIdle(99L)
        assertEquals(
            "A stop callback for a different routine must not clobber the active session",
            42L,
            registry.activeRoutineId.value,
        )
        assertTrue(registry.isActive(42L))
    }

    @Test
    fun `markActive replaces the previous active id`() {
        val registry = ActiveRoutineRegistry()
        registry.markActive(1L)
        registry.markActive(2L)
        assertEquals(2L, registry.activeRoutineId.value)
        assertTrue(registry.isActive(2L))
        assertFalse(registry.isActive(1L))
    }

    @Test
    fun `late markIdle from a previous routine does not clear a freshly-active one`() {
        // Reproduces the race the matching guard in markIdle exists to prevent:
        // routine A's onDestroy fires after routine B has already been marked active.
        val registry = ActiveRoutineRegistry()
        registry.markActive(1L)
        registry.markActive(2L)
        registry.markIdle(1L)
        assertEquals(2L, registry.activeRoutineId.value)
        assertTrue(registry.isActive(2L))
    }

    @Test
    fun `activeRoutineId flow exposes current value`() {
        val registry = ActiveRoutineRegistry()
        assertNull(registry.activeRoutineId.value)
        registry.markActive(5L)
        assertEquals(5L, registry.activeRoutineId.value)
        registry.markIdle(5L)
        assertNull(registry.activeRoutineId.value)
    }
}
