package com.example.catalog.tests

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.catalog.support.BaseTest
import com.example.catalog.support.Fixtures
import com.example.catalog.support.Net
import com.example.catalog.support.Responses
import com.example.catalog.support.waitForGone
import com.example.catalog.support.waitForTag
import com.example.catalog.ui.Tags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogListTest : BaseTest() {

    private fun serve(count: Int, delayMs: Long = Net.FAST) =
        routes.on("items") { _, _ -> Responses.items(Fixtures.items(count), delayMs) }

    @Test
    fun showsLoadingWhileRequestInFlight() {
        serve(3, Net.MEDIUM)
        app.launch()
        compose.waitForTag(Tags.LOADING)
        compose.onNodeWithTag(Tags.LOADING).assertIsDisplayed()
        compose.waitForGone(Tags.LOADING)
        compose.onNodeWithTag(Tags.LIST).assertIsDisplayed()
    }

    @Test
    fun showsAllItemsFromServer() {
        serve(5)
        app.launch()
        compose.waitForTag(Tags.LIST)
        (1..5).forEach { compose.onNodeWithTag(Tags.item(it)).assertIsDisplayed() }
    }

    @Test
    fun showsItemTitle() {
        serve(3)
        app.launch()
        compose.waitForTag(Tags.LIST)
        compose.onNodeWithText("Item 2").assertIsDisplayed()
    }

    @Test
    fun formatsPrice() {
        routes.on("items") { _, _ -> Responses.items(listOf(Fixtures.item(1, price = 12.5))) }
        app.launch()
        compose.waitForTag(Tags.LIST)
        compose.onNodeWithTag(Tags.itemPrice(1), useUnmergedTree = true).assertTextEquals("$12.50")
    }

    @Test
    fun showsCategory() {
        serve(1)
        app.launch()
        compose.waitForTag(Tags.LIST)
        compose.onNodeWithText(Fixtures.item(1).category).assertIsDisplayed()
    }

    @Test
    fun scrollsToLastItemInLongList() {
        serve(50)
        app.launch()
        compose.waitForTag(Tags.LIST)
        compose.onNodeWithTag(Tags.LIST).performScrollToNode(hasTestTag(Tags.item(50)))
        compose.onNodeWithTag(Tags.item(50)).assertIsDisplayed()
    }

    @Test
    fun requestsItemsWithoutQuery() {
        serve(2)
        app.launch()
        compose.waitForTag(Tags.LIST)
        val request = routes.requests.first()
        assertEquals("/api/items", request.url.encodedPath)
        assertNull(request.url.queryParameter("q"))
    }

    @Test
    fun emptyListShowsEmptyState() {
        serve(0)
        app.launch()
        compose.waitForTag(Tags.EMPTY)
        compose.onNodeWithText("Nothing found").assertIsDisplayed()
    }
}
