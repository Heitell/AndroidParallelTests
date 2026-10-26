package com.example.catalog.support

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.core.app.ActivityScenario
import com.example.catalog.MainActivity
import org.junit.rules.ExternalResource

const val WAIT_MS = 30_000L

/** Запускает экран после того, как тест настроил ответы заглушки, и закрывает его в конце. */
class AppLauncher : ExternalResource() {
    private var scenario: ActivityScenario<MainActivity>? = null

    fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    /** Закрыть экран посреди теста, например чтобы проверить перезапуск. */
    fun close() {
        scenario?.close()
        scenario = null
    }

    override fun after() = close()
}

@OptIn(ExperimentalTestApi::class)
fun ComposeTestRule.waitForTag(tag: String, timeoutMs: Long = WAIT_MS) =
    waitUntilAtLeastOneExists(hasTestTag(tag), timeoutMs)

@OptIn(ExperimentalTestApi::class)
fun ComposeTestRule.waitForText(text: String, timeoutMs: Long = WAIT_MS) =
    waitUntilAtLeastOneExists(hasText(text), timeoutMs)

@OptIn(ExperimentalTestApi::class)
fun ComposeTestRule.waitForGone(tag: String, timeoutMs: Long = WAIT_MS) =
    waitUntilDoesNotExist(hasTestTag(tag), timeoutMs)
