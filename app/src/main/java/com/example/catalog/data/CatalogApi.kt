package com.example.catalog.data

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable
data class Item(
    val id: Int,
    val title: String,
    val price: Double,
    val category: String,
)

@Serializable
data class ItemDetails(
    val id: Int,
    val title: String,
    val price: Double,
    val category: String,
    val description: String,
    val inStock: Boolean,
)

interface CatalogApi {
    @GET("items")
    suspend fun items(@Query("q") query: String? = null): List<Item>

    @GET("items/{id}")
    suspend fun item(@Path("id") id: Int): ItemDetails
}
