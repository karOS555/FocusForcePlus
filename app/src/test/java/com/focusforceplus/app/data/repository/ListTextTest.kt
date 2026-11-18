package com.focusforceplus.app.data.repository

import org.junit.Assert.assertEquals
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
}
