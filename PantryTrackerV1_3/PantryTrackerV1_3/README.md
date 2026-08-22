# Pantry Tracker V1.3

An offline-first Android pantry inventory app with barcode scanning and automatic product lookup.

## Version 1.3 features

- Pantry inventory list
- Add and edit pantry items
- Quantity controls
- Storage locations: Pantry, Fridge, Freezer, Other
- Category and unit fields
- Optional expiration/best-by date
- Minimum stock level
- Automatic low-stock/restock indicator
- Manual shopping-list flag
- Shopping-list tab
- Delete items
- Local Room database
- UPC/EAN/Code 128/QR barcode scanning with Google Code Scanner
- Automatic barcode lookup using Open Food Facts
- Auto-fill product name when available
- Auto-fill brand when available
- Auto-fill product category when available
- Auto-fill package size when available
- Manual "Look up barcode" button for typed barcodes
- Graceful manual-entry fallback for unknown products or network errors
- Duplicate barcode detection against the local pantry database
- One-tap **Add +1** when a scanned product is already in the pantry
- Option to **Edit existing** or **Create another** instead
- Database migration from V1/V1.1 that preserves existing pantry data

## How automatic lookup works

1. Tap **Add item**.
2. Tap **Scan**.
3. Scan the UPC/EAN barcode.
4. Pantry Tracker automatically queries Open Food Facts.
5. If a match is available, the app fills:
   - Product name
   - Brand
   - Package size
   - Category
6. Review or edit the values.
7. Enter how many packages/items you currently have and save.

The pantry quantity remains separate from package size. For example, a 500 g package of pasta can be stored as:

- Quantity: `2`
- Unit: `packages`
- Package size: `500 g`

## Product lookup source

V1.2 uses the Open Food Facts public API:

`https://world.openfoodfacts.org/api/v2/product/{barcode}.json`

Only the fields needed by Pantry Tracker are requested.

Open Food Facts is community-maintained, so some products may be missing or may have incomplete data. The app therefore never requires a successful lookup in order to save an item.

## Toolchain

- Android Gradle Plugin: 9.3.0
- Gradle configuration: 9.5.0
- Kotlin: 2.3.21
- Jetpack Compose BOM: 2026.08.00
- Room: 2.8.4
- Google Code Scanner: 16.1.0
- Retrofit: 3.0.0
- Retrofit Gson converter: 3.0.0
- compileSdk / targetSdk: 37
- minSdk: 26
- JDK: 17+

## Android permissions

V1.2 adds the standard Android `INTERNET` permission for product lookup.

The barcode scanning UI is provided by Google Code Scanner, so the app itself does not request the Android camera permission.

## Database upgrade

V1.2 upgrades the Room database to version 3.

- V1 → V1.1 added `barcode`
- V1.1 → V1.2 adds `brand` and `packageSize`

Both migrations are included, allowing a V1 database to migrate through to V1.2 without intentionally deleting existing pantry records.

## Opening the project

1. Extract this ZIP.
2. Open the `PantryTrackerV1_2` folder in Android Studio.
3. Install Android SDK Platform 37 if prompted.
4. Sync Gradle.
5. Run the `app` configuration on an emulator or Android device running Android 8.0 (API 26) or newer.

As with the earlier starter versions, the binary Gradle wrapper JAR is not bundled. If Android Studio asks, let it repair/generate the wrapper or use a local Gradle 9.5+ installation.

## Suggested V1.4 improvements

- Product images
- Expiration notifications
- Better date picker
- Category filters and search
- Grocery-list check-off workflow
- Import/export backup
- Household synchronization
- Configurable quantity increment for duplicate scans
