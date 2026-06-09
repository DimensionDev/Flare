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
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class AgentChatHistoryPresenter :
    PresenterBase<AgentChatHistoryPresenter.State>(),
    KoinComponent {
    private val historyProvider: AgentChatHistoryProvider by inject()

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
