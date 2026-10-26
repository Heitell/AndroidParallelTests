package com.example.catalog.support

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import org.junit.Rule
import org.junit.rules.RuleChain

/** Порядок: заглушка поднимается первой, экран закрывается раньше неё. */
abstract class BaseTest(timeoutMs: Long = 20_000) {
    protected val server = MockServerRule(timeoutMs)
    protected val app = AppLauncher()
    protected val compose = createEmptyComposeRule()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(server).around(compose).around(app)

    protected val routes get() = server.routes
}
