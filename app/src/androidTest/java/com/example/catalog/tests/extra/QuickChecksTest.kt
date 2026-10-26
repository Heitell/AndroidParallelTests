package com.example.catalog.tests.extra

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.catalog.support.BaseTest
import com.example.catalog.support.Fixtures
import com.example.catalog.support.Responses
import com.example.catalog.support.waitForTag
import com.example.catalog.ui.Tags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Много коротких тестов на быстрой сети, около секунды каждый. */
@RunWith(AndroidJUnit4::class)
class QuickChecksTest : BaseTest() {

    private fun show(count: Int) {
        routes.on("items") { _, _ -> Responses.items(Fixtures.items(count), 0) }
        app.launch()
        compose.waitForTag(Tags.LIST)
    }

    private fun showOne(code: Int) {
        routes.on("items") { _, _ -> Responses.error(code, 0) }
        app.launch()
        compose.waitForTag(Tags.ERROR_MESSAGE)
    }

    @Test fun oneItem() { show(1); compose.waitForTag(Tags.item(1)) }
    @Test fun twoItems() { show(2); compose.waitForTag(Tags.item(2)) }
    @Test fun fiveItems() { show(5); compose.waitForTag(Tags.item(5)) }
    @Test fun titleOfFirst() { show(1); compose.waitForTag(Tags.item(1)); compose.onNodeWithText("Item 1").assertIsDisplayed() }
    @Test fun categoryOfFirst() { show(1); compose.waitForTag(Tags.item(1)); compose.onNodeWithTag(Tags.item(1)).assertTextContains("Laptops", substring = true) }
    @Test fun priceOfThird() { show(3); compose.waitForTag(Tags.item(3)); compose.onNodeWithTag(Tags.itemPrice(3), useUnmergedTree = true).assertTextEquals("$31.50") }
    @Test fun noQueryOnStart() { show(1); compose.waitForTag(Tags.item(1)); assertNull(routes.requests.single().url.queryParameter("q")) }
    @Test fun searchFieldIsShown() { show(1); compose.onNodeWithTag(Tags.SEARCH_FIELD).assertIsDisplayed() }
    @Test fun findButtonIsShown() { show(1); compose.onNodeWithTag(Tags.SEARCH_BUTTON).assertIsDisplayed() }
    @Test fun notFoundMessage() { showOne(404); compose.onNodeWithTag(Tags.ERROR_MESSAGE).assertTextEquals("Not found") }
    @Test fun serverErrorMessage() { showOne(500); compose.onNodeWithTag(Tags.ERROR_MESSAGE).assertTextEquals("Server error (500)") }
    @Test fun badGatewayMessage() { showOne(502); compose.onNodeWithTag(Tags.ERROR_MESSAGE).assertTextEquals("Server error (502)") }
    @Test fun retryIsShownOnError() { showOne(500); compose.onNodeWithTag(Tags.RETRY).assertIsDisplayed() }

    @Test
    fun retryMakesSecondRequest() {
        routes.on("items") { _, call -> if (call == 0) Responses.error(500, 0) else Responses.items(Fixtures.items(1), 0) }
        app.launch()
        compose.waitForTag(Tags.RETRY)
        compose.onNodeWithTag(Tags.RETRY).performClick()
        compose.waitForTag(Tags.item(1))
        assertEquals(2, routes.callsTo("items"))
    }

    @Test
    fun emptyListMessage() {
        routes.on("items") { _, _ -> Responses.items(emptyList(), 0) }
        app.launch()
        compose.waitForTag(Tags.EMPTY)
        compose.onNodeWithTag(Tags.EMPTY).assertTextEquals("Nothing found")
    }
}
