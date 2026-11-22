package com.focusforceplus.app.service

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ServiceLinkTest {

    @Test
    fun `a service that runs is not reported`() = runBlocking {
        val notRunning = ServiceLink.notRunning(MutableStateFlow(true), MutableStateFlow(true), graceMillis = 60_000)
        assertFalse(withTimeout(5_000) { notRunning.first() })
    }

    @Test
    fun `a service that is switched off is not reported`() = runBlocking {
        val notRunning = ServiceLink.notRunning(MutableStateFlow(false), MutableStateFlow(false), graceMillis = 60_000)
        assertFalse(withTimeout(5_000) { notRunning.first() })
    }

    @Test
    fun `a stalled service says false at once, so nothing waits for it`() = runBlocking {
        // The grace time is far longer than the test waits: the first value must not need it.
        val notRunning = ServiceLink.notRunning(MutableStateFlow(true), MutableStateFlow(false), graceMillis = 60_000)
        assertFalse(withTimeout(5_000) { notRunning.first() })
    }

    @Test
    fun `a stalled service is reported after the grace time`() = runBlocking {
        val notRunning = ServiceLink.notRunning(MutableStateFlow(true), MutableStateFlow(false), graceMillis = 100)
        assertEquals(listOf(false, true), withTimeout(5_000) { notRunning.take(2).toList() })
    }

    @Test
    fun `a service that connects within the grace time is never reported`() = runBlocking {
        val linked = MutableStateFlow(false)
        val seen = mutableListOf<Boolean>()
        // Two seconds of grace against a link after 50 ms: room enough for a slow machine.
        val watching = launch { ServiceLink.notRunning(MutableStateFlow(true), linked, graceMillis = 2_000).collect { seen += it } }
        delay(50)
        linked.value = true
        delay(2_400)
        watching.cancel()
        assertEquals(listOf(false), seen)
    }
}
