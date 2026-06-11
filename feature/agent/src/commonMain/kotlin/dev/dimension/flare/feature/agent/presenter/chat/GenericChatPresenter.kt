package dev.dimension.flare.feature.agent.presenter.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.data.repository.AccountMicroblogDataSource
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.feature.agent.chat.GenericChatAgentUseCase
import dev.dimension.flare.feature.agent.common.AgentChatHistoryMessage
import dev.dimension.flare.feature.agent.common.AgentChatHistoryProvider
import dev.dimension.flare.feature.agent.common.AgentChatRoom
import dev.dimension.flare.feature.agent.common.AgentInputRequest
import dev.dimension.flare.feature.agent.presenter.rememberAgentChatPresenterController
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class GenericChatPresenter(
    private val conversationId: String = "generic-chat",
    private val initialMessage: String? = null,
) : PresenterBase<GenericChatPresenter.State>(),
    KoinComponent {
    private val accountService: AccountService by inject()
    private val genericChatAgentUseCase: GenericChatAgentUseCase by inject()
    private val historyProvider: AgentChatHistoryProvider by inject()

    @Immutable
    public interface State {
        public val response: UiState<String>
        public val room: AgentChatRoom
        public val messages: ImmutableList<AgentChatHistoryMessage>
        public val input: String
        public val statusInsightPosts: ImmutableList<UiTimelineV2.Post>
        public val canSend: Boolean

        public fun setInput(value: String)

        public fun sendMessage()

        public fun selectInputRequestOption(option: AgentInputRequest.Option)
    }

    @Composable
    override fun body(): State {
        val restoredMessages by remember(conversationId) {
            historyProvider.observeMessages(conversationId)
        }.collectAsState(emptyList())
        val statusInsightPosts by remember(conversationId) {
            historyProvider.observeStatusInsightPosts(conversationId)
        }.collectAsState(emptyList())
        val room by remember(conversationId) {
            historyProvider.observeRoom(conversationId)
        }.collectAsState(null)
        val currentRoom = room ?: AgentChatRoom.empty(conversationId)
        val contextFlow =
            remember {
                accountService
                    .allAccountServicesFlow()
                    .map { searchDataSources ->
                        GenericChatContext(searchDataSources)
                    }.distinctUntilChanged()
            }
        val controller =
            rememberAgentChatPresenterController(
                key = conversationId,
                conversationId = conversationId,
                room = currentRoom,
                messageRecords = restoredMessages,
                contextFlow = contextFlow,
                runAgent = { context, userInput, currentConversationId ->
                    genericChatAgentUseCase(
                        userInput = userInput.orEmpty(),
                        searchDataSources = context.searchDataSources,
                        conversationId = currentConversationId,
                    )
                },
                onUserMessageSubmitted = { text ->
                    historyProvider.storeUserUiMessage(conversationId, text)
                },
                onInputRequestOptionSubmitted = { option ->
                    historyProvider.storeUserUiInputRequestOption(conversationId, option)
                },
                onInputRequestSelected = { requestId, optionId ->
                    historyProvider.markInputRequestSelected(conversationId, requestId, optionId)
                },
                onAgentRunCompleted = {
                    historyProvider.generateTitleIfNeeded(conversationId)
                },
                onRoomStateChanged = { isRunning, currentTrace, traceHistory, errorMessage ->
                    historyProvider.updateRoomState(
                        conversationId = conversationId,
                        isRunning = isRunning,
                        currentTrace = currentTrace,
                        traceHistory = traceHistory,
                        errorMessage = errorMessage,
                    )
                },
                missingContextError = {
                    IllegalStateException("Generic chat context is unavailable")
                },
                autoRunOnContext = false,
                initialUserInput = initialMessage,
            )

        return StateImpl(
            response = controller.insight,
            room = controller.room,
            messages = controller.messages,
            input = controller.input,
            statusInsightPosts = statusInsightPosts.toImmutableList(),
            canSend = controller.canSend,
            onSetInput = controller::setInput,
            onSendMessage = controller::sendMessage,
            onSelectInputRequestOption = controller::selectInputRequestOption,
        )
    }

    @Immutable
    private data class StateImpl(
        override val response: UiState<String>,
        override val room: AgentChatRoom,
        override val messages: ImmutableList<AgentChatHistoryMessage>,
        override val input: String,
        override val statusInsightPosts: ImmutableList<UiTimelineV2.Post>,
        override val canSend: Boolean,
        private val onSetInput: (String) -> Unit,
        private val onSendMessage: () -> Unit,
        private val onSelectInputRequestOption: (AgentInputRequest.Option) -> Unit,
    ) : State {
        override fun setInput(value: String) {
            onSetInput.invoke(value)
        }

        override fun sendMessage() {
            onSendMessage.invoke()
        }

        override fun selectInputRequestOption(option: AgentInputRequest.Option) {
            onSelectInputRequestOption.invoke(option)
        }
    }

    private data class GenericChatContext(
        val searchDataSources: List<AccountMicroblogDataSource>,
    )
}
