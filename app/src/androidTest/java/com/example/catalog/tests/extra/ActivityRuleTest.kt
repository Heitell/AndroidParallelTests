package com.example.catalog.tests.extra

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.catalog.MainActivity
import com.example.catalog.support.Fixtures
import com.example.catalog.support.MockServerRule
import com.example.catalog.support.Net
import com.example.catalog.support.Responses
import com.example.catalog.support.waitForTag
import com.example.catalog.ui.Tags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Без общего базового класса: экран запускает само правило Compose до тела теста,
 * поэтому ответы заглушки задаются заранее, в правиле.
 */
@RunWith(AndroidJUnit4::class)
class ActivityRuleTest {
    private val server = MockServerRule {
        on("items") { _, _ -> Responses.items(Fixtures.items(6), Net.FAST) }
        (1..6).forEach { id -> on("items/$id") { _, _ -> Responses.details(Fixtures.details(id), Net.FAST) } }
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(server).around(compose)

    @Test
    fun listIsShownOnStart() {
        compose.waitForTag(Tags.item(6))
        compose.onNodeWithTag(Tags.LIST).assertIsDisplayed()
    }

    @Test
    fun startMakesExactlyOneListRequest() {
        compose.waitForTag(Tags.item(1))
        assertEquals(1, server.routes.callsTo("items"))
    }

    @Test
    fun opensDetailsOfTappedItem() {
        compose.waitForTag(Tags.item(5))
        compose.onNodeWithTag(Tags.item(5)).performClick()
        compose.waitForTag(Tags.DETAILS_TITLE)
        compose.onNodeWithTag(Tags.DETAILS_TITLE).assertTextEquals("Item 5")
    }

    @Test
    fun detailsSurviveRecreation() {
        compose.waitForTag(Tags.item(3))
        compose.onNodeWithTag(Tags.item(3)).performClick()
        compose.waitForTag(Tags.DETAILS_TITLE)
        compose.activityRule.scenario.recreate()
        compose.waitForTag(Tags.DETAILS_TITLE)
        compose.onNodeWithTag(Tags.DETAILS_TITLE).assertTextEquals("Item 3")
    }

    @Test
    fun recreationDoesNotReloadList() {
        compose.waitForTag(Tags.item(1))
        compose.activityRule.scenario.recreate()
        compose.waitForTag(Tags.item(1))
        // Список живёт в ViewModel, при пересоздании экрана повторного запроса нет.
        assertEquals(1, server.routes.callsTo("items"))
    }

    @Test
    fun backButtonReturnsToList() {
        compose.waitForTag(Tags.item(2))
        compose.onNodeWithTag(Tags.item(2)).performClick()
        compose.waitForTag(Tags.BACK)
        compose.onNodeWithTag(Tags.BACK).performClick()
        compose.waitForTag(Tags.item(2))
        compose.onNodeWithTag(Tags.LIST).assertIsDisplayed()
    }
}
