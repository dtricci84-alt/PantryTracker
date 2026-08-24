package com.example.pantrytracker.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.pantrytracker.data.PantryItem
import com.example.pantrytracker.data.StorageLocation
import com.example.pantrytracker.network.ProductLookupResult
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale

private val homeLocations = listOf(
    StorageLocation.PANTRY,
    StorageLocation.FRIDGE,
    StorageLocation.COUNTERTOP,
    StorageLocation.OTHER
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantryApp(viewModel: PantryViewModel) {
    val allItems by viewModel.items.collectAsState()
    val duplicateMatch by viewModel.duplicateMatch.collectAsState()
    val context = LocalContext.current

    var selectedLocation by remember { mutableStateOf<StorageLocation?>(null) }
    var shoppingMode by rememberSaveable { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<PantryItem?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showVoiceLocationPicker by remember { mutableStateOf(false) }
    var voiceLocation by remember { mutableStateOf(StorageLocation.PANTRY) }
    var voiceTranscript by rememberSaveable { mutableStateOf("") }
    var showVoiceReview by remember { mutableStateOf(false) }
    var voiceError by remember { mutableStateOf<String?>(null) }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val heard = matches?.firstOrNull().orEmpty().trim()
            if (heard.isNotBlank()) {
                voiceTranscript = heard
                showVoiceReview = true
            } else {
                voiceError = "I didn't catch any items. Please try again."
            }
        }
    }

    fun launchVoiceEntry(location: StorageLocation) {
        voiceLocation = location
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                "Say your items. Use 'next item' between items, for example: milk, next item eggs, next item bread."
            )
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            voiceError = null
            speechLauncher.launch(intent)
        } else {
            voiceError = "Speech recognition is not available on this device."
        }
    }

    val currentItems = when {
        shoppingMode -> allItems.filter { it.onShoppingList || it.needsRestock() }
        selectedLocation != null -> allItems.filter { itemBelongsToLocation(it, selectedLocation!!) }
        else -> allItems
    }

    val title = when {
        shoppingMode -> "Shopping List"
        selectedLocation != null -> selectedLocation!!.label
        else -> "My Kitchen"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, fontWeight = FontWeight.Bold)
                        if (selectedLocation == null && !shoppingMode) {
                            Text(
                                "Choose a storage area",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (selectedLocation != null || shoppingMode) {
                        IconButton(
                            onClick = {
                                selectedLocation = null
                                shoppingMode = false
                            }
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            selectedLocation = null
                            shoppingMode = true
                        }
                    ) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = "Shopping list")
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedLocation != null && !shoppingMode) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ExtendedFloatingActionButton(
                        onClick = { launchVoiceEntry(selectedLocation!!) },
                        icon = { Icon(Icons.Default.Mic, contentDescription = null) },
                        text = { Text("Speak items") }
                    )
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
        }
    ) { padding ->
        when {
            selectedLocation == null && !shoppingMode -> {
                KitchenHome(
                    items = allItems,
                    modifier = Modifier.padding(padding),
                    onLocationSelected = {
                        selectedLocation = it
                        shoppingMode = false
                    },
                    onVoiceSelected = { showVoiceLocationPicker = true }
                )
            }
            else -> {
                ItemListScreen(
                    items = currentItems,
                    emptyMessage = if (shoppingMode) {
                        "Nothing is on your shopping list."
                    } else {
                        "No items stored in ${selectedLocation?.label ?: "this area"} yet."
                    },
                    modifier = Modifier.padding(padding),
                    onIncrease = { viewModel.changeQuantity(it, 1.0) },
                    onDecrease = { viewModel.changeQuantity(it, -1.0) },
                    onEdit = {
                        editingItem = it
                        showEditor = true
                    },
                    onDelete = viewModel::delete,
                    onShoppingChanged = viewModel::setShoppingList
                )
            }
        }
    }

    voiceError?.let { message ->
        AlertDialog(
            onDismissRequest = { voiceError = null },
            title = { Text("Voice entry") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { voiceError = null }) { Text("OK") }
            }
        )
    }

    if (showVoiceLocationPicker) {
        AlertDialog(
            onDismissRequest = { showVoiceLocationPicker = false },
            title = { Text("Where are these items stored?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    homeLocations.forEach { location ->
                        OutlinedButton(
                            onClick = {
                                showVoiceLocationPicker = false
                                launchVoiceEntry(location)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${locationEmoji(location)}  ${location.label}")
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showVoiceReview) {
        VoiceReviewDialog(
            transcript = voiceTranscript,
            location = voiceLocation,
            onTranscriptChanged = { voiceTranscript = it },
            onDismiss = { showVoiceReview = false },
            onAdd = { names ->
                viewModel.addVoiceItems(names, voiceLocation)
                selectedLocation = voiceLocation
                shoppingMode = false
                showVoiceReview = false
                voiceTranscript = ""
            }
        )
    }

    duplicateMatch?.let { existingMatch ->
        AlertDialog(
            onDismissRequest = { viewModel.clearDuplicateMatch() },
            title = { Text("Already in your kitchen") },
            text = {
                Text(
                    "${existingMatch.name} is already tracked with quantity " +
                        "${formatQuantity(existingMatch.quantity)} ${existingMatch.unit}."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addOneToExisting(existingMatch)
                        showEditor = false
                    }
                ) { Text("Add +1") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            editingItem = existingMatch
                            viewModel.clearDuplicateMatch()
                            showEditor = true
                        }
                    ) { Text("Edit") }
                    TextButton(onClick = { viewModel.clearDuplicateMatch() }) {
                        Text("Create another")
                    }
                }
            }
        )
    }

    if (showEditor) {
        PantryItemEditorDialog(
            existing = editingItem,
            defaultLocation = selectedLocation ?: StorageLocation.PANTRY,
            onDismiss = {
                viewModel.clearDuplicateMatch()
                showEditor = false
            },
            onBarcodeCaptured = { barcode ->
                if (editingItem == null) viewModel.checkForDuplicateBarcode(barcode)
            },
            onSave = {
                viewModel.save(it)
                selectedLocation = displayLocationFor(it)
                shoppingMode = false
                showEditor = false
            },
            onLookup = viewModel::lookupProduct
        )
    }
}

@Composable
private fun KitchenHome(
    items: List<PantryItem>,
    modifier: Modifier = Modifier,
    onLocationSelected: (StorageLocation) -> Unit,
    onVoiceSelected: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Kitchen at a glance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "${items.size} tracked item${if (items.size == 1) "" else "s"} • " +
                        "${items.count { it.needsRestock() }} low stock",
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LocationCard(
                location = StorageLocation.PANTRY,
                count = items.count { itemBelongsToLocation(it, StorageLocation.PANTRY) },
                modifier = Modifier.weight(1f),
                onClick = { onLocationSelected(StorageLocation.PANTRY) }
            )
            LocationCard(
                location = StorageLocation.FRIDGE,
                count = items.count { itemBelongsToLocation(it, StorageLocation.FRIDGE) },
                modifier = Modifier.weight(1f),
                onClick = { onLocationSelected(StorageLocation.FRIDGE) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LocationCard(
                location = StorageLocation.COUNTERTOP,
                count = items.count { itemBelongsToLocation(it, StorageLocation.COUNTERTOP) },
                modifier = Modifier.weight(1f),
                onClick = { onLocationSelected(StorageLocation.COUNTERTOP) }
            )
            LocationCard(
                location = StorageLocation.OTHER,
                count = items.count { itemBelongsToLocation(it, StorageLocation.OTHER) },
                modifier = Modifier.weight(1f),
                onClick = { onLocationSelected(StorageLocation.OTHER) }
            )
        }

        Card(
            onClick = onVoiceSelected,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondary
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Add a list by voice", fontWeight = FontWeight.Bold)
                    Text(
                        "Say several items and review them before they are saved.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationCard(
    location: StorageLocation,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(150.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(locationEmoji(location), fontSize = 42.sp)
            Column {
                Text(location.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("$count item${if (count == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ItemListScreen(
    items: List<PantryItem>,
    emptyMessage: String,
    modifier: Modifier = Modifier,
    onIncrease: (PantryItem) -> Unit,
    onDecrease: (PantryItem) -> Unit,
    onEdit: (PantryItem) -> Unit,
    onDelete: (PantryItem) -> Unit,
    onShoppingChanged: (PantryItem, Boolean) -> Unit
) {
    if (items.isEmpty()) {
        Box(modifier = modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(emptyMessage, style = MaterialTheme.typography.titleMedium)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items, key = { it.id }) { item ->
            PantryItemCard(
                item = item,
                onIncrease = { onIncrease(item) },
                onDecrease = { onDecrease(item) },
                onEdit = { onEdit(item) },
                onDelete = { onDelete(item) },
                onShoppingChanged = { onShoppingChanged(item, it) }
            )
        }
        item { Spacer(Modifier.height(100.dp)) }
    }
}

@Composable
private fun PantryItemCard(
    item: PantryItem,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShoppingChanged: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ItemImage(item)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (item.needsRestock()) {
                        Spacer(Modifier.width(8.dp))
                        Text("LOW", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (item.brand.isNotBlank()) {
                    Text(item.brand, style = MaterialTheme.typography.bodySmall)
                }
                val detail = listOfNotNull(
                    item.category.takeIf { it.isNotBlank() },
                    item.packageSize.takeIf { it.isNotBlank() },
                    displayLocationFor(item).label
                ).joinToString(" • ")
                if (detail.isNotBlank()) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item.expirationEpochDay?.let {
                    Text("Best by ${LocalDate.ofEpochDay(it)}", style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
        }

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = onDecrease) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease")
                }
                Text(
                    "${formatQuantity(item.quantity)} ${item.unit}",
                    modifier = Modifier.padding(horizontal = 12.dp),
                    fontWeight = FontWeight.SemiBold
                )
                FilledTonalIconButton(onClick = onIncrease) {
                    Icon(Icons.Default.Add, contentDescription = "Increase")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = item.onShoppingList, onCheckedChange = onShoppingChanged)
                Text("Buy")
            }
        }
    }
}

@Composable
private fun ItemImage(item: PantryItem) {
    Box(
        modifier = Modifier
            .size(74.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(foodEmoji(item.name, item.category), fontSize = 34.sp)
        if (item.imageUrl.isNotBlank()) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun VoiceReviewDialog(
    transcript: String,
    location: StorageLocation,
    onTranscriptChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onAdd: (List<String>) -> Unit
) {
    val parsed = parseSpokenItems(transcript)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review spoken items") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Saving to ${location.label}. Edit the text if anything was misheard.")
                OutlinedTextField(
                    value = transcript,
                    onValueChange = onTranscriptChanged,
                    label = { Text("Recognized list") },
                    supportingText = { Text("Use commas or the words 'next item' to separate items.") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Items to add", fontWeight = FontWeight.Bold)
                parsed.forEach { name ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text("${foodEmoji(name, "")}  $name", modifier = Modifier.padding(10.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(parsed) }, enabled = parsed.isNotEmpty()) {
                Text("Add ${parsed.size} item${if (parsed.size == 1) "" else "s"}")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PantryItemEditorDialog(
    existing: PantryItem?,
    defaultLocation: StorageLocation,
    onDismiss: () -> Unit,
    onBarcodeCaptured: (String) -> Unit,
    onSave: (PantryItem) -> Unit,
    onLookup: (String, (ProductLookupResult) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var quantity by rememberSaveable(existing?.id) { mutableStateOf(existing?.quantity?.let(::formatQuantity) ?: "1") }
    var unit by rememberSaveable(existing?.id) { mutableStateOf(existing?.unit ?: "item") }
    var category by rememberSaveable(existing?.id) { mutableStateOf(existing?.category.orEmpty()) }
    var brand by rememberSaveable(existing?.id) { mutableStateOf(existing?.brand.orEmpty()) }
    var packageSize by rememberSaveable(existing?.id) { mutableStateOf(existing?.packageSize.orEmpty()) }
    var location by remember(existing?.id) { mutableStateOf(existing?.let(::displayLocationFor) ?: defaultLocation) }
    var expiration by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.expirationEpochDay?.let { LocalDate.ofEpochDay(it).toString() }.orEmpty())
    }
    var minimum by rememberSaveable(existing?.id) { mutableStateOf(existing?.minimumQuantity?.let(::formatQuantity) ?: "0") }
    var shopping by rememberSaveable(existing?.id) { mutableStateOf(existing?.onShoppingList ?: false) }
    var barcode by rememberSaveable(existing?.id) { mutableStateOf(existing?.barcode.orEmpty()) }
    var imageUrl by rememberSaveable(existing?.id) { mutableStateOf(existing?.imageUrl.orEmpty()) }
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
                    if (product.imageUrl.isNotBlank()) imageUrl = product.imageUrl
                    lookupMessage = "Product details and image found."
                }
                ProductLookupResult.NotFound -> lookupMessage = "Product not found. Enter the details manually."
                is ProductLookupResult.Error -> lookupMessage = "Lookup failed: ${result.message}"
            }
        }
    }

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
    val scanner = remember(context) { GmsBarcodeScanning.getClient(context, scannerOptions) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add kitchen item" else "Edit kitchen item") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ItemImage(
                        PantryItem(
                            name = name.ifBlank { "Food" },
                            category = category,
                            imageUrl = imageUrl
                        )
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { barcode = it.filter(Char::isDigit) },
                        label = { Text("Barcode") },
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
                                .addOnFailureListener { validationMessage = "Barcode scan failed. Try again." }
                        }
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan")
                        Spacer(Modifier.width(5.dp))
                        Text("Scan")
                    }
                }
                OutlinedButton(
                    onClick = {
                        onBarcodeCaptured(barcode)
                        lookupBarcode(barcode)
                    },
                    enabled = barcode.isNotBlank() && !lookupInProgress,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (lookupInProgress) "Looking up…" else "Look up barcode")
                }
                lookupMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
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
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Storage location", fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    homeLocations.forEach { option ->
                        FilterChip(
                            selected = location == option,
                            onClick = { location = option },
                            label = { Text("${locationEmoji(option)} ${option.label}") }
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
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = shopping, onCheckedChange = { shopping = it })
                    Text("Add to shopping list")
                }
                validationMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = quantity.toDoubleOrNull()
                    val minQty = minimum.toDoubleOrNull()
                    val expirationEpochDay = try {
                        expiration.trim().takeIf { it.isNotEmpty() }?.let { LocalDate.parse(it).toEpochDay() }
                    } catch (_: DateTimeParseException) {
                        validationMessage = "Use YYYY-MM-DD for the best-by date."
                        return@Button
                    }
                    when {
                        name.isBlank() -> validationMessage = "Enter an item name."
                        qty == null || qty < 0 -> validationMessage = "Quantity must be 0 or greater."
                        minQty == null || minQty < 0 -> validationMessage = "Minimum stock must be 0 or greater."
                        else -> onSave(
                            PantryItem(
                                id = existing?.id ?: 0L,
                                name = name.trim(),
                                quantity = qty,
                                unit = unit.trim().ifBlank { "item" },
                                category = category.trim(),
                                location = location.name,
                                expirationEpochDay = expirationEpochDay,
                                minimumQuantity = minQty,
                                onShoppingList = shopping || qty <= minQty,
                                notes = existing?.notes.orEmpty(),
                                barcode = barcode.trim(),
                                brand = brand.trim(),
                                packageSize = packageSize.trim(),
                                imageUrl = imageUrl.trim()
                            )
                        )
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun itemBelongsToLocation(item: PantryItem, location: StorageLocation): Boolean {
    return when (location) {
        StorageLocation.OTHER -> item.location == StorageLocation.OTHER.name || item.location == StorageLocation.FREEZER.name
        else -> item.location == location.name
    }
}

private fun displayLocationFor(item: PantryItem): StorageLocation {
    return when (item.location) {
        StorageLocation.PANTRY.name -> StorageLocation.PANTRY
        StorageLocation.FRIDGE.name -> StorageLocation.FRIDGE
        StorageLocation.COUNTERTOP.name -> StorageLocation.COUNTERTOP
        else -> StorageLocation.OTHER
    }
}

private fun parseSpokenItems(text: String): List<String> {
    val normalized = text
        .replace(Regex("(?i)\\bnext item\\b"), ",")
        .replace(Regex("(?i)\\band then\\b"), ",")
        .replace(Regex("(?i)\\bthen\\b"), ",")
        .replace(Regex("(?i)\\bcomma\\b"), ",")
        .replace(';', ',')
        .replace('\n', ',')

    return normalized
        .split(',')
        .map { it.trim().trim('.', ' ') }
        .filter { it.isNotBlank() }
        .distinct()
}

private fun locationEmoji(location: StorageLocation): String = when (location) {
    StorageLocation.PANTRY -> "🥫"
    StorageLocation.FRIDGE -> "🧊"
    StorageLocation.COUNTERTOP -> "🍎"
    StorageLocation.OTHER -> "📦"
    StorageLocation.FREEZER -> "❄️"
}

private fun foodEmoji(name: String, category: String): String {
    val value = "$name $category".lowercase(Locale.getDefault())
    return when {
        "soup" in value || "canned" in value || "beans" in value -> "🥫"
        "tomato" in value -> "🍅"
        "milk" in value -> "🥛"
        "egg" in value -> "🥚"
        "bread" in value || "roll" in value -> "🍞"
        "cheese" in value -> "🧀"
        "cereal" in value || "oat" in value -> "🥣"
        "pasta" in value || "spaghetti" in value || "noodle" in value -> "🍝"
        "rice" in value -> "🍚"
        "coffee" in value -> "☕"
        "juice" in value || "drink" in value -> "🧃"
        "apple" in value || "fruit" in value -> "🍎"
        "banana" in value -> "🍌"
        "vegetable" in value || "carrot" in value -> "🥕"
        "meat" in value || "steak" in value -> "🥩"
        "fish" in value -> "🐟"
        else -> "🛒"
    }
}

private fun formatQuantity(value: Double): String {
    return if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
}
