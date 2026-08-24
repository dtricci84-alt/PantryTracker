package com.example.pantrytracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pantrytracker.ui.PantryAppWithReceiptImport
import com.example.pantrytracker.ui.PantryViewModel
import com.example.pantrytracker.ui.PantryViewModelFactory
import com.example.pantrytracker.ui.theme.PantryTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val application = application as PantryApplication

        setContent {
            PantryTrackerTheme {
                val pantryViewModel: PantryViewModel = viewModel(
                    factory = PantryViewModelFactory(application.repository, application.productLookupService)
                )
                PantryAppWithReceiptImport(viewModel = pantryViewModel)
            }
        }
    }
}
