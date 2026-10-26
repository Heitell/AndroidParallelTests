package com.example.catalog.tests.extra

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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

/** Тесты объявлены в абстрактном родителе, запускаются в каждом наследнике со своими данными. */
abstract class ListContract(private val count: Int) : BaseTest() {

    @Before
    fun serve() {
        routes.on("items") { _, _ -> Responses.items(Fixtures.items(count), Net.FAST) }
        routes.on("items/$count") { _, _ -> Responses.details(Fixtures.details(count), Net.FAST) }
        app.launch()
        compose.waitForTag(Tags.LIST)
    }

    @Test
    fun firstItemIsVisible() {
        compose.waitForTag(Tags.item(1))
        compose.onNodeWithTag(Tags.item(1)).assertIsDisplayed()
    }

    @Test
    fun lastItemIsReachable() {
        compose.onNodeWithTag(Tags.LIST).performScrollToNode(hasTestTag(Tags.item(count)))
        compose.onNodeWithTag(Tags.item(count)).assertIsDisplayed()
    }

    @Test
    fun lastItemOpensDetails() {
        compose.onNodeWithTag(Tags.LIST).performScrollToNode(hasTestTag(Tags.item(count)))
        compose.onNodeWithTag(Tags.item(count)).performClick()
        compose.waitForTag(Tags.DETAILS_TITLE)
        compose.onNodeWithTag(Tags.DETAILS_TITLE).assertTextContains("Item $count")
    }

    @Test
    fun oneRequestForWholeList() {
        compose.waitForTag(Tags.item(1))
        assertEquals(1, routes.callsTo("items"))
    }
}

@RunWith(AndroidJUnit4::class)
class ShortListTest : ListContract(count = 3)

@RunWith(AndroidJUnit4::class)
class LongListTest : ListContract(count = 60)
