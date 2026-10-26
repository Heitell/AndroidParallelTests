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
import org.junit.Test
import org.junit.runner.RunWith

/** Короткий таймаут клиента (6 с), чтобы тест на зависший сервер не тянул шард. */
@RunWith(AndroidJUnit4::class)
class ErrorHandlingTest : BaseTest(timeoutMs = 6_000) {

    private fun expectError(message: String) {
        compose.waitForTag(Tags.ERROR_MESSAGE)
        compose.onNodeWithTag(Tags.ERROR_MESSAGE).assertTextEquals(message)
    }

    @Test
    fun serverErrorShowsMessage() {
        routes.on("items") { _, _ -> Responses.error(500) }
        app.launch()
        expectError("Server error (500)")
    }

    @Test
    fun serviceUnavailableShowsMessage() {
        routes.on("items") { _, _ -> Responses.error(503) }
        app.launch()
        expectError("Server error (503)")
    }

    @Test
    fun retryAfterServerErrorShowsItems() {
        routes.on("items") { _, call -> if (call == 0) Responses.error(500) else Responses.items(Fixtures.items(3)) }
        app.launch()
        expectError("Server error (500)")
        compose.onNodeWithTag(Tags.RETRY).performClick()
        compose.waitForTag(Tags.item(1))
        assertEquals(2, routes.callsTo("items"))
    }

    @Test
    fun retryShowsLoadingAgain() {
        routes.on("items") { _, _ -> Responses.error(500, Net.FAST) }
        app.launch()
        expectError("Server error (500)")
        compose.onNodeWithTag(Tags.RETRY).performClick()
        compose.waitForTag(Tags.LOADING)
        expectError("Server error (500)")
    }

    @Test
    fun droppedConnectionShowsNoConnection() {
        routes.on("items") { _, _ -> Responses.disconnect() }
        app.launch()
        expectError("No connection")
    }

    @Test
    fun silentServerShowsTimeout() {
        routes.on("items") { _, _ -> Responses.stall() }
        app.launch()
        expectError("Request timed out")
    }

    @Test
    fun malformedJsonShowsUnexpectedResponse() {
        routes.on("items") { _, _ -> Responses.ok("""{"not":"a list"}""", Net.FAST) }
        app.launch()
        expectError("Unexpected response")
    }

    @Test
    fun detailsErrorRetrySucceeds() {
        routes.on("items") { _, _ -> Responses.items(Fixtures.items(2)) }
        routes.on("items/2") { _, call ->
            if (call == 0) Responses.error(500) else Responses.details(Fixtures.details(2))
        }
        app.launch()
        compose.waitForTag(Tags.item(2))
        compose.onNodeWithTag(Tags.item(2)).performClick()
        expectError("Server error (500)")
        compose.onNodeWithTag(Tags.RETRY).performClick()
        compose.waitForTag(Tags.DETAILS_TITLE)
        compose.onNodeWithTag(Tags.DETAILS_TITLE).assertIsDisplayed()
    }
}
