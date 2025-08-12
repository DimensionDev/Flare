package dev.dimension.flare.ui.presenter.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.dimension.flare.common.BaseTimelineLoader
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimeline
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class StatusContextPresenter(
    private val accountType: AccountType,
    private val statusKey: MicroBlogKey,
) : PresenterBase<TimelineState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val timelinePresenter by lazy {
        object : TimelinePresenter() {
            private val currentStatusCreatedAtFlow by lazy {
                accountServiceFlow(
                    accountType = accountType,
                    repository = accountRepository,
                ).flatMapLatest { service ->
                    service.status(statusKey).toUi()
                }.mapNotNull { it.takeSuccess() }
                    .mapNotNull {
                        if (it.content is UiTimeline.ItemContent.Status) {
                            it.content.createdAt
                        } else {
                            null
                        }
                    }.distinctUntilChanged()
            }

            override val loader: Flow<BaseTimelineLoader> by lazy {
                accountServiceFlow(
                    accountType = accountType,
                    repository = accountRepository,
                ).map { service ->
                    service.context(statusKey)
                }.combine(currentStatusCreatedAtFlow) { loader, _ ->
                    loader
                }
            }

            override suspend fun transform(data: UiTimeline): UiTimeline {
                val currentCreatedAt = currentStatusCreatedAtFlow.firstOrNull()
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
    override fun body(): TimelineState {
        val listState = timelinePresenter.body()
        remember {
            LogStatusHistoryPresenter(
                accountType = accountType,
                statusKey = statusKey,
            )
        }.body()
        return listState
    }
}
