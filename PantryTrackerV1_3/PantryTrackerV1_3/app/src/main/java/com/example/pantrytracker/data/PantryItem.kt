package com.example.pantrytracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pantry_items")
data class PantryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val quantity: Double = 1.0,
    val unit: String = "item",
    val category: String = "",
    val location: String = StorageLocation.PANTRY.name,
    val expirationEpochDay: Long? = null,
    val minimumQuantity: Double = 0.0,
    val onShoppingList: Boolean = false,
    val notes: String = "",
    val barcode: String = "",
    val brand: String = "",
    val packageSize: String = ""
) {
    fun needsRestock(): Boolean = quantity <= minimumQuantity
}

enum class StorageLocation(val label: String) {
    PANTRY("Pantry"),
    FRIDGE("Fridge"),
    FREEZER("Freezer"),
    OTHER("Other")
}
