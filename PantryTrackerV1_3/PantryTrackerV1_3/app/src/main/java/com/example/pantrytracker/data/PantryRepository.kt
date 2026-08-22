package com.example.pantrytracker.data

import kotlinx.coroutines.flow.Flow

class PantryRepository(
    private val pantryDao: PantryDao
) {
    val items: Flow<List<PantryItem>> = pantryDao.observeAll()

    suspend fun save(item: PantryItem) {
        if (item.id == 0L) {
            pantryDao.insert(item)
        } else {
            pantryDao.update(item)
        }
    }

    suspend fun delete(item: PantryItem) {
        pantryDao.delete(item)
    }

    suspend fun updateQuantity(item: PantryItem, newQuantity: Double) {
        val safeQuantity = newQuantity.coerceAtLeast(0.0)
        pantryDao.update(
            item.copy(
                quantity = safeQuantity,
                onShoppingList = item.onShoppingList || safeQuantity <= item.minimumQuantity
            )
        )
    }

    suspend fun setShoppingList(item: PantryItem, enabled: Boolean) {
        pantryDao.update(item.copy(onShoppingList = enabled))
    }

    suspend fun findByBarcode(barcode: String): PantryItem? {
        if (barcode.isBlank()) return null
        return pantryDao.findByBarcode(barcode.trim())
    }
}
