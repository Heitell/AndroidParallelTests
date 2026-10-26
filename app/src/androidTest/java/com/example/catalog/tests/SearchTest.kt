package com.example.catalog.tests

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.catalog.support.BaseTest
import com.example.catalog.support.Fixtures
import com.example.catalog.support.Net
import com.example.catalog.support.Responses
import com.example.catalog.support.waitForGone
import com.example.catalog.support.waitForTag
import com.example.catalog.support.waitForText
import com.example.catalog.ui.Tags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchTest : BaseTest() {

    @Before
    fun serveCatalog() {
        routes.on("items") { request, _ ->
            val q = request.url.queryParameter("q")
            when (q) {
                null -> Responses.items(Fixtures.items(10), Net.FAST)
                "nothing" -> Responses.items(emptyList(), Net.MEDIUM)
                else -> Responses.items(listOf(Fixtures.item(7, title = "Found $q")), Net.MEDIUM)
            }
        }
    }

    private fun searchFor(text: String) {
        app.launch()
        compose.waitForTag(Tags.LIST)
        compose.onNodeWithTag(Tags.SEARCH_FIELD).performTextInput(text)
        compose.onNodeWithTag(Tags.SEARCH_BUTTON).performClick()
    }

    private fun lastQuery() = routes.requests.last().url.queryParameter("q")

    @Test
    fun searchSendsQueryParameter() {
        searchFor("phone")
        compose.waitForText("Found phone")
        assertEquals("phone", lastQuery())
    }

    @Test
    fun searchShowsFilteredResults() {
        searchFor("phone")
        compose.waitForText("Found phone")
        compose.onNodeWithTag(Tags.item(7)).assertTextContains("Found phone")
        compose.waitForGone(Tags.item(1))
    }

    @Test
    fun searchWithNoResultsShowsEmptyState() {
        searchFor("nothing")
        compose.waitForTag(Tags.EMPTY)
        compose.onNodeWithTag(Tags.EMPTY).assertIsDisplayed()
    }

    @Test
    fun clearButtonAppearsWhenTyping() {
        app.launch()
        compose.waitForTag(Tags.LIST)
        compose.waitForGone(Tags.CLEAR_BUTTON)
        compose.onNodeWithTag(Tags.SEARCH_FIELD).performTextInput("a")
        compose.onNodeWithTag(Tags.CLEAR_BUTTON).assertIsDisplayed()
    }

    @Test
    fun clearResetsQueryAndReloadsFullList() {
        searchFor("phone")
        compose.waitForText("Found phone")
        compose.onNodeWithTag(Tags.CLEAR_BUTTON).performClick()
        compose.waitForTag(Tags.item(1))
        assertNull(lastQuery())
        assertEquals(3, routes.callsTo("items"))
    }

    @Test
    fun imeSearchActionTriggersSearch() {
        app.launch()
        compose.waitForTag(Tags.LIST)
        compose.onNodeWithTag(Tags.SEARCH_FIELD).performTextInput("laptop")
        compose.onNodeWithTag(Tags.SEARCH_FIELD).performImeAction()
        compose.waitForText("Found laptop")
        assertEquals("laptop", lastQuery())
    }

    @Test
    fun searchTrimsWhitespace() {
        searchFor("  phone  ")
        compose.waitForText("Found phone")
        assertEquals("phone", lastQuery())
    }

    @Test
    fun blankQueryLoadsFullList() {
        searchFor("   ")
        compose.waitForTag(Tags.item(1))
        assertNull(lastQuery())
        assertEquals(2, routes.callsTo("items"))
    }
}
