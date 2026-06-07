package dev.dimension.flare.feature.agent.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.dimension.flare.feature.agent.common.AgentChatHistoryProvider
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.render.UiDateTime
import dev.dimension.flare.ui.render.toUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Instant

public class AgentChatHistoryPresenter :
    PresenterBase<AgentChatHistoryPresenter.State>(),
    KoinComponent {
    private val historyProvider: AgentChatHistoryProvider by inject()

    @Immutable
    public interface State {
        public val conversations: ImmutableList<Conversation>
    }

    @Immutable
    public data class Conversation(
        val id: String,
        val title: String,
        val updatedAt: UiDateTime,
    )

    @Composable
    override fun body(): State {
        val records by historyProvider.observeRecords().collectAsState(emptyList())
        return StateImpl(
            conversations =
                records
                    .map { record ->
                        Conversation(
                            id = record.conversationId,
                            title = record.title,
                            updatedAt = Instant.fromEpochMilliseconds(record.updatedAt).toUi(),
                        )
                    }.toImmutableList(),
        )
    }

    private data class StateImpl(
        override val conversations: ImmutableList<Conversation>,
    ) : State
}
