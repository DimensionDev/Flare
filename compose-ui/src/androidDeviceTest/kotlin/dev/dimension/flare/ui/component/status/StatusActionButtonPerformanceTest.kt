package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Heart
import dev.dimension.flare.data.model.appearance.TimelineAppearance
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatusActionButtonPerformanceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun scrollingDoesNotReplaceActionIcons() {
        val scrolling = mutableStateOf(false)
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalIsScrollingInProgress provides scrolling.value,
                    LocalTimelineAppearance provides TimelineAppearance(),
                ) {
                    Column {
                        repeat(16) {
                            StatusActionButton(
                                icon = FontAwesomeIcons.Solid.Heart,
                                number = null,
                                onClicked = {},
                            )
                        }
                    }
                }
            }
        }
        val originalIds = composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().map { it.id }
        assertEquals(16, originalIds.size)

        repeat(5) {
            composeRule.runOnIdle { scrolling.value = true }
            assertEquals(originalIds, composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().map { it.id })
            composeRule.runOnIdle { scrolling.value = false }
            assertEquals(originalIds, composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().map { it.id })
        }
    }
}
