package dev.dimension.flare.ui.presenter.guest

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.paging.Pager
import androidx.paging.PagingConfig
import dev.dimension.flare.common.collectPagingProxy
import dev.dimension.flare.data.datasource.guest.GuestTimelinePagingSource
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.home.HomeTimelineState

class GuestTimelinePresenter : PresenterBase<HomeTimelineState>() {
    @Composable
    override fun body(): HomeTimelineState {
        val listState =
            remember {
                Pager(PagingConfig(pageSize = 20)) {
                    GuestTimelinePagingSource()
                }.flow
            }.collectPagingProxy()
        return object : HomeTimelineState {
            override val listState = UiState.Success(listState)

            override suspend fun refresh() {
                listState.refreshSuspend()
            }
        }
    }
}
