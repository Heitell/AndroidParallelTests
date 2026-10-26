package com.example.catalog.ui

/** Метки элементов экрана для тестов. */
object Tags {
    const val SEARCH_FIELD = "search_field"
    const val SEARCH_BUTTON = "search_button"
    const val CLEAR_BUTTON = "clear_button"
    const val LOADING = "loading"
    const val LIST = "item_list"
    const val EMPTY = "empty"
    const val ERROR = "error"
    const val ERROR_MESSAGE = "error_message"
    const val RETRY = "retry"
    const val REFRESH_BOX = "refresh_box"
    const val DETAILS = "details"
    const val DETAILS_TITLE = "details_title"
    const val DETAILS_PRICE = "details_price"
    const val DETAILS_DESCRIPTION = "details_description"
    const val DETAILS_STOCK = "details_stock"
    const val BACK = "back"

    fun item(id: Int) = "item_$id"
    fun itemPrice(id: Int) = "item_price_$id"
}

fun formatPrice(price: Double): String = String.format(java.util.Locale.US, "$%.2f", price)
