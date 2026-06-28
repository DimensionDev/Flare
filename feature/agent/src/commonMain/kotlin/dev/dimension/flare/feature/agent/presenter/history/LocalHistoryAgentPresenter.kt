package dev.dimension.flare.feature.agent.presenter.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.data.repository.AccountMicroblogDataSource
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.feature.agent.common.AgentChatHistoryMessage
import dev.dimension.flare.feature.agent.common.AgentChatHistoryProvider
import dev.dimension.flare.feature.agent.common.AgentChatRoom
import dev.dimension.flare.feature.agent.common.AgentInputRequest
import dev.dimension.flare.feature.agent.localhistory.LocalHistoryAgentTarget
import dev.dimension.flare.feature.agent.localhistory.LocalHistoryAgentUseCase
import dev.dimension.flare.feature.agent.presenter.rememberAgentChatPresenterController
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

public class LocalHistoryAgentPresenter(
    private val conversationId: String,
    private val query: String? = null,
    private val target: LocalHistoryAgentTarget = LocalHistoryAgentTarget.All,
) : PresenterBase<LocalHistoryAgentPresenter.State>() {
    private val accountService: AccountService by koinInject()
    private val localHistoryAgentUseCase: LocalHistoryAgentUseCase by koinInject()
    private val historyProvider: AgentChatHistoryProvider by koinInject()

    @Immutable
    public interface State {
        public val room: AgentChatRoom
        public val messages: PagingState<AgentChatHistoryMessage>
        public val input: String
        public val canSend: Boolean

        public fun setInput(value: String)

        public fun sendMessage()

        public fun selectInputRequestOption(option: AgentInputRequest.Option)
    }

    @Composable
    override fun body(): State {
        val normalizedQuery = query?.trim()?.takeIf { it.isNotBlank() }
        val key =
            remember(conversationId, normalizedQuery, target) {
                "local_history_agent:$conversationId:${normalizedQuery.orEmpty()}:$target"
            }
        val room by remember(conversationId) {
            historyProvider.observeRoom(conversationId)
        }.collectAsState(null)
        val currentRoom = room ?: AgentChatRoom.empty(conversationId)
        val contextFlow =
            remember {
                accountService
                    .allAccountServicesFlow()
                    .map { searchDataSources ->
                        LocalHistoryAgentContext(searchDataSources)
                    }.distinctUntilChanged()
            }
        val controller =
            rememberAgentChatPresenterController(
                key = key,
                conversationId = conversationId,
                room = currentRoom,
                contextFlow = contextFlow,
                runAgent = { context, userInput, currentConversationId ->
                    localHistoryAgentUseCase(
                        query = normalizedQuery,
                        target = target,
                        searchDataSources = context.searchDataSources,
                        userInput = userInput,
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
                onRoomRuntimeStateChanged = { isRunning ->
                    historyProvider.updateRoomState(
                        conversationId = conversationId,
                        isRunning = isRunning,
                        updateErrorMessage = false,
                    )
                },
                onRoomStateChanged = { errorMessage ->
                    historyProvider.updateRoomState(
                        conversationId = conversationId,
                        errorMessage = errorMessage,
                    )
                },
                missingContextError = {
                    IllegalStateException("Local history agent context is unavailable")
                },
                autoRunOnContext = normalizedQuery != null,
            )

        return StateImpl(
            room = controller.room,
            messages = controller.messages,
            input = controller.input,
            canSend = controller.canSend,
            onSetInput = controller::setInput,
            onSendMessage = controller::sendMessage,
            onSelectInputRequestOption = controller::selectInputRequestOption,
        )
    }

    @Immutable
    private data class StateImpl(
        override val room: AgentChatRoom,
        override val messages: PagingState<AgentChatHistoryMessage>,
        override val input: String,
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

    private data class LocalHistoryAgentContext(
        val searchDataSources: List<AccountMicroblogDataSource>,
    )
}
