package com.example.pantrytracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.pantrytracker.data.PantryItem
import com.example.pantrytracker.data.PantryRepository
import com.example.pantrytracker.data.StorageLocation
import com.example.pantrytracker.network.ProductLookupResult
import com.example.pantrytracker.network.ProductLookupService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PantryViewModel(
    private val repository: PantryRepository,
    private val productLookupService: ProductLookupService
) : ViewModel() {
    private val _duplicateMatch = MutableStateFlow<PantryItem?>(null)
    val duplicateMatch: StateFlow<PantryItem?> = _duplicateMatch.asStateFlow()

    val items: StateFlow<List<PantryItem>> = repository.items
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun checkForDuplicateBarcode(barcode: String) {
        if (barcode.isBlank()) {
            _duplicateMatch.value = null
            return
        }
        viewModelScope.launch {
            _duplicateMatch.value = repository.findByBarcode(barcode)
        }
    }

    fun clearDuplicateMatch() {
        _duplicateMatch.value = null
    }

    fun addOneToExisting(item: PantryItem) {
        viewModelScope.launch {
            repository.updateQuantity(item, item.quantity + 1.0)
            _duplicateMatch.value = null
        }
    }

    fun save(item: PantryItem) {
        viewModelScope.launch {
            repository.save(item)
        }
    }

    fun addVoiceItems(names: List<String>, location: StorageLocation) {
        val cleaned = names.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (cleaned.isEmpty()) return
        viewModelScope.launch {
            cleaned.forEach { name ->
                repository.save(
                    PantryItem(
                        name = name,
                        quantity = 1.0,
                        unit = "item",
                        location = location.name
                    )
                )
            }
        }
    }

    fun delete(item: PantryItem) {
        viewModelScope.launch {
            repository.delete(item)
        }
    }

    fun changeQuantity(item: PantryItem, delta: Double) {
        viewModelScope.launch {
            repository.updateQuantity(item, item.quantity + delta)
        }
    }

    fun setShoppingList(item: PantryItem, enabled: Boolean) {
        viewModelScope.launch {
            repository.setShoppingList(item, enabled)
        }
    }

    fun lookupProduct(
        barcode: String,
        onResult: (ProductLookupResult) -> Unit
    ) {
        viewModelScope.launch {
            onResult(productLookupService.lookup(barcode))
        }
    }
}

class PantryViewModelFactory(
    private val repository: PantryRepository,
    private val productLookupService: ProductLookupService
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PantryViewModel::class.java)) {
            return PantryViewModel(repository, productLookupService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
