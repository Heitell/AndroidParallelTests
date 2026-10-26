package com.example.catalog.tests.extra

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.catalog.support.BaseTest
import com.example.catalog.support.Fixtures
import com.example.catalog.support.Net
import com.example.catalog.support.Responses
import com.example.catalog.support.waitForTag
import com.example.catalog.ui.Tags
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** Ограничение времени средствами JUnit: параметр `@Test(timeout)` и правило `Timeout`. */
@RunWith(AndroidJUnit4::class)
class TimeoutTest : BaseTest() {

    @get:Rule
    val limit: Timeout = Timeout(60, TimeUnit.SECONDS)

    private fun showAndOpen(id: Int) {
        routes.on("items") { _, _ -> Responses.items(Fixtures.items(3), Net.FAST) }
        routes.on("items/$id") { _, _ -> Responses.details(Fixtures.details(id), Net.FAST) }
        app.launch()
        compose.waitForTag(Tags.item(id))
        compose.onNodeWithTag(Tags.item(id)).performClick()
        compose.waitForTag(Tags.DETAILS_TITLE)
    }

    @Test(timeout = 45_000)
    fun withTimeoutParameter() = showAndOpen(1)

    @Test
    fun withTimeoutRuleOnly() = showAndOpen(2)

    @Test(timeout = 45_000)
    fun withBoth() = showAndOpen(3)
}
