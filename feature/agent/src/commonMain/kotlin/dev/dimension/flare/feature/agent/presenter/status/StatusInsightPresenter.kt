package dev.dimension.flare.feature.agent.presenter.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.data.repository.AccountMicroblogDataSource
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.feature.agent.common.AgentChatHistoryMessage
import dev.dimension.flare.feature.agent.common.AgentChatHistoryProvider
import dev.dimension.flare.feature.agent.common.AgentChatRoom
import dev.dimension.flare.feature.agent.common.AgentInputRequest
import dev.dimension.flare.feature.agent.presenter.AgentMessagePart
import dev.dimension.flare.feature.agent.presenter.rememberAgentChatPresenterController
import dev.dimension.flare.feature.agent.status.StatusInsightAgentUseCase
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

public class StatusInsightPresenter(
    private val accountType: AccountType,
    private val statusKey: MicroBlogKey,
) : PresenterBase<StatusInsightPresenter.State>() {
    private val accountService: AccountService by koinInject()
    private val statusInsightAgentUseCase: StatusInsightAgentUseCase by koinInject()
    private val historyProvider: AgentChatHistoryProvider by koinInject()

    @Immutable
    public interface State {
        public val room: AgentChatRoom
        public val messages: PagingState<AgentChatHistoryMessage>
        public val input: String
        public val post: UiTimelineV2.Post?
        public val canSend: Boolean

        public fun setInput(value: String)

        public fun sendMessage()

        public fun selectInputRequestOption(option: AgentInputRequest.Option)
    }

    @Composable
    override fun body(): State {
        val key = "$accountType:$statusKey"
        val conversationId =
            remember(accountType, statusKey) {
                "status-insight:$accountType:$statusKey"
            }
        val room by remember(conversationId) {
            historyProvider.observeRoom(conversationId)
        }.collectAsState(null)
        val currentRoom = room ?: AgentChatRoom.empty(conversationId)
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
                    }.distinctUntilChanged()
            }
        val controller =
            rememberAgentChatPresenterController(
                key = key,
                conversationId = conversationId,
                room = currentRoom,
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
                onUserMessageSubmitted = { text ->
                    historyProvider.storeUserUiMessage(conversationId, text)
                },
                onInputRequestOptionSubmitted = { option ->
                    historyProvider.storeUserUiInputRequestOption(conversationId, option)
                },
                onInputRequestSelected = { requestId, optionId ->
                    historyProvider.markInputRequestSelected(conversationId, requestId, optionId)
                },
                onInitialContentLoaded = { post ->
                    historyProvider.storeUserUiMessage(
                        conversationId = conversationId,
                        displayText = post.insightUserMessageTitle(),
                        parts = listOf(AgentMessagePart.PostCard(post)),
                    )
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
                    IllegalStateException("Current account does not support post data source")
                },
            )

        return StateImpl(
            room = controller.room,
            messages = controller.messages,
            input = controller.input,
            post = controller.content,
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
        override val post: UiTimelineV2.Post?,
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

    private data class StatusInsightContext(
        val postDataSource: PostDataSource,
        val searchDataSources: List<AccountMicroblogDataSource>,
    )
}

private fun UiTimelineV2.Post.insightUserMessageTitle(): String =
    content.original.raw
        .trim()
        .ifBlank {
            contentWarning
                ?.original
                ?.raw
                .orEmpty()
                .trim()
        }.ifBlank { card?.title.orEmpty().trim() }
        .ifBlank {
            user
                ?.name
                ?.raw
                .orEmpty()
                .trim()
        }.ifBlank {
            user
                ?.handle
                ?.raw
                .orEmpty()
                .trim()
        }.ifBlank { statusKey.toString() }
