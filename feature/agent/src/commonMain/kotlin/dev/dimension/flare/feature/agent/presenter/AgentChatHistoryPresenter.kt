package dev.dimension.flare.feature.agent.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.feature.agent.common.AgentChatHistoryProvider
import dev.dimension.flare.feature.agent.common.AgentChatRoom
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

public class AgentChatHistoryPresenter : PresenterBase<AgentChatHistoryPresenter.State>() {
    private val historyProvider: AgentChatHistoryProvider by koinInject()
    private val scope: CoroutineScope by koinInject()

    @Immutable
    public interface State {
        public val rooms: ImmutableList<AgentChatRoom>

        public fun delete(conversationId: String)
    }

    @Composable
    override fun body(): State {
        val rooms by historyProvider.observeRooms().collectAsState(emptyList())
        return StateImpl(
            rooms = rooms.toImmutableList(),
            onDelete = { conversationId ->
                AgentChatRunRegistry.cancel(conversationId)
                scope.launch {
                    historyProvider.deleteConversation(conversationId)
                }
            },
        )
    }

    private data class StateImpl(
        override val rooms: ImmutableList<AgentChatRoom>,
        private val onDelete: (String) -> Unit,
    ) : State {
        override fun delete(conversationId: String) {
            onDelete(conversationId)
        }
    }
}
