package com.example.pantrytracker.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreReceiptParserTest {
    @Test
    fun detectsReweAndDropsPfand() {
        val result = StoreReceiptParser.parse(
            """
            REWE Markt
            2 x Tomatensuppe 3,98
            Milch 1,29
            PFAND 0,25
            SUMME 5,52
            """.trimIndent()
        )

        assertEquals(ReceiptStore.REWE, result.store)
        assertTrue(result.lines.any { it.name.contains("Tomatensuppe") && it.quantity == 2.0 })
        assertTrue(result.lines.any { it.name == "Milch" })
        assertFalse(result.lines.any { it.name.contains("PFAND", ignoreCase = true) })
    }

    @Test
    fun detectsAlbertHeijnAndDropsBonusRows() {
        val result = StoreReceiptParser.parse(
            """
            Albert Heijn
            VOLLE MELK 1,59
            BANANEN 2,49
            BONUS -0,50
            STATIEGELD 0,25
            TOTAAL 3,58
            """.trimIndent()
        )

        assertEquals(ReceiptStore.ALBERT_HEIJN, result.store)
        assertTrue(result.lines.any { it.name == "VOLLE MELK" })
        assertTrue(result.lines.any { it.name == "BANANEN" })
        assertFalse(result.lines.any { "BONUS" in it.name })
        assertFalse(result.lines.any { "STATIEGELD" in it.name })
    }

    @Test
    fun detectsWalmartAndStripsSku() {
        val result = StoreReceiptParser.parse(
            """
            Walmart
            123456789012 TOMATO SOUP 1.48
            987654321098 CEREAL 3.97
            TAX 0.42
            TOTAL 5.87
            """.trimIndent()
        )

        assertEquals(ReceiptStore.WALMART, result.store)
        assertTrue(result.lines.any { it.name == "TOMATO SOUP" })
        assertTrue(result.lines.any { it.name == "CEREAL" })
    }

    @Test
    fun unknownStoreUsesGenericFallback() {
        val result = StoreReceiptParser.parse(
            """
            Local Grocery
            Pasta 1,99
            Eggs 2,49
            Total 4,48
            """.trimIndent()
        )

        assertEquals(ReceiptStore.UNKNOWN, result.store)
        assertTrue(result.lines.any { it.name == "Pasta" })
        assertTrue(result.lines.any { it.name == "Eggs" })
    }
}
