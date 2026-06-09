package dev.dimension.flare.feature.agent.presenter.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import dev.dimension.flare.data.repository.AccountMicroblogDataSource
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.feature.agent.common.AgentChatHistoryProvider
import dev.dimension.flare.feature.agent.common.AgentInputRequest
import dev.dimension.flare.feature.agent.common.AgentLocalizedText
import dev.dimension.flare.feature.agent.common.AgentTrace
import dev.dimension.flare.feature.agent.localhistory.LocalHistoryAgentTarget
import dev.dimension.flare.feature.agent.localhistory.LocalHistoryAgentUseCase
import dev.dimension.flare.feature.agent.presenter.AgentMessagePart
import dev.dimension.flare.feature.agent.presenter.parseAgentMessageParts
import dev.dimension.flare.feature.agent.presenter.rememberAgentChatPresenterController
import dev.dimension.flare.feature.agent.presenter.toAgentTextParts
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class LocalHistoryAgentPresenter(
    private val conversationId: String,
    private val query: String? = null,
    private val target: LocalHistoryAgentTarget = LocalHistoryAgentTarget.All,
) : PresenterBase<LocalHistoryAgentPresenter.State>(),
    KoinComponent {
    private val accountService: AccountService by inject()
    private val localHistoryAgentUseCase: LocalHistoryAgentUseCase by inject()
    private val historyProvider: AgentChatHistoryProvider by inject()

    @Immutable
    public interface State {
        public val response: UiState<String>
        public val messages: ImmutableList<Message>
        public val input: String
        public val isRunning: Boolean
        public val currentTrace: AgentTrace?
        public val traceHistory: ImmutableList<AgentTrace>
        public val inputRequest: AgentInputRequest?
        public val error: Throwable?
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
        val contextFlow =
            remember {
                accountService
                    .allAccountServicesFlow()
                    .map { searchDataSources ->
                        LocalHistoryAgentContext(searchDataSources)
                    }
            }
        val controller =
            rememberAgentChatPresenterController(
                key = key,
                conversationId = conversationId,
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
                userMessage = { text, localizedText ->
                    Message.User(text = text, localizedText = localizedText)
                },
                assistantMessage = { text, attachments, inputRequest ->
                    Message.Assistant(
                        text = text,
                        parts = parseAgentMessageParts(text, attachments),
                        inputRequest = inputRequest,
                    )
                },
                isAssistantMessage = { it is Message.Assistant },
                messageInputRequest = Message::inputRequest,
                messageInputRequestSelected = Message::inputRequestSelected,
                markMessageInputRequestSelected = { message, requestId, optionId ->
                    when (message) {
                        is Message.Assistant -> {
                            if (message.inputRequest?.requestId == requestId) {
                                message.copy(
                                    inputRequestSelected = true,
                                    inputRequestSelectedOptionId = optionId,
                                )
                            } else {
                                message
                            }
                        }

                        is Message.User -> {
                            message
                        }
                    }
                },
                messageText = Message::text,
                onInputRequestSelected = { requestId, optionId ->
                    historyProvider.markInputRequestSelected(conversationId, requestId, optionId)
                },
                missingContextError = {
                    IllegalStateException("Local history agent context is unavailable")
                },
            )

        return StateImpl(
            response = controller.insight,
            messages = controller.messages,
            input = controller.input,
            isRunning = controller.isRunning,
            currentTrace = controller.currentTrace,
            traceHistory = controller.traceHistory,
            inputRequest = controller.inputRequest,
            error = controller.error,
            canSend = controller.canSend,
            onSetInput = controller::setInput,
            onSendMessage = controller::sendMessage,
            onSelectInputRequestOption = controller::selectInputRequestOption,
        )
    }

    @Immutable
    public sealed interface Message {
        public val text: String
        public val localizedText: AgentLocalizedText?
            get() = null
        public val parts: ImmutableList<AgentMessagePart>
        public val inputRequest: AgentInputRequest?
            get() = null
        public val inputRequestSelected: Boolean
            get() = false
        public val inputRequestSelectedOptionId: String?
            get() = null

        @Immutable
        public data class User(
            override val text: String,
            override val localizedText: AgentLocalizedText? = null,
            override val parts: ImmutableList<AgentMessagePart> = text.toAgentTextParts(),
        ) : Message

        @Immutable
        public data class Assistant(
            override val text: String,
            override val parts: ImmutableList<AgentMessagePart>,
            override val inputRequest: AgentInputRequest? = null,
            override val inputRequestSelected: Boolean = false,
            override val inputRequestSelectedOptionId: String? = null,
        ) : Message
    }

    @Immutable
    private data class StateImpl(
        override val response: UiState<String>,
        override val messages: ImmutableList<Message>,
        override val input: String,
        override val isRunning: Boolean,
        override val currentTrace: AgentTrace?,
        override val traceHistory: ImmutableList<AgentTrace>,
        override val inputRequest: AgentInputRequest?,
        override val error: Throwable?,
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
