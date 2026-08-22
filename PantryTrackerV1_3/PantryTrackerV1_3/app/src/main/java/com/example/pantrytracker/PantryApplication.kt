package com.example.pantrytracker

import android.app.Application
import com.example.pantrytracker.data.PantryDatabase
import com.example.pantrytracker.data.PantryRepository
import com.example.pantrytracker.network.ProductLookupService

class PantryApplication : Application() {
    val database: PantryDatabase by lazy {
        PantryDatabase.getDatabase(this)
    }

    val repository: PantryRepository by lazy {
        PantryRepository(database.pantryDao())
    }

    val productLookupService: ProductLookupService by lazy {
        ProductLookupService()
    }
}
