package dev.dimension.flare.ui.presenter.home.bluesky

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.refreshSuspend
import dev.dimension.flare.data.datasource.bluesky.BlueskyFeedDataSource
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.account.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.flattenUiState
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.mapNotNull
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.home.TimelinePresenter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class BlueskyFeedPresenter(
    private val accountType: AccountType,
    private val uri: String,
) : PresenterBase<BlueskyFeedState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    private val timelinePresenter by lazy {
        createBlueskyFeedTimeline(accountType, uri, accountRepository)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val infoFlow by lazy {
        accountServiceFlow(accountType, accountRepository)
            .flatMapLatest {
                require(it is BlueskyFeedDataSource)
                it.feedHandler.listInfo(uri).toUi()
            }.map {
                it.mapNotNull { it }
            }
    }

    @Composable
    override fun body(): BlueskyFeedState {
        val scope = rememberCoroutineScope()
        val serviceState = accountServiceProvider(accountType = accountType, repository = accountRepository)
        val timeline = timelinePresenter.body().listState
        val info by infoFlow.flattenUiState()
        val subscribed =
            serviceState
                .flatMap {
                    require(it is BlueskyFeedDataSource)
                    remember(it) {
                        it.feedHandler.cacheData
                    }.collectAsUiState().value
                }.map {
                    it.any { it.id == uri }
                }
        return object : BlueskyFeedState {
            override val info = info
            override val timeline = timeline
            override val subscribed = subscribed

            override suspend fun refreshSuspend() {
//                info.onSuccess {
//                    it.refresh()
//                }
                timeline.refreshSuspend()
            }

            override fun subscribe(list: UiList.Feed) {
                serviceState.onSuccess {
                    scope.launch {
                        require(it is BlueskyFeedDataSource)
                        it.subscribeFeed(list)
                    }
                }
            }

            override fun unsubscribe(list: UiList.Feed) {
                serviceState.onSuccess {
                    scope.launch {
                        require(it is BlueskyFeedDataSource)
                        it.unsubscribeFeed(list)
                    }
                }
            }

            override fun favorite(list: UiList.Feed) {
                serviceState.onSuccess {
                    scope.launch {
                        require(it is BlueskyFeedDataSource)
                        it.favouriteFeed(list)
                    }
                }
            }

            override fun unfavorite(list: UiList.Feed) {
                serviceState.onSuccess {
                    scope.launch {
                        require(it is BlueskyFeedDataSource)
                        it.favouriteFeed(list)
                    }
                }
            }
        }
    }
}

public fun createBlueskyFeedTimeline(
    accountType: AccountType,
    uri: String,
): TimelinePresenter =
    createBlueskyFeedTimeline(
        accountType = accountType,
        uri = uri,
        accountRepository = null,
    )

private fun createBlueskyFeedTimeline(
    accountType: AccountType,
    uri: String,
    accountRepository: AccountRepository?,
): TimelinePresenter =
    object : TimelinePresenter() {
        private val injectedAccountRepository: AccountRepository by inject()

        override val loader: Flow<RemoteLoader<UiTimelineV2>> by lazy {
            accountServiceFlow(accountType, accountRepository ?: injectedAccountRepository)
                .map {
                    require(it is BlueskyFeedDataSource)
                    it.feedTimelineLoader(uri)
                }
        }
    }

@Immutable
public interface BlueskyFeedState {
    public val info: UiState<UiList.Feed>
    public val timeline: PagingState<UiTimelineV2>
    public val subscribed: UiState<Boolean>

    public suspend fun refreshSuspend()

    public fun subscribe(list: UiList.Feed)

    public fun unsubscribe(list: UiList.Feed)

    public fun favorite(list: UiList.Feed)

    public fun unfavorite(list: UiList.Feed)
}
