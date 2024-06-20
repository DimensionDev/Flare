package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.paging.compose.LazyPagingItems
import dev.dimension.flare.common.refreshSuspend
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase

abstract class TimelinePresenter : PresenterBase<TimelineState>() {
    @Composable
    final override fun body(): TimelineState {
        val listState = listState()

        return object : TimelineState {
            override val listState = listState

            override suspend fun refresh() {
                listState.onSuccess {
                    it.refreshSuspend()
                }
            }
        }
    }

    @Composable
    abstract fun listState(): UiState<LazyPagingItems<UiStatus>>
}

@Immutable
interface TimelineState {
    val listState: UiState<LazyPagingItems<UiStatus>>

    suspend fun refresh()
}
