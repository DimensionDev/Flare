package dev.dimension.flare.feature.agent.presenter.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.data.repository.AccountMicroblogDataSource
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.data.repository.AuthoredPlugin
import dev.dimension.flare.data.repository.PluginAuthoring
import dev.dimension.flare.data.repository.authoredPluginId
import dev.dimension.flare.di.koinGet
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.feature.agent.chat.GenericChatAgentUseCase
import dev.dimension.flare.feature.agent.common.AgentChatHistoryMessage
import dev.dimension.flare.feature.agent.common.AgentChatHistoryProvider
import dev.dimension.flare.feature.agent.common.AgentChatRoom
import dev.dimension.flare.feature.agent.common.AgentInputRequest
import dev.dimension.flare.feature.agent.presenter.AgentChatRunRegistry
import dev.dimension.flare.feature.agent.presenter.rememberAgentChatPresenterController
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

public class GenericChatPresenter(
    private val conversationId: String = "generic-chat",
    private val initialMessage: String? = null,
) : PresenterBase<GenericChatPresenter.State>() {
    private val accountService: AccountService by koinInject()
    private val genericChatAgentUseCase: GenericChatAgentUseCase by koinInject()
    private val historyProvider: AgentChatHistoryProvider by koinInject()
    private val pluginId = authoredPluginId(conversationId)
    private val authoring by lazy { pluginId?.let { koinGet<PluginAuthoring>() } }

    @Immutable
    public interface State {
        public val room: AgentChatRoom
        public val messages: PagingState<AgentChatHistoryMessage>
        public val input: String
        public val canSend: Boolean
        public val plugin: AuthoredPlugin?

        public fun approvePlugin()

        public fun stopPlugin()

        public fun setPluginEnabled(enabled: Boolean)

        public fun setInput(value: String)

        public fun sendMessage()

        public fun selectInputRequestOption(option: AgentInputRequest.Option)
    }

    @Composable
    override fun body(): State {
        val scope = rememberCoroutineScope()
        val plugins by remember { authoring?.plugins ?: flowOf(emptyList()) }.collectAsState(emptyList())
        val plugin = plugins.firstOrNull { it.id == pluginId }

        fun pluginAction(block: suspend (PluginAuthoring, String) -> Unit) {
            val service = authoring ?: return
            val id = pluginId ?: return
            scope.launch {
                try {
                    block(service, id)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    historyProvider.updateRoomState(conversationId, errorMessage = error.message?.take(512))
                }
            }
        }
        val room by remember(conversationId) {
            historyProvider.observeRoom(conversationId)
        }.collectAsState(null)
        val currentRoom = room ?: AgentChatRoom.empty(conversationId)
        val contextFlow =
            remember {
                if (pluginId != null) {
                    flowOf(GenericChatContext(emptyList()))
                } else {
                    accountService
                        .allAccountServicesFlow()
                        .map { searchDataSources ->
                            GenericChatContext(searchDataSources)
                        }.distinctUntilChanged()
                }
            }
        val controller =
            rememberAgentChatPresenterController(
                key = conversationId,
                conversationId = conversationId,
                room = currentRoom,
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
                    IllegalStateException("Generic chat context is unavailable")
                },
                autoRunOnContext = false,
                initialUserInput = initialMessage,
            )

        return StateImpl(
            room = controller.room,
            messages = controller.messages,
            input = controller.input,
            canSend = controller.canSend && (pluginId == null || plugin != null),
            plugin = plugin,
            onApprovePlugin = { plugin?.let { current -> pluginAction { service, id -> service.approve(id, current.revision) } } },
            onStopPlugin = {
                AgentChatRunRegistry.cancel(conversationId)
                pluginAction { service, id -> service.stop(id) }
            },
            onSetPluginEnabled = { enabled -> pluginAction { service, id -> service.setEnabled(id, enabled) } },
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
        override val plugin: AuthoredPlugin?,
        private val onApprovePlugin: () -> Unit,
        private val onStopPlugin: () -> Unit,
        private val onSetPluginEnabled: (Boolean) -> Unit,
        private val onSetInput: (String) -> Unit,
        private val onSendMessage: () -> Unit,
        private val onSelectInputRequestOption: (AgentInputRequest.Option) -> Unit,
    ) : State {
        override fun approvePlugin() = onApprovePlugin()

        override fun stopPlugin() = onStopPlugin()

        override fun setPluginEnabled(enabled: Boolean) = onSetPluginEnabled(enabled)

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
