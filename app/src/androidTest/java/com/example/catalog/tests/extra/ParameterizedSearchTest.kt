package com.example.catalog.tests.extra

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.catalog.support.BaseTest
import com.example.catalog.support.Fixtures
import com.example.catalog.support.Net
import com.example.catalog.support.Responses
import com.example.catalog.support.waitForTag
import com.example.catalog.ui.Tags
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Параметризация средствами JUnit: один метод, 12 наборов данных, имена вида `search[3]`.
 * [typed] — что ввёл пользователь, [sent] — какой `q` должен уйти на сервер (null — без поиска).
 */
@RunWith(Parameterized::class)
class ParameterizedSearchTest(private val typed: String, private val sent: String?) : BaseTest() {

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun data(): List<Array<Any?>> = listOf(
            arrayOf("phone", "phone"),
            arrayOf("  phone  ", "phone"),
            arrayOf("Phone", "Phone"),
            arrayOf("two words", "two words"),
            arrayOf("кириллица", "кириллица"),
            arrayOf("a", "a"),
            arrayOf("123", "123"),
            arrayOf("c++", "c++"),
            arrayOf("50%", "50%"),
            arrayOf("a&b", "a&b"),
            arrayOf("x/y", "x/y"),
            arrayOf("   ", null),
        )
    }

    @Test
    fun search() {
        routes.on("items") { request, _ ->
            val q = request.url.queryParameter("q")
            Responses.items(if (q == null) Fixtures.items(3) else listOf(Fixtures.item(42, "Found $q")), Net.FAST)
        }
        app.launch()
        compose.waitForTag(Tags.item(1))
        compose.onNodeWithTag(Tags.SEARCH_FIELD).performTextInput(typed)
        compose.onNodeWithTag(Tags.SEARCH_BUTTON).performClick()
        compose.waitForTag(if (sent == null) Tags.item(1) else Tags.item(42))
        compose.onNodeWithTag(Tags.LIST).assertIsDisplayed()
        compose.waitForIdle()
        assertEquals(2, routes.callsTo("items"))
        assertEquals(sent, routes.requests.last().url.queryParameter("q"))
    }
}
