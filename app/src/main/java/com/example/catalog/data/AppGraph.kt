package com.example.catalog.data

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Простой контейнер зависимостей. Тесты меняют адрес сервера и таймаут
 * до запуска экрана, поэтому клиент собирается заново при каждом [configure].
 */
object AppGraph {
    private const val DEFAULT_BASE_URL = "https://catalog.example.com/api/"

    @Volatile
    var api: CatalogApi = build(DEFAULT_BASE_URL, timeoutMs = 15_000)
        private set

    /** Хранилище последнего запроса. Подключается при старте экрана. */
    @Volatile
    var lastQuery: LastQuery? = null
        private set

    fun attach(context: android.content.Context) {
        if (lastQuery == null) lastQuery = LastQuery(context.applicationContext)
    }

    fun configure(baseUrl: String, timeoutMs: Long = 15_000) {
        api = build(baseUrl, timeoutMs)
    }

    private fun build(baseUrl: String, timeoutMs: Long): CatalogApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()
        val json = Json { ignoreUnknownKeys = true }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(CatalogApi::class.java)
    }
}
