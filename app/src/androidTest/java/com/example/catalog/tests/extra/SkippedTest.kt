package com.example.catalog.tests.extra

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.catalog.support.BaseTest
import com.example.catalog.support.Fixtures
import com.example.catalog.support.Responses
import com.example.catalog.support.waitForTag
import com.example.catalog.ui.Tags
import org.junit.Assume.assumeTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/** Пропуски: способ не должен считать их ни прошедшими, ни потерянными. */
@RunWith(AndroidJUnit4::class)
class SkippedTest : BaseTest() {

    private fun showList() {
        routes.on("items") { _, _ -> Responses.items(Fixtures.items(2), 0) }
        app.launch()
        compose.waitForTag(Tags.item(2))
        compose.onNodeWithTag(Tags.LIST).assertIsDisplayed()
    }

    @Test
    fun runsNormally() = showList()

    @Test
    fun alsoRunsNormally() = showList()

    @Ignore("пример пропуска через @Ignore")
    @Test
    fun ignoredOne() = showList()

    @Ignore("пример пропуска через @Ignore")
    @Test
    fun ignoredTwo() = showList()

    @Test
    fun assumptionFailsBeforeWork() {
        assumeTrue("пропуск по условию до работы", false)
        showList()
    }

    @Test
    fun assumptionFailsAfterLaunch() {
        showList()
        assumeTrue("пропуск по условию после запуска экрана", false)
    }
}

/** Весь класс пропущен. */
@Ignore("пример пропуска целого класса")
@RunWith(AndroidJUnit4::class)
class IgnoredClassTest : BaseTest() {
    @Test
    fun first() = Unit

    @Test
    fun second() = Unit
}
