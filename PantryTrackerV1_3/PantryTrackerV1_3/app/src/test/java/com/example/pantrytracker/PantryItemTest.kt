package com.example.pantrytracker

import com.example.pantrytracker.data.PantryItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PantryItemTest {
    @Test
    fun lowStockWhenQuantityEqualsMinimum() {
        val item = PantryItem(
            name = "Black beans",
            quantity = 2.0,
            minimumQuantity = 2.0
        )
        assertTrue(item.needsRestock())
    }

    @Test
    fun notLowStockWhenQuantityAboveMinimum() {
        val item = PantryItem(
            name = "Pasta",
            quantity = 3.0,
            minimumQuantity = 1.0
        )
        assertFalse(item.needsRestock())
    }
    @Test
    fun barcodeCanBeStored() {
        val item = PantryItem(
            name = "Cereal",
            barcode = "012345678905"
        )
        assertTrue(item.barcode == "012345678905")
    }
}
