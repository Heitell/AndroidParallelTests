package com.example.catalog.tests.extra

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.catalog.support.BaseTest
import com.example.catalog.support.Fixtures
import com.example.catalog.support.Net
import com.example.catalog.support.Responses
import com.example.catalog.support.waitForGone
import com.example.catalog.support.waitForTag
import com.example.catalog.ui.Tags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Приложение запоминает последний запрос на диске. Тесты здесь меняют это состояние;
 * чистое состояние перед каждым тестом даёт MockServerRule. Без этой очистки тест,
 * ожидающий пустой запрос, зависел бы от того, какой тест шёл перед ним в том же процессе.
 */
@RunWith(AndroidJUnit4::class)
class StatefulTest : BaseTest() {

    @Before
    fun serve() {
        routes.on("items") { request, _ ->
            val q = request.url.queryParameter("q")
            Responses.items(if (q == null) Fixtures.items(3) else listOf(Fixtures.item(8, "Found $q")), Net.FAST)
        }
    }

    private fun search(text: String) {
        compose.onNodeWithTag(Tags.SEARCH_FIELD).performTextInput(text)
        compose.onNodeWithTag(Tags.SEARCH_BUTTON).performClick()
        compose.waitForTag(Tags.item(8))
    }

    @Test
    fun startsWithEmptyQuery() {
        app.launch()
        compose.waitForTag(Tags.item(1))
        assertNull(routes.requests.single().url.queryParameter("q"))
    }

    @Test
    fun searchIsRestoredAfterRestart() {
        app.launch()
        compose.waitForTag(Tags.item(1))
        search("saved")
        // Новый экран и новая ViewModel: запрос можно взять только с диска.
        app.close()
        app.launch()
        compose.waitForTag(Tags.item(8))
        compose.onNodeWithTag(Tags.SEARCH_FIELD).assertTextContains("saved")
        assertEquals("saved", routes.requests.last().url.queryParameter("q"))
    }

    @Test
    fun clearForgetsSavedQuery() {
        app.launch()
        compose.waitForTag(Tags.item(1))
        search("temp")
        compose.onNodeWithTag(Tags.CLEAR_BUTTON).performClick()
        compose.waitForTag(Tags.item(1))
        app.close()
        app.launch()
        compose.waitForTag(Tags.item(1))
        compose.waitForGone(Tags.CLEAR_BUTTON)
        assertNull(routes.requests.last().url.queryParameter("q"))
    }

    @Test
    fun startsWithEmptyQueryAgain() {
        // Тот же контроль, что и первый тест: если очистка состояния сломается,
        // один из двух упадёт при любом порядке запуска после searchIsRestoredAfterRestart.
        app.launch()
        compose.waitForTag(Tags.item(1))
        assertNull(routes.requests.single().url.queryParameter("q"))
    }
}
