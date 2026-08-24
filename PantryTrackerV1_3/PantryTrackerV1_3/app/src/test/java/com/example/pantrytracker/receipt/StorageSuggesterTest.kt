package com.example.pantrytracker.receipt

import com.example.pantrytracker.data.StorageLocation
import org.junit.Assert.assertEquals
import org.junit.Test

class StorageSuggesterTest {
    @Test
    fun suggestsFridgeForDairy() {
        assertEquals(StorageLocation.FRIDGE, StorageSuggester.suggest("Whole milk"))
        assertEquals(StorageLocation.FRIDGE, StorageSuggester.suggest("Joghurt natur"))
    }

    @Test
    fun suggestsCountertopForProduceAndBread() {
        assertEquals(StorageLocation.COUNTERTOP, StorageSuggester.suggest("Bananas"))
        assertEquals(StorageLocation.COUNTERTOP, StorageSuggester.suggest("Baguette"))
    }

    @Test
    fun suggestsPantryForShelfStableFood() {
        assertEquals(StorageLocation.PANTRY, StorageSuggester.suggest("Tomato soup"))
        assertEquals(StorageLocation.PANTRY, StorageSuggester.suggest("Pasta"))
    }

    @Test
    fun usesConfiguredFallbackForUnknownItem() {
        assertEquals(StorageLocation.OTHER, StorageSuggester.suggest("Mystery grocery item", StorageLocation.OTHER))
    }

    @Test
    fun recognizesForeignLanguageFoodWords() {
        assertEquals(StorageLocation.FRIDGE, StorageSuggester.suggest("Volle melk"))
        assertEquals(StorageLocation.COUNTERTOP, StorageSuggester.suggest("Kartoffeln"))
        assertEquals(StorageLocation.PANTRY, StorageSuggester.suggest("Haricots conserve"))
    }
}
