package dev.dimension.flare.feature.agent.presenter.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.repository.AccountMicroblogDataSource
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.feature.agent.common.AgentInputRequest
import dev.dimension.flare.feature.agent.common.AgentLocalizedText
import dev.dimension.flare.feature.agent.common.AgentTrace
import dev.dimension.flare.feature.agent.presenter.AgentMessagePart
import dev.dimension.flare.feature.agent.presenter.parseAgentMessageParts
import dev.dimension.flare.feature.agent.presenter.rememberAgentChatPresenterController
import dev.dimension.flare.feature.agent.presenter.toAgentTextParts
import dev.dimension.flare.feature.agent.profile.ProfileInsightAgentUseCase
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.combine
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class ProfileInsightPresenter(
    private val accountType: AccountType,
    private val userKey: MicroBlogKey,
) : PresenterBase<ProfileInsightPresenter.State>(),
    KoinComponent {
    private val accountService: AccountService by inject()
    private val profileInsightAgentUseCase: ProfileInsightAgentUseCase by inject()

    @Immutable
    public interface State {
        public val insight: UiState<String>
        public val messages: ImmutableList<Message>
        public val input: String
        public val isRunning: Boolean
        public val profile: UiProfile?
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
        val key = "$accountType:$userKey"
        val conversationId =
            remember(accountType, userKey) {
                "profile-insight:$accountType:$userKey"
            }
        val contextFlow =
            remember(accountType) {
                accountService
                    .accountServiceFlow(accountType)
                    .combine(accountService.allAccountServicesFlow()) { service, availableSearchDataSources ->
                        (service as? UserDataSource)?.let { userDataSource ->
                            ProfileInsightContext(
                                userDataSource = userDataSource,
                                searchDataSources = availableSearchDataSources,
                            )
                        }
                    }
            }
        val controller =
            rememberAgentChatPresenterController(
                key = key,
                conversationId = conversationId,
                contextFlow = contextFlow,
                runAgent = { context, userInput, currentConversationId ->
                    profileInsightAgentUseCase(
                        userDataSource = context.userDataSource,
                        userKey = userKey,
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
                missingContextError = {
                    IllegalStateException("Current account does not support user data source")
                },
            )

        return StateImpl(
            messages = controller.messages,
            input = controller.input,
            isRunning = controller.isRunning,
            profile = controller.content,
            currentTrace = controller.currentTrace,
            traceHistory = controller.traceHistory,
            inputRequest = controller.inputRequest,
            error = controller.error,
            insight = controller.insight,
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
        override val messages: ImmutableList<Message>,
        override val input: String,
        override val isRunning: Boolean,
        override val profile: UiProfile?,
        override val currentTrace: AgentTrace?,
        override val traceHistory: ImmutableList<AgentTrace>,
        override val inputRequest: AgentInputRequest?,
        override val error: Throwable?,
        override val insight: UiState<String>,
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

    private data class ProfileInsightContext(
        val userDataSource: UserDataSource,
        val searchDataSources: List<AccountMicroblogDataSource>,
    )
}
