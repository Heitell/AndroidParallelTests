package com.example.catalog.data

import android.content.Context
import android.content.SharedPreferences

/** Последний поисковый запрос. Переживает перезапуск приложения. */
class LastQuery(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun get(): String = prefs.getString(KEY, "").orEmpty()

    fun set(query: String) = prefs.edit().putString(KEY, query).commit()

    fun clear() = prefs.edit().clear().commit()

    companion object {
        const val FILE = "catalog"
        private const val KEY = "last_query"
    }
}
