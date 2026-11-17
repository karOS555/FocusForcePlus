package com.focusforceplus.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StartLinkTest {

    private fun parse(scheme: String, host: String, vararg params: Pair<String, String>): StartLink? {
        val map = params.toMap()
        return StartLink.parse(scheme, host) { map[it] }
    }

    @Test
    fun quickFocusDefaultsTo25MinutesWithoutDnd() {
        assertEquals(StartLink.QuickFocus(25, false), parse("focusforceplus", "focus"))
    }

    @Test
    fun quickFocusLengthIsClamped() {
        assertEquals(StartLink.QuickFocus(240, true), parse("focusforceplus", "focus", "minutes" to "999", "dnd" to "1"))
        assertEquals(StartLink.QuickFocus(1, false), parse("focusforceplus", "focus", "minutes" to "0"))
    }

    @Test
    fun namedSessionWinsOverMinutes() {
        assertEquals(
            StartLink.FocusSession("Deep work"),
            parse("focusforceplus", "focus", "session" to " Deep work ", "minutes" to "10"),
        )
    }

    @Test
    fun todoPriorityWords() {
        assertEquals(StartLink.AddTodo("Call", 2), parse("focusforceplus", "todo", "title" to "Call", "priority" to "HIGH"))
        assertEquals(StartLink.AddTodo("Call", 1), parse("focusforceplus", "todo", "title" to "Call"))
        assertNull(parse("focusforceplus", "todo", "title" to "  "))
    }

    @Test
    fun listItemsSplitOnCommas() {
        assertEquals(
            StartLink.AddToList("Groceries", listOf("Milk", "Bread")),
            parse("focusforceplus", "list", "name" to "Groceries", "item" to "Milk, Bread,"),
        )
        assertNull(parse("focusforceplus", "list", "name" to "Groceries"))
    }

    @Test
    fun nothingThatStopsThingsIsUnderstood() {
        assertNull(parse("focusforceplus", "stop"))
        assertNull(parse("focusforceplus", "cancel"))
        assertNull(parse("focusforceplus", "routine"))
        assertNull(parse("https", "focus"))
    }
}
