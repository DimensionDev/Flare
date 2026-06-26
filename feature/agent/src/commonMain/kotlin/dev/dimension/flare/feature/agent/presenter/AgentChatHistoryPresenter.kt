package dev.dimension.flare.feature.agent.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.dimension.flare.feature.agent.common.AgentChatHistoryProvider
import dev.dimension.flare.feature.agent.common.AgentChatRoom
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import dev.dimension.flare.di.koinInject

public class AgentChatHistoryPresenter :
    PresenterBase<AgentChatHistoryPresenter.State>() {
    private val historyProvider: AgentChatHistoryProvider by koinInject()

    @Immutable
    public interface State {
        public val rooms: ImmutableList<AgentChatRoom>
    }

    @Composable
    override fun body(): State {
        val rooms by historyProvider.observeRooms().collectAsState(emptyList())
        return StateImpl(
            rooms = rooms.toImmutableList(),
        )
    }

    private data class StateImpl(
        override val rooms: ImmutableList<AgentChatRoom>,
    ) : State
}
