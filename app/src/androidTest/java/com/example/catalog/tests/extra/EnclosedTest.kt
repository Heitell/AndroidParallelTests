package com.example.catalog.tests.extra

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.catalog.support.BaseTest
import com.example.catalog.support.Fixtures
import com.example.catalog.support.Net
import com.example.catalog.support.Responses
import com.example.catalog.support.waitForTag
import com.example.catalog.ui.Tags
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith

/**
 * Вложенные классы через Enclosed: у тестов имена вида `EnclosedTest$Errors`.
 * Знак `$` в имени класса опасен для способов, которые передают имя через оболочку устройства.
 */
@RunWith(Enclosed::class)
class EnclosedTest {

    class Errors : BaseTest() {
        private fun failWith(code: Int) {
            routes.on("items") { _, _ -> Responses.error(code, Net.FAST) }
            app.launch()
            compose.waitForTag(Tags.ERROR_MESSAGE)
        }

        @Test
        fun forbidden() {
            failWith(403)
            compose.onNodeWithTag(Tags.ERROR_MESSAGE).assertTextEquals("Server error (403)")
        }

        @Test
        fun unavailable() {
            failWith(503)
            compose.onNodeWithTag(Tags.ERROR_MESSAGE).assertTextEquals("Server error (503)")
        }
    }

    class Details : BaseTest() {
        private fun open(id: Int) {
            routes.on("items") { _, _ -> Responses.items(Fixtures.items(2), Net.FAST) }
            routes.on("items/$id") { _, _ -> Responses.details(Fixtures.details(id), Net.FAST) }
            app.launch()
            compose.waitForTag(Tags.item(id))
            compose.onNodeWithTag(Tags.item(id)).performClick()
            compose.waitForTag(Tags.DETAILS_PRICE)
        }

        @Test
        fun firstPrice() {
            open(1)
            compose.onNodeWithTag(Tags.DETAILS_PRICE).assertTextEquals("$10.50")
        }

        @Test
        fun secondPrice() {
            open(2)
            compose.onNodeWithTag(Tags.DETAILS_PRICE).assertTextEquals("$21.00")
        }
    }
}
