package com.focusforceplus.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListCatalogTest {

    @Test
    fun commonThingsFindTheirAisle() {
        assertEquals(ListCatalog.DAIRY, ListCatalog.aisleFor("Milk"))
        assertEquals(ListCatalog.PRODUCE, ListCatalog.aisleFor("tomatoes"))
        assertEquals(ListCatalog.BAKERY, ListCatalog.aisleFor("Bread"))
        assertEquals(ListCatalog.HOUSEHOLD, ListCatalog.aisleFor("Toilet paper"))
        assertEquals(ListCatalog.CARE, ListCatalog.aisleFor("toothpaste"))
    }

    @Test
    fun germanNamesWorkToo() {
        assertEquals(ListCatalog.DAIRY, ListCatalog.aisleFor("Milch"))
        assertEquals(ListCatalog.PRODUCE, ListCatalog.aisleFor("Äpfel"))
        assertEquals(ListCatalog.BAKERY, ListCatalog.aisleFor("Brötchen"))
        assertEquals(ListCatalog.HOUSEHOLD, ListCatalog.aisleFor("Spülmittel"))
        assertEquals(ListCatalog.DRINKS, ListCatalog.aisleFor("Apfelschorle"))
    }

    @Test
    fun singularAndPluralBothMatch() {
        assertEquals(ListCatalog.PRODUCE, ListCatalog.aisleFor("tomato"))
        assertEquals(ListCatalog.DAIRY, ListCatalog.aisleFor("egg"))
        assertEquals(ListCatalog.PRODUCE, ListCatalog.aisleFor("Apple"))
        assertEquals(ListCatalog.PRODUCE, ListCatalog.aisleFor("Kartoffel"))
    }

    @Test
    fun theThingItselfIsUsuallyNamedLast() {
        assertEquals(ListCatalog.DAIRY, ListCatalog.aisleFor("whole milk"))
        assertEquals(ListCatalog.PRODUCE, ListCatalog.aisleFor("organic bananas"))
        assertEquals(ListCatalog.DAIRY, ListCatalog.aisleFor("Bio-Eier"))
        // German builds one word of it.
        assertEquals(ListCatalog.DAIRY, ListCatalog.aisleFor("Vollmilch"))
        assertEquals(ListCatalog.BAKERY, ListCatalog.aisleFor("Bauernbrot"))
    }

    @Test
    fun whatIsNotKnownHasNoAisle() {
        assertNull(ListCatalog.aisleFor("Birthday card"))
        assertNull(ListCatalog.aisleFor("Screws 4x40"))
        assertNull(ListCatalog.aisleFor(""))
    }

    @Test
    fun suggestionsStartWithWhatIsTyped() {
        val tom = ListCatalog.suggest("tom")
        assertTrue(tom.toString(), tom.isNotEmpty() && tom.all { it.lowercase().startsWith("tom") })
        assertTrue(tom.toString(), "Tomatoes" in tom || "Tomaten" in tom)
        // Shortest first, written with a capital.
        assertEquals(tom.sortedBy { it.length }, tom)
        assertTrue(ListCatalog.suggest("mi").contains("Milk"))
    }

    @Test
    fun oneLetterOffersNothing() {
        assertEquals(emptyList<String>(), ListCatalog.suggest("t"))
        assertEquals(emptyList<String>(), ListCatalog.suggest(" "))
        assertFalse(ListCatalog.suggest("milk").contains("Milk"))
    }

    @Test
    fun everyAisleOfTheCatalogueIsInTheWalkThroughTheShop() {
        listOf("milk", "apples", "bread", "salmon", "pizza", "rice", "beer", "chips", "batteries", "soap", "diapers")
            .forEach { assertTrue(it, ListCatalog.aisleFor(it) in ListCatalog.AISLES) }
    }
}
