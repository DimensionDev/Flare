package dev.dimension.flare.ui.component.agent

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.data.repository.AuthoredPlugin
import dev.dimension.flare.data.repository.PluginAuthoringPhase
import dev.dimension.flare.feature.agent.common.AgentChatHistoryMessage
import dev.dimension.flare.feature.agent.common.AgentChatRoom
import dev.dimension.flare.feature.agent.common.AgentInputRequest
import dev.dimension.flare.feature.agent.presenter.chat.GenericChatPresenter
import dev.dimension.flare.ui.theme.FlareTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PluginAuthoringCardTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun revalidationCanBeStoppedWithoutAnAgentRun() {
        var currentPlugin by mutableStateOf(
            AuthoredPlugin("test.plugin", "Test plugin", phase = PluginAuthoringPhase.Validating, revision = 1, running = true),
        )
        var stops = 0
        val state =
            object : GenericChatPresenter.State {
                override val room = AgentChatRoom.empty("test")
                override val messages: PagingState<AgentChatHistoryMessage> get() = error("Unused")
                override val input = ""
                override val canSend = false
                override val plugin get() = currentPlugin

                override fun approvePlugin() = Unit

                override fun stopPlugin() {
                    stops++
                    currentPlugin = currentPlugin.copy(phase = PluginAuthoringPhase.Idle)
                }

                override fun setPluginEnabled(enabled: Boolean) = Unit

                override fun setInput(value: String) = Unit

                override fun sendMessage() = Unit

                override fun selectInputRequestOption(option: AgentInputRequest.Option) = Unit
            }
        compose.setContent {
            FlareTheme(isDarkTheme = false) {
                PluginAuthoringCard(state)
            }
        }

        compose.onNodeWithText("Stop generating").assertIsDisplayed().performClick()
        compose.onNodeWithText("Stop generating").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, stops) }
    }
}
