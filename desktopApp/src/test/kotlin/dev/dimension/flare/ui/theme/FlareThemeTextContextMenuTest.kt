package dev.dimension.flare.ui.theme

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import org.junit.Rule
import org.junit.Test

class FlareThemeTextContextMenuTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectedTextRightClickDoesNotThrow() {
        composeRule.setContent {
            FlareTheme(isDarkTheme = false) {
                SelectionContainer {
                    BasicText("Selectable text")
                }
            }
        }

        composeRule.onNodeWithText("Selectable text").performMouseInput {
            doubleClick()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Selectable text").performMouseInput {
            rightClick()
        }
        composeRule.waitForIdle()
    }
}
