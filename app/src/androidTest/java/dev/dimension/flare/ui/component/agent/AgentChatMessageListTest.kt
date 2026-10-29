package dev.dimension.flare.ui.component.agent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.dimension.flare.common.collectPagingState
import dev.dimension.flare.feature.agent.common.AgentChatHistoryMessage
import dev.dimension.flare.feature.agent.common.AgentFailure
import dev.dimension.flare.feature.agent.presenter.AgentMessagePart
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
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
        composeRule.onNodeWithTag("messages").performScrollToIndex(20)
        composeRule.onNodeWithText("Reply 20").assertIsDisplayed()

        composeRule.runOnIdle { messages.value = listOf(message("New reply")) + messages.value }

        composeRule.onNodeWithText("Reply 20").assertIsDisplayed()
        composeRule.onNodeWithText("New reply").assertIsNotDisplayed()
    }

    @Test
    fun historyRetryResubscribesWithoutStartingAnAgent() {
        var loads = 0
        val history =
            flow {
                loads++
                if (loads == 1) error("Database unavailable")
                emit(listOf(message("Restored reply")))
            }
        composeRule.setContent {
            val state by history.collectPagingState()
            MaterialTheme {
                AgentChatMessageList(messages = state, onInputRequestOptionSelected = {})
            }
        }
        composeRule.onNodeWithText("Retry").performClick()
        composeRule.onNodeWithText("Restored reply").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(2, loads) }
    }

    @Test
    fun unknownErrorIsPlainVisibleTextAndRetryFitsANarrowCard() {
        var retries = 0
        val message = "<b>Provider rejected this model</b>"
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.size(240.dp, 300.dp)) {
                    AgentErrorCard(AgentFailure.fromThrowable(IllegalStateException(message)), onRetry = { retries++ })
                }
            }
        }
        composeRule.onNodeWithText(message).assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, retries) }
    }

    private fun showMessages(): MutableStateFlow<List<AgentChatHistoryMessage>> {
        val messages = MutableStateFlow(List(40) { message("Reply $it") })
        composeRule.setContent {
            val state by messages.collectPagingState()
            MaterialTheme {
                Box(Modifier.size(300.dp, 240.dp)) {
                    AgentChatMessageList(
                        messages = state,
                        onInputRequestOptionSelected = {},
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
