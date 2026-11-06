package dev.dimension.flare.ui.screen.counter

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.dimension.flare.R
import org.junit.Rule
import org.junit.Test

class CounterScreenTest {
    @get:Rule val compose = createAndroidComposeRule<CounterTestActivity>()

    @Test
    fun cruxActionsAndRepeatedNavigationResetState() {
        repeat(8) {
            open()
            compose.onNodeWithTag("counter-increment").performClick()
            value("1")
            compose.onNodeWithTag("counter-decrement").performClick()
            value("0")
            compose.onNodeWithTag("counter-decrement").performClick()
            value("-1")
            compose.onNodeWithTag("counter-reset").performClick()
            value("0")
            back()
        }
    }

    @Test
    fun recreationRetainsTheCoreAndPoppingResetsIt() {
        open()
        compose.onNodeWithTag("counter-increment").performClick()
        value("1")
        compose.activityRule.scenario.recreate()
        value("1")
        back()
        open()
        value("0")
        back()
    }

    @Test
    fun stackedCounterPagesHaveIndependentCores() {
        open()
        compose.onNodeWithTag("counter-increment").performClick()
        value("1")
        compose.onNodeWithTag("counter-next").performClick()
        value("0")
        compose.onNodeWithTag("counter-decrement").performClick()
        value("-1")
        back()
        value("1")
        back()
    }

    @Test
    fun actionFailureKeepsContentAndAllowsRecovery() {
        compose.activityRule.scenario.onActivity { it.intent.putExtra("counter-initial-value", Long.MAX_VALUE) }
        open(Long.MAX_VALUE.toString())
        compose.onNodeWithTag("counter-increment").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("counter-error")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("counter-error").assertTextEquals("counter value or state revision would overflow")
        value(Long.MAX_VALUE.toString())
        compose.onNodeWithTag("counter-reset").performClick()
        value("0")
        compose.onNodeWithTag("counter-error").assertDoesNotExist()
        back()
    }

    private fun open(initial: String = "0") {
        compose.onNodeWithTag("compose").performClick()
        value(initial)
    }

    private fun back() {
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.navigate_back)).performClick()
    }

    private fun value(expected: String) {
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("counter-value") and hasText(expected)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("counter-value").assertTextEquals(expected)
    }
}
