package com.example.pantrytracker.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

data class ProductLookupData(
    val name: String,
    val brand: String,
    val packageSize: String,
    val category: String,
    val imageUrl: String
)

sealed interface ProductLookupResult {
    data class Found(val product: ProductLookupData) : ProductLookupResult
    data object NotFound : ProductLookupResult
    data class Error(val message: String) : ProductLookupResult
}

class ProductLookupService(
    private val api: OpenFoodFactsApi = createApi()
) {
    suspend fun lookup(barcode: String): ProductLookupResult {
        val normalized = barcode.filter(Char::isDigit)
        if (normalized.isBlank()) {
            return ProductLookupResult.Error("Enter or scan a barcode first.")
        }

        return try {
            val response = api.getProduct(normalized)
            val product = response.product

            if (response.status != 1 || product == null) {
                ProductLookupResult.NotFound
            } else {
                val name = product.productName.orEmpty().trim()
                val brand = product.brands.orEmpty().trim()
                val packageSize = product.quantity.orEmpty().trim()
                val category = product.categories
                    .orEmpty()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .lastOrNull()
                    .orEmpty()
                val imageUrl = product.imageFrontUrl.orEmpty().trim()

                if (name.isBlank() && brand.isBlank() && packageSize.isBlank() && category.isBlank()) {
                    ProductLookupResult.NotFound
                } else {
                    ProductLookupResult.Found(
                        ProductLookupData(
                            name = name,
                            brand = brand,
                            packageSize = packageSize,
                            category = category,
                            imageUrl = imageUrl
                        )
                    )
                }
            }
        } catch (e: Exception) {
            ProductLookupResult.Error(
                e.message?.takeIf { it.isNotBlank() }
                    ?: "Unable to reach the product database."
            )
        }
    }

    companion object {
        private fun createApi(): OpenFoodFactsApi {
            return Retrofit.Builder()
                .baseUrl("https://world.openfoodfacts.org/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(OpenFoodFactsApi::class.java)
        }
    }
}
