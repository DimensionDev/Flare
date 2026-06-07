package dev.dimension.flare.feature.agent.presenter.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.data.repository.AccountMicroblogDataSource
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.feature.agent.common.AgentTrace
import dev.dimension.flare.feature.agent.presenter.rememberAgentChatPresenterController
import dev.dimension.flare.feature.agent.status.StatusInsightAgentUseCase
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.combine
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class StatusInsightPresenter(
    private val accountType: AccountType,
    private val statusKey: MicroBlogKey,
) : PresenterBase<StatusInsightPresenter.State>(),
    KoinComponent {
    private val accountService: AccountService by inject()
    private val statusInsightAgentUseCase: StatusInsightAgentUseCase by inject()

    @Immutable
    public interface State {
        public val insight: UiState<String>
        public val messages: ImmutableList<Message>
        public val input: String
        public val isRunning: Boolean
        public val post: UiTimelineV2.Post?
        public val currentTrace: AgentTrace?
        public val error: Throwable?
        public val canSend: Boolean

        public fun setInput(value: String)

        public fun sendMessage()
    }

    @Composable
    override fun body(): State {
        val key = "$accountType:$statusKey"
        val conversationId =
            remember(accountType, statusKey) {
                "status-insight:$accountType:$statusKey"
            }
        val contextFlow =
            remember(accountType) {
                accountService
                    .accountServiceFlow(accountType)
                    .combine(accountService.allAccountServicesFlow()) { service, availableSearchDataSources ->
                        (service as? PostDataSource)?.let { postDataSource ->
                            StatusInsightContext(
                                postDataSource = postDataSource,
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
                    statusInsightAgentUseCase(
                        postDataSource = context.postDataSource,
                        statusKey = statusKey,
                        searchDataSources = context.searchDataSources,
                        userInput = userInput,
                        conversationId = currentConversationId,
                    )
                },
                userMessage = Message::User,
                assistantMessage = Message::Assistant,
                isAssistantMessage = { it is Message.Assistant },
                messageText = Message::text,
                missingContextError = {
                    IllegalStateException("Current account does not support post data source")
                },
            )

        return StateImpl(
            messages = controller.messages,
            input = controller.input,
            isRunning = controller.isRunning,
            post = controller.content,
            currentTrace = controller.currentTrace,
            error = controller.error,
            insight = controller.insight,
            canSend = controller.canSend,
            onSetInput = controller::setInput,
            onSendMessage = controller::sendMessage,
        )
    }

    @Immutable
    public sealed interface Message {
        public val text: String

        @Immutable
        public data class User(
            override val text: String,
        ) : Message

        @Immutable
        public data class Assistant(
            override val text: String,
        ) : Message
    }

    @Immutable
    private data class StateImpl(
        override val messages: ImmutableList<Message>,
        override val input: String,
        override val isRunning: Boolean,
        override val post: UiTimelineV2.Post?,
        override val currentTrace: AgentTrace?,
        override val error: Throwable?,
        override val insight: UiState<String>,
        override val canSend: Boolean,
        private val onSetInput: (String) -> Unit,
        private val onSendMessage: () -> Unit,
    ) : State {
        override fun setInput(value: String) {
            onSetInput.invoke(value)
        }

        override fun sendMessage() {
            onSendMessage.invoke()
        }
    }

    private data class StatusInsightContext(
        val postDataSource: PostDataSource,
        val searchDataSources: List<AccountMicroblogDataSource>,
    )
}
