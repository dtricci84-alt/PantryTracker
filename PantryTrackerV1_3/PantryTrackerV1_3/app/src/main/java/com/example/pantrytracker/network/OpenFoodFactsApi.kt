package com.example.pantrytracker.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

interface OpenFoodFactsApi {
    @Headers("User-Agent: PantryTracker/1.2 (Android)")
    @GET("api/v2/product/{barcode}.json")
    suspend fun getProduct(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String = "code,product_name,brands,quantity,categories"
    ): OpenFoodFactsResponse
}

data class OpenFoodFactsResponse(
    val status: Int = 0,
    @SerializedName("status_verbose")
    val statusVerbose: String? = null,
    val product: OpenFoodFactsProduct? = null
)

data class OpenFoodFactsProduct(
    val code: String? = null,
    @SerializedName("product_name")
    val productName: String? = null,
    val brands: String? = null,
    val quantity: String? = null,
    val categories: String? = null
)
