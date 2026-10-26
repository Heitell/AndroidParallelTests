package com.example.catalog.tests.extra

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.MediumTest
import androidx.test.filters.SmallTest
import com.example.catalog.support.BaseTest
import com.example.catalog.support.Fixtures
import com.example.catalog.support.Net
import com.example.catalog.support.Responses
import com.example.catalog.support.waitForTag
import com.example.catalog.ui.Tags
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Метки размера и своя метка @Smoke: по ним способы должны уметь отбирать тесты. */
@RunWith(AndroidJUnit4::class)
class AnnotatedTest : BaseTest() {

    @Before
    fun serve() {
        routes.on("items") { _, _ -> Responses.items(Fixtures.items(3), Net.FAST) }
        (1..3).forEach { id -> routes.on("items/$id") { _, _ -> Responses.details(Fixtures.details(id), Net.FAST) } }
        app.launch()
        compose.waitForTag(Tags.item(1))
    }

    private fun open(id: Int) {
        compose.onNodeWithTag(Tags.item(id)).performClick()
        compose.waitForTag(Tags.DETAILS_TITLE)
        compose.onNodeWithTag(Tags.DETAILS_TITLE).assertTextEquals("Item $id")
    }

    @Smoke @SmallTest @Test
    fun smokeListIsShown() = compose.onNodeWithTag(Tags.LIST).assertIsDisplayed().let { }

    @Smoke @MediumTest @Test
    fun smokeOpensFirstItem() = open(1)

    @Smoke @LargeTest @Test
    fun smokeOpensLastItem() = open(3)

    @SmallTest @Test
    fun smallOnly() = compose.onNodeWithTag(Tags.item(3)).assertIsDisplayed().let { }

    @MediumTest @Test
    fun mediumOnly() = open(2)

    @LargeTest @Test
    fun largeOnly() {
        open(2)
        compose.onNodeWithTag(Tags.BACK).performClick()
        open(3)
    }
}
