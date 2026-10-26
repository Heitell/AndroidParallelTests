package com.example.catalog.tests

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.catalog.support.BaseTest
import com.example.catalog.support.Fixtures
import com.example.catalog.support.Net
import com.example.catalog.support.Responses
import com.example.catalog.support.waitForTag
import com.example.catalog.ui.Tags
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DetailsTest : BaseTest() {

    @Before
    fun serveCatalog() {
        routes.on("items") { _, _ -> Responses.items(Fixtures.items(5), Net.FAST) }
        (1..5).forEach { id ->
            routes.on("items/$id") { _, _ -> Responses.details(Fixtures.details(id, inStock = id != 4), Net.MEDIUM) }
        }
    }

    private fun open(id: Int) {
        app.launch()
        compose.waitForTag(Tags.item(id))
        compose.onNodeWithTag(Tags.item(id)).performClick()
    }

    @Test
    fun tapOpensDetails() {
        open(1)
        compose.waitForTag(Tags.DETAILS_TITLE)
        compose.onNodeWithTag(Tags.DETAILS).assertIsDisplayed()
    }

    @Test
    fun detailsShowTitleAndPrice() {
        open(2)
        compose.waitForTag(Tags.DETAILS_TITLE)
        compose.onNodeWithTag(Tags.DETAILS_TITLE).assertTextEquals("Item 2")
        compose.onNodeWithTag(Tags.DETAILS_PRICE).assertTextEquals("$21.00")
    }

    @Test
    fun detailsShowDescription() {
        open(3)
        compose.waitForTag(Tags.DETAILS_DESCRIPTION)
        compose.onNodeWithTag(Tags.DETAILS_DESCRIPTION).assertTextEquals("Description of Item 3")
    }

    @Test
    fun detailsShowOutOfStock() {
        open(4)
        compose.waitForTag(Tags.DETAILS_STOCK)
        compose.onNodeWithTag(Tags.DETAILS_STOCK).assertTextEquals("Out of stock")
    }

    @Test
    fun detailsRequestItemById() {
        open(3)
        compose.waitForTag(Tags.DETAILS_TITLE)
        assertEquals(1, routes.callsTo("items/3"))
    }

    @Test
    fun backReturnsToList() {
        open(1)
        compose.waitForTag(Tags.DETAILS_TITLE)
        compose.onNodeWithTag(Tags.BACK).performClick()
        compose.waitForTag(Tags.LIST)
        compose.onNodeWithTag(Tags.item(1)).assertIsDisplayed()
    }

    @Test
    fun missingItemShowsNotFound() {
        routes.on("items/5") { _, _ -> Responses.error(404, Net.MEDIUM) }
        open(5)
        compose.waitForTag(Tags.ERROR_MESSAGE)
        compose.onNodeWithTag(Tags.ERROR_MESSAGE).assertTextEquals("Not found")
    }
}
