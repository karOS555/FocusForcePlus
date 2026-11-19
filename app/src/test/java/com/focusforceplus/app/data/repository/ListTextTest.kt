package com.focusforceplus.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListTextTest {

    private fun check(raw: String, text: String, quantity: String?) =
        assertEquals(ListText.Parsed(text, quantity), ListText.parse(raw))

    @Test
    fun amountsAtTheStart() {
        check("2 Milk", "Milk", "2")
        check("2x Milk", "Milk", "2x")
        check("2 x Milk", "Milk", "2x")
        check("500 g Flour", "Flour", "500 g")
        check("1,5 l Milch", "Milch", "1,5 l")
        check("3 Äpfel", "Äpfel", "3")
    }

    @Test
    fun amountsAtTheEnd() {
        check("Milk x2", "Milk", "2x")
        check("Flour 500 g", "Flour", "500 g")
        check("Eier 10 Stk", "Eier", "10 Stk")
    }

    @Test
    fun plainTextStaysAsItIs() {
        check("Route 66", "Route 66", null)
        check("7up", "7up", null)
        check("  Bread  ", "Bread", null)
        check("Size 42 shoes", "Size 42 shoes", null)
    }

    // ── Several items at once ─────────────────────────────────────────────────

    @Test
    fun commasSeparateItems() {
        assertEquals(listOf("Milk", "eggs", "bread"), ListText.splitItems("Milk, eggs,bread"))
        assertEquals(listOf("Milk", "eggs"), ListText.splitItems("Milk; eggs;"))
    }

    @Test
    fun aCommaInsideANumberIsNotASeparator() {
        assertEquals(listOf("1,5 l Milch"), ListText.splitItems("1,5 l Milch"))
        assertEquals(listOf("Milch 1,5 l", "Eier"), ListText.splitItems("Milch 1,5 l, Eier"))
        // After a word, a number starts the next item.
        assertEquals(listOf("eggs", "2 milk"), ListText.splitItems("eggs,2 milk"))
        check(ListText.splitItems("1,5 l Milch").single(), "Milch", "1,5 l")
    }

    @Test
    fun aPastedListComesInLineByLine() {
        val pasted = "Groceries\n- Milk\n* Eggs\n• Bread\n1. Flour 500 g\n2) Butter\n[ ] Salt\n[x] Sugar\n\n"
        assertEquals(
            listOf("Groceries", "Milk", "Eggs", "Bread", "Flour 500 g", "Butter", "Salt", "Sugar"),
            ListText.splitItems(pasted),
        )
    }

    @Test
    fun nothingGivesNothing() {
        assertEquals(emptyList<String>(), ListText.splitItems("  ,, \n ; "))
    }

    // ── Plus and minus ────────────────────────────────────────────────────────

    @Test
    fun aPlainCountCanBeStepped() {
        assertEquals(1, ListText.countOf(null))
        assertEquals(1, ListText.countOf("  "))
        assertEquals(3, ListText.countOf("3"))
        assertEquals(3, ListText.countOf("3x"))
        assertEquals(3, ListText.countOf("3 x"))
    }

    @Test
    fun anAmountWithAUnitCannot() {
        assertNull(ListText.countOf("500 g"))
        assertNull(ListText.countOf("1,5 l"))
        assertNull(ListText.countOf("a few"))
    }

    @Test
    fun steppingKeepsTheWayItWasWritten() {
        assertEquals("2", ListText.withCount(null, 2))
        assertEquals("4", ListText.withCount("3", 4))
        assertEquals("4x", ListText.withCount("3x", 4))
        // One is not written at all, and it never goes below one.
        assertNull(ListText.withCount("2", 1))
        assertNull(ListText.withCount("2x", 0))
    }

    // ── Tidy writing ──────────────────────────────────────────────────────────

    @Test
    fun aLowerCaseItemGetsACapital() {
        assertEquals("Milk", ListText.tidy("milk"))
        assertEquals("Olive oil", ListText.tidy("olive oil"))
        assertEquals("Äpfel", ListText.tidy("äpfel"))
    }

    @Test
    fun whatHasItsOwnCapitalsIsLeftAlone() {
        assertEquals("iPhone cable", ListText.tidy("iPhone cable"))
        assertEquals("USB stick", ListText.tidy("USB stick"))
        assertEquals("7up", ListText.tidy("7up"))
        assertEquals("", ListText.tidy(""))
    }
}
