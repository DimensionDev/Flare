package dev.dimension.flare.ui.screen.status.action

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class AltTextSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun longAltTextCanBeScrolled() {
        val longAltText = List(30) { "Long alternative text" }.joinToString("\n")

        composeRule.setContent {
            MaterialTheme {
                Box(
                    modifier =
                        Modifier
                            .width(360.dp)
                            .height(320.dp),
                ) {
                    AltTextSheet(
                        text = longAltText,
                        onBack = {},
                    )
                }
            }
        }

        val scrollableContent = composeRule.onNode(hasScrollAction())
        val hasVerticalOverflow =
            SemanticsMatcher("has vertical overflow") { node ->
                node.config[SemanticsProperties.VerticalScrollAxisRange].maxValue() > 0f
            }

        scrollableContent.assert(hasVerticalOverflow)
        scrollableContent.performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy ->
            scrollBy(0f, Float.MAX_VALUE)
        }
        scrollableContent.assert(
            SemanticsMatcher("has scrolled to the bottom") { node ->
                val scrollRange = node.config[SemanticsProperties.VerticalScrollAxisRange]
                scrollRange.value() == scrollRange.maxValue()
            },
        )
    }
}
