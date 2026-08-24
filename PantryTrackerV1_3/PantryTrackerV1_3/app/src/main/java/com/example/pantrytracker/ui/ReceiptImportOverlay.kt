package com.example.pantrytracker.ui

import android.app.Activity
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pantrytracker.data.PantryItem
import com.example.pantrytracker.data.StorageLocation
import com.example.pantrytracker.receipt.ReceiptProcessor
import com.example.pantrytracker.receipt.ReceiptScanResult
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

private data class ReceiptEdit(
    val original: String,
    val name: String,
    val quantity: String,
    val selected: Boolean = true
)

@Composable
fun PantryAppWithReceiptImport(viewModel: PantryViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity
    val processor = remember { ReceiptProcessor() }

    var processing by remember { mutableStateOf(false) }
    var receiptError by remember { mutableStateOf<String?>(null) }
    var scanResult by remember { mutableStateOf<ReceiptScanResult?>(null) }
    var edits by remember { mutableStateOf<List<ReceiptEdit>>(emptyList()) }
    var location by remember { mutableStateOf(StorageLocation.PANTRY) }

    val scannerOptions = remember {
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
    }
    val scanner = remember { GmsDocumentScanning.getClient(scannerOptions) }

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        if (activityResult.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val documentResult = GmsDocumentScanningResult.fromActivityResultIntent(activityResult.data)
        val imageUri = documentResult?.pages?.firstOrNull()?.imageUri
        if (imageUri == null) {
            receiptError = "The receipt image could not be read."
            return@rememberLauncherForActivityResult
        }

        processing = true
        receiptError = null
        try {
            val bitmap = context.contentResolver.openInputStream(imageUri)?.use(BitmapFactory::decodeStream)
            if (bitmap == null) {
                processing = false
                receiptError = "The scanned receipt image could not be opened."
            } else {
                processor.process(bitmap) { result ->
                    processing = false
                    result.onSuccess { parsed ->
                        scanResult = parsed
                        edits = parsed.candidates.map {
                            ReceiptEdit(
                                original = it.originalName,
                                name = it.translatedName,
                                quantity = formatReceiptQuantity(it.quantity)
                            )
                        }
                    }.onFailure {
                        receiptError = it.message ?: "Receipt processing failed."
                    }
                }
            }
        } catch (e: Exception) {
            processing = false
            receiptError = e.message ?: "Receipt processing failed."
        }
    }

    fun launchReceiptScanner() {
        if (activity == null) {
            receiptError = "Receipt scanning is not available on this device."
            return
        }
        receiptError = null
        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener {
                receiptError = it.message ?: "The receipt scanner could not be opened."
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PantryApp(viewModel = viewModel)

        ExtendedFloatingActionButton(
            onClick = ::launchReceiptScanner,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 18.dp),
            icon = { androidx.compose.material3.Icon(Icons.Default.ReceiptLong, contentDescription = null) },
            text = { Text(if (processing) "Reading receipt…" else "Scan receipt") }
        )
    }

    receiptError?.let { message ->
        AlertDialog(
            onDismissRequest = { receiptError = null },
            title = { Text("Receipt scan") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { receiptError = null }) { Text("OK") }
            }
        )
    }

    if (processing) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Reading receipt") },
            text = {
                Text("Extracting grocery items and translating them when needed. The first translation may download a language model.")
            },
            confirmButton = {}
        )
    }

    scanResult?.let { result ->
        ReceiptReviewDialog(
            result = result,
            edits = edits,
            location = location,
            onLocationChanged = { location = it },
            onEditChanged = { index, updated ->
                edits = edits.toMutableList().also { it[index] = updated }
            },
            onDismiss = {
                scanResult = null
                edits = emptyList()
            },
            onAdd = {
                edits.filter { it.selected && it.name.isNotBlank() }.forEach { edit ->
                    viewModel.save(
                        PantryItem(
                            name = edit.name.trim(),
                            quantity = edit.quantity.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(1.0) ?: 1.0,
                            unit = "item",
                            location = location.name,
                            notes = if (edit.original.equals(edit.name, ignoreCase = true)) {
                                "Added from receipt"
                            } else {
                                "Receipt: ${edit.original}"
                            }
                        )
                    )
                }
                scanResult = null
                edits = emptyList()
            }
        )
    }
}

@Composable
private fun ReceiptReviewDialog(
    result: ReceiptScanResult,
    edits: List<ReceiptEdit>,
    location: StorageLocation,
    onLocationChanged: (StorageLocation) -> Unit,
    onEditChanged: (Int, ReceiptEdit) -> Unit,
    onDismiss: () -> Unit,
    onAdd: () -> Unit
) {
    val selectedCount = edits.count { it.selected && it.name.isNotBlank() }
    val languageLabel = when (result.sourceLanguage) {
        "de" -> "German → English"
        "nl" -> "Dutch → English"
        "fr" -> "French → English"
        "en" -> "English"
        else -> "Language not confidently detected"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review receipt items") },
        text = {
            Column(
                modifier = Modifier
                    .height(520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(languageLabel, style = MaterialTheme.typography.labelLarge)
                Text(
                    "Receipt text is automatically filtered, but stores format receipts differently. Uncheck totals, discounts, deposits, or anything that is not a grocery item.",
                    style = MaterialTheme.typography.bodySmall
                )

                Text("Store items in", fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        StorageLocation.PANTRY,
                        StorageLocation.FRIDGE,
                        StorageLocation.COUNTERTOP,
                        StorageLocation.OTHER
                    ).forEach { option ->
                        FilterChip(
                            selected = option == location,
                            onClick = { onLocationChanged(option) },
                            label = { Text(option.label) }
                        )
                    }
                }

                edits.forEachIndexed { index, edit ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = edit.selected,
                                onCheckedChange = { checked ->
                                    onEditChanged(index, edit.copy(selected = checked))
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                if (!edit.original.equals(edit.name, ignoreCase = true)) {
                                    Text(
                                        "Receipt: ${edit.original}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedTextField(
                                    value = edit.name,
                                    onValueChange = { value ->
                                        onEditChanged(index, edit.copy(name = value))
                                    },
                                    label = { Text("Item") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                value = edit.quantity,
                                onValueChange = { value ->
                                    onEditChanged(index, edit.copy(quantity = value))
                                },
                                label = { Text("Qty") },
                                singleLine = true,
                                modifier = Modifier.width(82.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onAdd, enabled = selectedCount > 0) {
                Text("Add $selectedCount item${if (selectedCount == 1) "" else "s"}")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun formatReceiptQuantity(value: Double): String {
    return if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
}
