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

class BlueskyFeedPresenter(
    private val accountType: AccountType,
    private val uri: String,
) : PresenterBase<BlueskyFeedState>() {
    @Composable
    override fun body(): BlueskyFeedState {
        val scope = rememberCoroutineScope()
        val serviceState = accountServiceProvider(accountType = accountType)
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
        return object : BlueskyFeedState {
            override val info =
                info.flatMap {
                    it.toUi()
                }
            override val timeline = timeline

            override suspend fun refreshSuspend() {
                info.onSuccess {
                    it.refresh()
                }
                timeline.refreshSuspend()
            }
        }
    }
}

interface BlueskyFeedState {
    val info: UiState<UiList>
    val timeline: PagingState<UiTimeline>

    suspend fun refreshSuspend()
}
