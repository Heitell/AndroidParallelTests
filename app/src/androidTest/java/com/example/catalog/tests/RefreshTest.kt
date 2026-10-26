package com.example.catalog.tests

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.catalog.support.BaseTest
import com.example.catalog.support.Fixtures
import com.example.catalog.support.Net
import com.example.catalog.support.Responses
import com.example.catalog.support.WAIT_MS
import com.example.catalog.support.waitForTag
import com.example.catalog.ui.Tags
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RefreshTest : BaseTest() {

    private fun pullToRefresh() {
        compose.onNodeWithTag(Tags.LIST).performTouchInput { swipeDown() }
    }

    private fun waitForCalls(n: Int) = compose.waitUntil(WAIT_MS) { routes.callsTo("items") >= n }

    @Test
    fun pullToRefreshRequestsAgain() {
        routes.on("items") { _, _ -> Responses.items(Fixtures.items(3), Net.MEDIUM) }
        app.launch()
        compose.waitForTag(Tags.LIST)
        pullToRefresh()
        waitForCalls(2)
        assertEquals(2, routes.callsTo("items"))
    }

    @Test
    fun refreshShowsUpdatedData() {
        routes.on("items") { _, call ->
            Responses.items(if (call == 0) Fixtures.items(2) else Fixtures.items(2, from = 10), Net.MEDIUM)
        }
        app.launch()
        compose.waitForTag(Tags.item(1))
        pullToRefresh()
        compose.waitForTag(Tags.item(10))
        compose.onNodeWithTag(Tags.item(11)).assertIsDisplayed()
    }

    @Test
    fun listStaysVisibleDuringRefresh() {
        routes.on("items") { _, _ -> Responses.items(Fixtures.items(3), Net.MEDIUM) }
        app.launch()
        compose.waitForTag(Tags.LIST)
        pullToRefresh()
        waitForCalls(2)
        compose.onNodeWithTag(Tags.item(1)).assertIsDisplayed()
    }

    @Test
    fun refreshKeepsSearchQuery() {
        routes.on("items") { _, _ -> Responses.items(Fixtures.items(3), Net.MEDIUM) }
        app.launch()
        compose.waitForTag(Tags.LIST)
        compose.onNodeWithTag(Tags.SEARCH_FIELD).performTextInput("audio")
        compose.onNodeWithTag(Tags.SEARCH_BUTTON).performClick()
        waitForCalls(2)
        compose.waitForTag(Tags.LIST)
        pullToRefresh()
        waitForCalls(3)
        assertEquals("audio", routes.requests.last().url.queryParameter("q"))
    }

    @Test
    fun failedRefreshShowsError() {
        routes.on("items") { _, call -> if (call == 0) Responses.items(Fixtures.items(3), Net.MEDIUM) else Responses.error(500, Net.MEDIUM) }
        app.launch()
        compose.waitForTag(Tags.LIST)
        pullToRefresh()
        compose.waitForTag(Tags.ERROR)
        compose.onNodeWithTag(Tags.RETRY).assertIsDisplayed()
    }
}
