package com.example.catalog.tests.extra

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.catalog.data.ItemDetails
import com.example.catalog.support.BaseTest
import com.example.catalog.support.Fixtures
import com.example.catalog.support.Net
import com.example.catalog.support.Responses
import com.example.catalog.support.waitForTag
import com.example.catalog.ui.Tags
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.Test
import org.junit.runner.RunWith

/** Параметризация через TestParameterInjector: параметры в аргументах метода, имена вида `listPrice[CHEAP]`. */
@RunWith(TestParameterInjector::class)
class InjectedParamsTest : BaseTest() {

    enum class PriceCase(val price: Double, val text: String) {
        ZERO(0.0, "$0.00"),
        CHEAP(0.5, "$0.50"),
        ROUND(10.0, "$10.00"),
        CENTS(19.99, "$19.99"),
        ROUNDS_UP(2.005, "$2.01"),
        THOUSANDS(1999.0, "$1999.00"),
    }

    @Test
    fun listPrice(@TestParameter case: PriceCase) {
        routes.on("items") { _, _ -> Responses.items(listOf(Fixtures.item(1, price = case.price)), Net.FAST) }
        app.launch()
        compose.waitForTag(Tags.item(1))
        // Цена — часть склеенной строки списка, ищем её в несклеенном дереве.
        compose.onNodeWithTag(Tags.itemPrice(1), useUnmergedTree = true).assertTextEquals(case.text)
    }

    @Test
    fun stockLabel(@TestParameter inStock: Boolean) {
        routes.on("items") { _, _ -> Responses.items(Fixtures.items(1), Net.FAST) }
        routes.on("items/1") { _, _ -> Responses.details(Fixtures.details(1, inStock = inStock), Net.FAST) }
        app.launch()
        compose.waitForTag(Tags.item(1))
        compose.onNodeWithTag(Tags.item(1)).performClick()
        compose.waitForTag(Tags.DETAILS_STOCK)
        compose.onNodeWithTag(Tags.DETAILS_STOCK).assertTextEquals(if (inStock) "In stock" else "Out of stock")
    }
}
