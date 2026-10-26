package com.example.catalog.tests.extra

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.catalog.support.BaseTest
import com.example.catalog.support.Fixtures
import com.example.catalog.support.Net
import com.example.catalog.support.Responses
import com.example.catalog.support.waitForTag
import com.example.catalog.ui.Tags
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Один длинный сценарий на медленной сети, около 40 с: список, карточка, назад, поиск.
 * Такой тест нельзя разрезать, он задаёт нижнюю границу времени у любого способа.
 */
@RunWith(AndroidJUnit4::class)
class LongFlowTest : BaseTest() {

    @Test
    fun fullJourneyOnSlowNetwork() {
        routes.on("items") { request, _ ->
            val q = request.url.queryParameter("q")
            Responses.items(if (q == null) Fixtures.items(5) else listOf(Fixtures.item(5, "Found $q")), 9_000)
        }
        routes.on("items/5") { _, _ -> Responses.details(Fixtures.details(5), 9_000) }

        app.launch()
        compose.waitForTag(Tags.item(5))
        compose.onNodeWithTag(Tags.item(5)).performClick()
        compose.waitForTag(Tags.DETAILS_TITLE)
        compose.onNodeWithTag(Tags.DETAILS_TITLE).assertTextEquals("Item 5")
        compose.onNodeWithTag(Tags.BACK).performClick()
        compose.waitForTag(Tags.SEARCH_FIELD)
        compose.onNodeWithTag(Tags.SEARCH_FIELD).performTextInput("slow")
        compose.onNodeWithTag(Tags.SEARCH_BUTTON).performClick()
        compose.waitForTag(Tags.item(5))
        compose.onNodeWithTag(Tags.item(5)).performClick()
        compose.waitForTag(Tags.DETAILS_TITLE)
        compose.onNodeWithTag(Tags.DETAILS_TITLE).assertTextEquals("Item 5")
    }
}
