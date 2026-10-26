package com.example.catalog.tests

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.catalog.support.BaseTest
import com.example.catalog.support.Fixtures
import com.example.catalog.support.Net
import com.example.catalog.support.Responses
import com.example.catalog.support.WAIT_MS
import com.example.catalog.support.waitForTag
import com.example.catalog.ui.Tags
import org.junit.Test
import org.junit.runner.RunWith

/** Медленная сеть: самые долгие тесты набора, на них видно, как делятся шарды. */
@RunWith(AndroidJUnit4::class)
class SlowNetworkTest : BaseTest() {

    @Test
    fun slowListEventuallyLoads() {
        routes.on("items") { _, _ -> Responses.items(Fixtures.items(4), Net.SLOW) }
        app.launch()
        compose.waitForTag(Tags.item(4))
        compose.onNodeWithTag(Tags.LIST).assertIsDisplayed()
    }

    @Test
    fun slowDetailsEventuallyLoad() {
        routes.on("items") { _, _ -> Responses.items(Fixtures.items(2), Net.FAST) }
        routes.on("items/1") { _, _ -> Responses.details(Fixtures.details(1), Net.SLOW) }
        app.launch()
        compose.waitForTag(Tags.item(1))
        compose.onNodeWithTag(Tags.item(1)).performClick()
        compose.waitForTag(Tags.DETAILS_TITLE)
    }

    @Test
    fun slowSearchEventuallyLoads() {
        routes.on("items") { request, _ ->
            if (request.url.queryParameter("q") == null) Responses.items(Fixtures.items(3), Net.FAST)
            else Responses.items(listOf(Fixtures.item(9)), Net.SLOW)
        }
        app.launch()
        compose.waitForTag(Tags.LIST)
        compose.onNodeWithTag(Tags.SEARCH_FIELD).performTextInput("camera")
        compose.onNodeWithTag(Tags.SEARCH_BUTTON).performClick()
        compose.waitForTag(Tags.item(9))
    }

    @Test
    fun loadingStaysDuringSlowResponse() {
        routes.on("items") { _, _ -> Responses.items(Fixtures.items(2), Net.SLOW) }
        app.launch()
        compose.waitForTag(Tags.LOADING)
        // Запрос уже дошёл до сервера, а ответ ещё в пути: индикатор должен оставаться.
        compose.waitUntil(WAIT_MS) { routes.callsTo("items") == 1 }
        compose.onNodeWithTag(Tags.LOADING).assertIsDisplayed()
        compose.waitForTag(Tags.item(1))
    }
}
