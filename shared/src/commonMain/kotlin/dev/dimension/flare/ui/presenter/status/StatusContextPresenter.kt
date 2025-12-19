package dev.dimension.flare.ui.presenter.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.common.BaseTimelineLoader
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.home.TimelinePresenter
import dev.dimension.flare.ui.presenter.home.TimelineState
import dev.dimension.flare.ui.render.compareTo
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@OptIn(ExperimentalCoroutinesApi::class)
public class StatusContextPresenter(
    private val accountType: AccountType,
    private val statusKey: MicroBlogKey,
) : PresenterBase<StatusContextPresenter.State>(),
    KoinComponent {
    public interface State : TimelineState {
        public val current: UiState<UiTimeline.ItemContent.Status>
    }

    private val accountRepository: AccountRepository by inject()

    private val currentStatusFlow by lazy {
        accountServiceFlow(
            accountType = accountType,
            repository = accountRepository,
        ).flatMapLatest { service ->
            service.status(statusKey).toUi()
        }.mapNotNull { it.takeSuccess() }
            .mapNotNull {
                it.content as? UiTimeline.ItemContent.Status
            }.distinctUntilChanged()
    }

    private val timelinePresenter by lazy {
        object : TimelinePresenter() {
            override val loader: Flow<BaseTimelineLoader> by lazy {
                currentStatusFlow
                    .map {
                        it.statusKey
                    }.distinctUntilChanged()
                    .flatMapLatest { statusKey ->
                        accountServiceFlow(
                            accountType = accountType,
                            repository = accountRepository,
                        ).map { service ->
                            service.context(statusKey)
                        }
                    }
            }

            override suspend fun transform(data: UiTimeline): UiTimeline {
                val currentCreatedAt = currentStatusFlow.firstOrNull()?.createdAt
                return data.copy(
                    content =
                        when (val content = data.content) {
                            is UiTimeline.ItemContent.Status -> {
                                if (currentCreatedAt != null && content.createdAt <= currentCreatedAt) {
                                    content.copy(
                                        parents = persistentListOf(),
                                    )
                                } else if (currentCreatedAt != null) {
                                    content.copy(
                                        parents =
                                            content.parents
                                                .filter {
                                                    it.createdAt > currentCreatedAt
                                                }.toPersistentList(),
                                    )
                                } else {
                                    content
                                }
                            }

                            else -> content
                        },
                )
            }
        }
    }

    @Composable
    override fun body(): State {
        val current by currentStatusFlow.collectAsUiState()
        val listState = timelinePresenter.body()
        current.onSuccess {
            remember {
                LogStatusHistoryPresenter(
                    accountType = accountType,
                    statusKey = it.statusKey,
                )
            }.body()
        }
        return object : State, TimelineState by listState {
            override val current = current
        }
    }
}
