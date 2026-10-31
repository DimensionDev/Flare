package dev.dimension.flare.ui.component.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import dev.dimension.flare.common.collectPagingState
import dev.dimension.flare.feature.agent.common.AgentChatHistoryMessage
import dev.dimension.flare.feature.agent.common.AgentInputRequest
import dev.dimension.flare.feature.agent.common.AgentInputRequestOptionButtonType
import dev.dimension.flare.feature.agent.presenter.AgentMessagePart
import dev.dimension.flare.ui.theme.FlareTheme
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AgentChatMessageListTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun assistantUsesTheContentWidthWithoutABubbleAndUserKeepsItsBubbleWidth() {
        val assistantText = "Assistant response ".repeat(12).trim()
        val userText = "User message ".repeat(12).trim()
        val messages = MutableStateFlow(listOf(message(assistantText), message(userText).copy(role = AgentChatHistoryMessage.Role.User)))
        composeRule.setContent {
            val state by messages.collectPagingState()
            FlareTheme(isDarkTheme = false) {
                Box(Modifier.size(300.dp, 500.dp).background(Color.Magenta).testTag("chat")) {
                    AgentChatMessageList(state, {}, {}, {})
                }
            }
        }

        val chat = composeRule.onNodeWithTag("chat")
        val chatBounds = chat.getUnclippedBoundsInRoot()
        val assistant = composeRule.onNodeWithText(assistantText).getUnclippedBoundsInRoot()
        val user = composeRule.onNodeWithText(userText).getUnclippedBoundsInRoot()
        val contentWidth = (chatBounds.right - chatBounds.left).value - 24f
        assertEquals(chatBounds.left.value + 12f, assistant.left.value, 1f)
        assertEquals(contentWidth, (assistant.right - assistant.left).value, 1f)
        assertEquals(contentWidth * 0.82f - 24f, (user.right - user.left).value, 1f)
        assertEquals(chatBounds.right.value - 24f, user.right.value, 1f)

        val pixels = chat.captureToImage().toPixelMap()
        val scale = pixels.width / (contentWidth + 24f)
        assertEquals(
            Color.Magenta,
            pixels[
                ((assistant.left.value - chatBounds.left.value + 1f) * scale).toInt(),
                ((assistant.top.value - chatBounds.top.value - 2f) * scale).toInt(),
            ],
        )
    }

    @Test
    fun destructiveConfirmationWrapsWithoutHidingItsTargetOrDeletionScope() {
        val warning =
            "Confirm deleting plugin Example (local.ai.p0123456789abcdef0123456789abcdef, revision 3), " +
                "including its source, accounts, credentials and local data."
        val option = AgentInputRequest.Option("confirm", warning, AgentInputRequestOptionButtonType.Destructive, warning)
        var selected: AgentInputRequest.Option? = null
        val messages =
            MutableStateFlow(
                listOf(
                    message("confirmation").copy(
                        parts = persistentListOf(AgentMessagePart.Actions(AgentInputRequest("delete", listOf(option)))),
                    ),
                ),
            )
        composeRule.setContent {
            val state by messages.collectPagingState()
            FlareTheme(isDarkTheme = false) {
                Box(Modifier.size(300.dp, 500.dp)) {
                    AgentChatMessageList(state, { selected = it }, {}, {})
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(warning, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            it(layouts)
        }
        assertTrue(layouts.single().lineCount > 1)
        assertFalse(layouts.single().hasVisualOverflow)
        composeRule.onNodeWithText(warning).assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(option, selected) }
    }

    @Test
    fun newReplyIsVisibleWhenReadingTheLatestMessages() {
        val messages = showMessages()
        composeRule.onNodeWithText("Reply 0").assertIsDisplayed()

        composeRule.runOnIdle { messages.value = listOf(message("New reply")) + messages.value }

        composeRule.onNodeWithText("New reply").assertIsDisplayed()
    }

    @Test
    fun newReplyDoesNotPullTheUserAwayFromHistory() {
        val messages = showMessages()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(20)
        composeRule.onNodeWithText("Reply 20").assertIsDisplayed()

        composeRule.runOnIdle { messages.value = listOf(message("New reply")) + messages.value }

        composeRule.onNodeWithText("Reply 20").assertIsDisplayed()
        composeRule.onNodeWithText("New reply").assertDoesNotExist()
    }

    private fun showMessages(): MutableStateFlow<List<AgentChatHistoryMessage>> {
        val messages = MutableStateFlow(List(40) { message("Reply $it") })
        composeRule.setContent {
            val state by messages.collectPagingState()
            FlareTheme(isDarkTheme = false) {
                Box(Modifier.size(300.dp, 240.dp)) {
                    AgentChatMessageList(
                        messages = state,
                        onInputRequestOptionSelected = {},
                        onPostClick = {},
                        onUserClick = {},
                        modifier = Modifier.testTag("messages"),
                    )
                }
            }
        }
        return messages
    }

    private fun message(text: String): AgentChatHistoryMessage =
        AgentChatHistoryMessage(
            id = text,
            role = AgentChatHistoryMessage.Role.Assistant,
            parts = persistentListOf(AgentMessagePart.Text(text)),
            createdAt = 0L,
        )
}
