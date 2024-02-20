package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import dev.dimension.flare.common.LazyPagingItemsProxy
import dev.dimension.flare.common.collectPagingProxy
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase

class HomeTimelinePresenter(
    private val accountKey: MicroBlogKey,
) : PresenterBase<HomeTimelineState>() {
    @Composable
    override fun body(): HomeTimelineState {
        val serviceState = accountServiceProvider(accountKey = accountKey)
        val listState =
            serviceState.map { service ->
                remember(accountKey) {
                    service.homeTimeline()
                }.collectPagingProxy()
            }

        return object : HomeTimelineState {
            override val listState = listState

            override suspend fun refresh() {
                listState.onSuccess {
                    it.refreshSuspend()
                }
            }
        }
    }
}

@Immutable
interface HomeTimelineState {
    val listState: UiState<LazyPagingItemsProxy<UiStatus>>

    suspend fun refresh()
}
