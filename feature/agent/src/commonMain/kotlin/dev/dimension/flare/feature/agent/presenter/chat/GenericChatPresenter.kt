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
import dev.dimension.flare.feature.agent.common.AgentConversationAttachment
import dev.dimension.flare.feature.agent.common.AgentConversationAttachmentOwner
import dev.dimension.flare.feature.agent.common.AgentInputRequest
import dev.dimension.flare.feature.agent.common.AgentTrace
import dev.dimension.flare.feature.agent.presenter.AgentMessagePart
import dev.dimension.flare.feature.agent.presenter.parseAgentMessageParts
import dev.dimension.flare.feature.agent.presenter.rememberAgentChatPresenterController
import dev.dimension.flare.feature.agent.presenter.toAgentTextParts
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
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
        public val title: String?
        public val messages: ImmutableList<Message>
        public val input: String
        public val isRunning: Boolean
        public val statusInsightPosts: ImmutableList<UiTimelineV2.Post>
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
        val restoredMessages by remember(conversationId) {
            historyProvider.observeMessages(conversationId)
        }.collectAsState(emptyList())
        val statusInsightPosts by remember(conversationId) {
            historyProvider.observeStatusInsightPosts(conversationId)
        }.collectAsState(emptyList())
        val assistantAttachments by remember(conversationId) {
            historyProvider.observeAttachments(conversationId, AgentConversationAttachmentOwner.Assistant)
        }.collectAsState(emptyList())
        val conversationRecord by remember(conversationId) {
            historyProvider.observeRecord(conversationId)
        }.collectAsState(null)
        val contextFlow =
            remember {
                accountService
                    .allAccountServicesFlow()
                    .map { searchDataSources ->
                        GenericChatContext(searchDataSources)
                    }
            }
        val controller =
            rememberAgentChatPresenterController(
                key = conversationId,
                conversationId = conversationId,
                contextFlow = contextFlow,
                runAgent = { context, userInput, currentConversationId ->
                    genericChatAgentUseCase(
                        userInput = userInput.orEmpty(),
                        searchDataSources = context.searchDataSources,
                        conversationId = currentConversationId,
                    )
                },
                userMessage = Message::User,
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
                    IllegalStateException("Generic chat context is unavailable")
                },
                autoRunOnContext = false,
                initialUserInput = initialMessage,
                initialMessages = restoredMessages.mapNotNull { it.toPresenterMessage(statusInsightPosts, assistantAttachments) },
            )

        return StateImpl(
            response = controller.insight,
            title = conversationRecord?.title,
            messages = controller.messages,
            input = controller.input,
            isRunning = controller.isRunning,
            statusInsightPosts = statusInsightPosts.toImmutableList(),
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
        override val title: String?,
        override val messages: ImmutableList<Message>,
        override val input: String,
        override val isRunning: Boolean,
        override val statusInsightPosts: ImmutableList<UiTimelineV2.Post>,
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

    private data class GenericChatContext(
        val searchDataSources: List<AccountMicroblogDataSource>,
    )

    private fun AgentChatHistoryMessage.toPresenterMessage(
        statusInsightPosts: List<UiTimelineV2.Post>,
        assistantAttachments: List<AgentConversationAttachment>,
    ): Message? =
        when (role) {
            AgentChatHistoryMessage.Role.User -> {
                val displayText = text.statusInsightDisplayText(statusInsightPosts) ?: return null
                Message.User(displayText)
            }

            AgentChatHistoryMessage.Role.Assistant -> {
                val attachments =
                    statusInsightPosts.map { AgentConversationAttachment.Post(it) } + assistantAttachments
                Message.Assistant(
                    text = text,
                    parts = parseAgentMessageParts(text, attachments),
                    inputRequest = inputRequest,
                    inputRequestSelected = inputRequestSelected,
                    inputRequestSelectedOptionId = inputRequestSelectedOptionId,
                )
            }

            AgentChatHistoryMessage.Role.System -> {
                null
            }
        }

    private fun String.statusInsightDisplayText(statusInsightPosts: List<UiTimelineV2.Post>): String? {
        if (statusInsightPosts.isEmpty() || !conversationId.startsWith(STATUS_INSIGHT_CONVERSATION_PREFIX)) {
            return this
        }
        val latestQuestion =
            substringAfter("Latest user question:\n", missingDelimiterValue = "")
                .substringBefore("\n\nCurrent post snapshot:")
                .trim()
                .takeIf { it.isNotBlank() }
        if (latestQuestion != null) {
            return latestQuestion
        }
        if (isStatusInsightSourcePrompt()) {
            return null
        }
        return this
    }

    private fun String.isStatusInsightSourcePrompt(): Boolean =
        contains("Analyze this social post for the user.") ||
            contains("Current post snapshot:") ||
            contains("\nPost:\nplatform:")

    private companion object {
        const val STATUS_INSIGHT_CONVERSATION_PREFIX = "status-insight:"
    }
}
