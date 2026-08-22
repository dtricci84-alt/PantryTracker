package com.example.pantrytracker.ui

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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pantrytracker.data.PantryItem
import com.example.pantrytracker.data.StorageLocation
import com.example.pantrytracker.network.ProductLookupResult
import java.time.LocalDate
import java.time.format.DateTimeParseException

private enum class AppTab {
    PANTRY, SHOPPING
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantryApp(
    viewModel: PantryViewModel
) {
    val items by viewModel.items.collectAsState()
    val duplicateMatch by viewModel.duplicateMatch.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.PANTRY) }
    var editingItem by remember { mutableStateOf<PantryItem?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    val visibleItems = when (selectedTab) {
        AppTab.PANTRY -> items
        AppTab.SHOPPING -> items.filter { it.onShoppingList || it.needsRestock() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectedTab == AppTab.PANTRY) "Pantry Tracker"
                        else "Shopping List"
                    )
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == AppTab.PANTRY,
                    onClick = { selectedTab = AppTab.PANTRY },
                    icon = { Icon(Icons.Default.Inventory2, contentDescription = null) },
                    label = { Text("Pantry") }
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.SHOPPING,
                    onClick = { selectedTab = AppTab.SHOPPING },
                    icon = { Icon(Icons.Default.ShoppingCart, contentDescription = null) },
                    label = { Text("Shopping") }
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == AppTab.PANTRY) {
                ExtendedFloatingActionButton(
                    onClick = {
                        viewModel.clearDuplicateMatch()
                        editingItem = null
                        showEditor = true
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add item") }
                )
            }
        }
    ) { padding ->
        if (visibleItems.isEmpty()) {
            EmptyState(
                selectedTab = selectedTab,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(
                    items = visibleItems,
                    key = { it.id }
                ) { item ->
                    PantryItemRow(
                        item = item,
                        shoppingMode = selectedTab == AppTab.SHOPPING,
                        onIncrease = { viewModel.changeQuantity(item, 1.0) },
                        onDecrease = { viewModel.changeQuantity(item, -1.0) },
                        onEdit = {
                            editingItem = item
                            showEditor = true
                        },
                        onDelete = { viewModel.delete(item) },
                        onShoppingChanged = {
                            viewModel.setShoppingList(item, it)
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }


    duplicateMatch?.let { existingMatch ->
        AlertDialog(
            onDismissRequest = { viewModel.clearDuplicateMatch() },
            title = { Text("Already in pantry") },
            text = {
                Text(
                    "${existingMatch.name} is already being tracked with quantity " +
                        "${formatQuantity(existingMatch.quantity)} ${existingMatch.unit}. " +
                        "Would you like to add one more?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addOneToExisting(existingMatch)
                        showEditor = false
                    }
                ) {
                    Text("Add +1")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            editingItem = existingMatch
                            viewModel.clearDuplicateMatch()
                            showEditor = true
                        }
                    ) {
                        Text("Edit existing")
                    }
                    TextButton(
                        onClick = { viewModel.clearDuplicateMatch() }
                    ) {
                        Text("Create another")
                    }
                }
            }
        )
    }


    if (showEditor) {
        PantryItemEditorDialog(
            existing = editingItem,
            onDismiss = {
                viewModel.clearDuplicateMatch()
                showEditor = false
            },
            onBarcodeCaptured = { barcode ->
                if (editingItem == null) {
                    viewModel.checkForDuplicateBarcode(barcode)
                }
            },
            onSave = {
                viewModel.save(it)
                showEditor = false
            },
            onLookup = { barcode, onResult ->
                viewModel.lookupProduct(barcode, onResult)
            }
        )
    }
}

@Composable
private fun EmptyState(
    selectedTab: AppTab,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (selectedTab == AppTab.PANTRY) {
                    Icons.Default.Inventory2
                } else {
                    Icons.Default.ShoppingCart
                },
                contentDescription = null
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (selectedTab == AppTab.PANTRY) {
                    "Your pantry is empty."
                } else {
                    "Nothing is on your shopping list."
                },
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (selectedTab == AppTab.PANTRY) {
                    "Tap Add item to start tracking food."
                } else {
                    "Items appear here when flagged or when stock reaches the minimum."
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun PantryItemRow(
    item: PantryItem,
    shoppingMode: Boolean,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShoppingChanged: (Boolean) -> Unit
) {
    Surface {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            ListItem(
                headlineContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.name,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (item.needsRestock()) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "LOW",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                supportingContent = {
                    Column {
                        val locationLabel = runCatching {
                            StorageLocation.valueOf(item.location).label
                        }.getOrDefault(item.location)

                        Text(
                            listOfNotNull(
                                item.category.takeIf { it.isNotBlank() },
                                locationLabel
                            ).joinToString(" • ")
                        )

                        item.expirationEpochDay?.let {
                            Text("Best by ${LocalDate.ofEpochDay(it)}")
                        }

                        if (item.brand.isNotBlank()) {
                            Text(item.brand)
                        }

                        if (item.packageSize.isNotBlank()) {
                            Text("Package: ${item.packageSize}")
                        }

                        if (item.barcode.isNotBlank()) {
                            Text("Barcode: ${item.barcode}")
                        }

                        if (shoppingMode && item.needsRestock()) {
                            Text(
                                "Minimum: ${formatQuantity(item.minimumQuantity)} ${item.unit}",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                trailingContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(onClick = onDecrease) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease quantity")
                    }
                    Text(
                        text = "${formatQuantity(item.quantity)} ${item.unit}",
                        modifier = Modifier.padding(horizontal = 14.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                    FilledTonalIconButton(onClick = onIncrease) {
                        Icon(Icons.Default.Add, contentDescription = "Increase quantity")
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = item.onShoppingList,
                        onCheckedChange = onShoppingChanged
                    )
                    Text("Buy")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PantryItemEditorDialog(
    existing: PantryItem?,
    onDismiss: () -> Unit,
    onBarcodeCaptured: (String) -> Unit,
    onSave: (PantryItem) -> Unit,
    onLookup: (String, (ProductLookupResult) -> Unit) -> Unit
) {
    var name by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.name.orEmpty())
    }
    var quantity by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.quantity?.let(::formatQuantity) ?: "1")
    }
    var unit by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.unit ?: "item")
    }
    var category by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.category.orEmpty())
    }
    var brand by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.brand.orEmpty())
    }
    var packageSize by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.packageSize.orEmpty())
    }
    var location by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.location ?: StorageLocation.PANTRY.name)
    }
    var expiration by rememberSaveable(existing?.id) {
        mutableStateOf(
            existing?.expirationEpochDay?.let {
                LocalDate.ofEpochDay(it).toString()
            }.orEmpty()
        )
    }
    var minimum by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.minimumQuantity?.let(::formatQuantity) ?: "0")
    }
    var shopping by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.onShoppingList ?: false)
    }
    var barcode by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.barcode.orEmpty())
    }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    var lookupMessage by remember { mutableStateOf<String?>(null) }
    var lookupInProgress by remember { mutableStateOf(false) }

    fun lookupBarcode(value: String) {
        val normalized = value.filter(Char::isDigit)
        if (normalized.isBlank()) {
            lookupMessage = "Scan or enter a barcode first."
            return
        }

        lookupInProgress = true
        lookupMessage = "Looking up product…"
        onLookup(normalized) { result ->
            lookupInProgress = false
            when (result) {
                is ProductLookupResult.Found -> {
                    val product = result.product
                    if (product.name.isNotBlank()) name = product.name
                    if (product.brand.isNotBlank()) brand = product.brand
                    if (product.packageSize.isNotBlank()) packageSize = product.packageSize
                    if (product.category.isNotBlank()) category = product.category
                    lookupMessage = "Product found in Open Food Facts."
                }
                ProductLookupResult.NotFound -> {
                    lookupMessage = "Product not found. You can enter the details manually."
                }
                is ProductLookupResult.Error -> {
                    lookupMessage = "Lookup failed: ${result.message}"
                }
            }
        }
    }
    val context = LocalContext.current
    val scannerOptions = remember {
        GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_QR_CODE
            )
            .enableAutoZoom()
            .build()
    }
    val scanner = remember(context) {
        GmsBarcodeScanning.getClient(context, scannerOptions)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (existing == null) "Add pantry item" else "Edit pantry item")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text("Quantity") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { barcode = it.filter(Char::isDigit) },
                        label = { Text("Barcode") },
                        placeholder = { Text("Scan UPC / EAN") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            scanner.startScan()
                                .addOnSuccessListener { result ->
                                    result.rawValue?.let {
                                        barcode = it
                                        onBarcodeCaptured(it)
                                        lookupBarcode(it)
                                    }
                                }
                                .addOnFailureListener {
                                    validationMessage = "Barcode scan failed. Try again."
                                }
                        }
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan barcode")
                        Spacer(Modifier.width(6.dp))
                        Text("Scan")
                    }
                }

                OutlinedButton(
                    onClick = {
                        onBarcodeCaptured(barcode)
                        lookupBarcode(barcode)
                    },
                    enabled = !lookupInProgress && barcode.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (lookupInProgress) "Looking up…" else "Look up barcode")
                }

                lookupMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it.startsWith("Lookup failed")) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Brand") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = packageSize,
                    onValueChange = { packageSize = it },
                    label = { Text("Package size") },
                    placeholder = { Text("500 g, 1 L, 12 oz...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    placeholder = { Text("Canned goods, dairy, pasta...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "Automatic product details are provided by Open Food Facts when available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    "Storage location",
                    style = MaterialTheme.typography.labelLarge
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StorageLocation.entries.forEach { option ->
                        FilterChip(
                            selected = location == option.name,
                            onClick = { location = option.name },
                            label = { Text(option.label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = expiration,
                    onValueChange = { expiration = it },
                    label = { Text("Best-by date") },
                    placeholder = { Text("YYYY-MM-DD") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = minimum,
                    onValueChange = { minimum = it },
                    label = { Text("Minimum stock") },
                    supportingText = {
                        Text("At or below this amount, the item is treated as low stock.")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = shopping,
                        onCheckedChange = { shopping = it }
                    )
                    Text("Add to shopping list")
                }

                validationMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = quantity.toDoubleOrNull()
                    val minQty = minimum.toDoubleOrNull()
                    val expirationEpochDay = try {
                        expiration.trim().takeIf { it.isNotEmpty() }
                            ?.let { LocalDate.parse(it).toEpochDay() }
                    } catch (_: DateTimeParseException) {
                        validationMessage = "Use YYYY-MM-DD for the best-by date."
                        return@Button
                    }

                    when {
                        name.isBlank() -> {
                            validationMessage = "Enter an item name."
                        }
                        qty == null || qty < 0 -> {
                            validationMessage = "Quantity must be 0 or greater."
                        }
                        minQty == null || minQty < 0 -> {
                            validationMessage = "Minimum stock must be 0 or greater."
                        }
                        else -> {
                            onSave(
                                PantryItem(
                                    id = existing?.id ?: 0L,
                                    name = name.trim(),
                                    quantity = qty,
                                    unit = unit.trim().ifBlank { "item" },
                                    category = category.trim(),
                                    location = location,
                                    expirationEpochDay = expirationEpochDay,
                                    minimumQuantity = minQty,
                                    onShoppingList = shopping || qty <= minQty,
                                    notes = existing?.notes.orEmpty(),
                                    barcode = barcode.trim(),
                                    brand = brand.trim(),
                                    packageSize = packageSize.trim()
                                )
                            )
                        }
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatQuantity(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toLong().toString()
    } else {
        value.toString()
    }
}
