package dev.dimension.flare.ui.presenter.home.bluesky

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.common.refreshSuspend
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.bluesky.BlueskyDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BlueskyFeedPresenter(
    private val accountType: AccountType,
    private val uri: String,
) : PresenterBase<BlueskyFeedState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): BlueskyFeedState {
        val scope = rememberCoroutineScope()
        val serviceState = accountServiceProvider(accountType = accountType, repository = accountRepository)
        val timeline =
            serviceState
                .map {
                    require(it is BlueskyDataSource)
                    remember(it, uri) {
                        it.feedTimeline(uri = uri, scope = scope)
                    }.collectAsLazyPagingItems()
                }.toPagingState()
        val info =
            serviceState.map {
                require(it is BlueskyDataSource)
                remember(it, uri) {
                    it.feedInfo(uri = uri)
                }.collectAsState()
            }
        val subscribed =
            serviceState
                .flatMap {
                    require(it is BlueskyDataSource)
                    remember(it) {
                        it.myFeeds
                    }.collectAsState().toUi()
                }.map {
                    it.any { it.id == uri }
                }
        return object : BlueskyFeedState {
            override val info =
                info.flatMap {
                    it.toUi()
                }
            override val timeline = timeline
            override val subscribed = subscribed

            override suspend fun refreshSuspend() {
                info.onSuccess {
                    it.refresh()
                }
                timeline.refreshSuspend()
            }

            override fun subscribe(list: UiList) {
                serviceState.onSuccess {
                    scope.launch {
                        require(it is BlueskyDataSource)
                        it.subscribeFeed(list)
                    }
                }
            }

            override fun unsubscribe(list: UiList) {
                serviceState.onSuccess {
                    scope.launch {
                        require(it is BlueskyDataSource)
                        it.unsubscribeFeed(list)
                    }
                }
            }

            override fun favorite(list: UiList) {
                serviceState.onSuccess {
                    scope.launch {
                        require(it is BlueskyDataSource)
                        it.favouriteFeed(list)
                    }
                }
            }

            override fun unfavorite(list: UiList) {
                serviceState.onSuccess {
                    scope.launch {
                        require(it is BlueskyDataSource)
                        it.favouriteFeed(list)
                    }
                }
            }
        }
    }
}

interface BlueskyFeedState {
    val info: UiState<UiList>
    val timeline: PagingState<UiTimeline>
    val subscribed: UiState<Boolean>

    suspend fun refreshSuspend()

    fun subscribe(list: UiList)

    fun unsubscribe(list: UiList)

    fun favorite(list: UiList)

    fun unfavorite(list: UiList)
}
