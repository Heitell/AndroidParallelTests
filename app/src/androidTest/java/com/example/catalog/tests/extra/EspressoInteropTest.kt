package com.example.catalog.tests.extra

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.catalog.support.BaseTest
import com.example.catalog.support.Fixtures
import com.example.catalog.support.Net
import com.example.catalog.support.Responses
import com.example.catalog.support.waitForGone
import com.example.catalog.support.waitForTag
import com.example.catalog.ui.Tags
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Compose вместе с Espresso: системная кнопка «назад», клавиатура, корневое окно. */
@RunWith(AndroidJUnit4::class)
class EspressoInteropTest : BaseTest() {

    @Before
    fun serve() {
        routes.on("items") { request, _ ->
            val q = request.url.queryParameter("q")
            Responses.items(if (q == null) Fixtures.items(4) else listOf(Fixtures.item(9, "Found $q")), Net.FAST)
        }
        (1..4).forEach { id -> routes.on("items/$id") { _, _ -> Responses.details(Fixtures.details(id), Net.FAST) } }
    }

    private fun openDetails(id: Int) {
        app.launch()
        compose.waitForTag(Tags.item(id))
        compose.onNodeWithTag(Tags.item(id)).performClick()
        compose.waitForTag(Tags.DETAILS_TITLE)
    }

    @Test
    fun systemBackClosesDetails() {
        openDetails(1)
        Espresso.pressBack()
        compose.waitForTag(Tags.LIST)
        compose.waitForGone(Tags.DETAILS)
    }

    @Test
    fun systemBackKeepsListContent() {
        openDetails(2)
        Espresso.pressBack()
        compose.waitForTag(Tags.item(4))
        compose.onNodeWithTag(Tags.item(4)).assertIsDisplayed()
    }

    @Test
    fun rootViewIsDisplayed() {
        app.launch()
        compose.waitForTag(Tags.LIST)
        onView(isRoot()).check(matches(isDisplayed()))
    }

    @Test
    fun searchWorksAfterClosingKeyboard() {
        app.launch()
        compose.waitForTag(Tags.LIST)
        compose.onNodeWithTag(Tags.SEARCH_FIELD).performTextInput("lamp")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithTag(Tags.SEARCH_BUTTON).performClick()
        compose.waitForTag(Tags.item(9))
        compose.onNodeWithTag(Tags.item(9)).assertIsDisplayed()
    }

    @Test
    fun reopenDifferentItemAfterBack() {
        openDetails(3)
        Espresso.pressBack()
        compose.waitForTag(Tags.item(4))
        compose.onNodeWithTag(Tags.item(4)).performClick()
        compose.waitForTag(Tags.DETAILS_TITLE)
        compose.onNodeWithTag(Tags.DETAILS_TITLE).assertTextEquals("Item 4")
    }
}
