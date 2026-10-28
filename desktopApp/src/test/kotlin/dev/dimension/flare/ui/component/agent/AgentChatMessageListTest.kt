package dev.dimension.flare.ui.component.agent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import dev.dimension.flare.common.collectPagingState
import dev.dimension.flare.feature.agent.common.AgentChatHistoryMessage
import dev.dimension.flare.feature.agent.presenter.AgentMessagePart
import dev.dimension.flare.ui.theme.FlareTheme
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class AgentChatMessageListTest {
    @get:Rule
    val composeRule = createComposeRule()

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
