package com.example.pantrytracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PantryDao {
    @Query("SELECT * FROM pantry_items ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<PantryItem>>

    @Query("SELECT * FROM pantry_items WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): PantryItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PantryItem): Long

    @Update
    suspend fun update(item: PantryItem)

    @Delete
    suspend fun delete(item: PantryItem)

    @Query("DELETE FROM pantry_items")
    suspend fun deleteAll()
}
