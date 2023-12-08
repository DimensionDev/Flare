package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.dimension.flare.common.LazyPagingItemsProxy
import dev.dimension.flare.common.collectPagingProxy
import dev.dimension.flare.data.repository.activeAccountServicePresenter
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.mapNotNull

class HomeTimelinePresenter : PresenterBase<HomeTimelineState>() {
    @Composable
    override fun body(): HomeTimelineState {
        val listState =
            activeAccountServicePresenter().map { (service, account) ->
                remember(account.accountKey) {
                    service.homeTimeline()
                }.collectPagingProxy()
            }
        var showNewToots by remember { mutableStateOf(false) }
        if (listState is UiState.Success && listState.data.itemCount > 0) {
            LaunchedEffect(Unit) {
                snapshotFlow {
                    if (listState.data.itemCount > 0) {
                        listState.data.peek(0)?.statusKey
                    } else {
                        null
                    }
                }
                    .mapNotNull { it }
                    .distinctUntilChanged()
                    .drop(1)
                    .collect {
                        showNewToots = true
                    }
            }
        }

        return object : HomeTimelineState(
            listState,
            showNewToots,
        ) {
            override suspend fun refresh() {
                listState.onSuccess {
                    it.refreshSuspend()
                }
            }

            override fun onNewTootsShown() {
                showNewToots = false
            }
        }
    }
}

@Immutable
abstract class HomeTimelineState(
    val listState: UiState<LazyPagingItemsProxy<UiStatus>>,
    val showNewToots: Boolean,
) {
    abstract suspend fun refresh()

    abstract fun onNewTootsShown()

    companion object {
        val Empty =
            object : HomeTimelineState(
                listState = UiState.Loading(),
                showNewToots = false,
            ) {
                override suspend fun refresh() {
                }

                override fun onNewTootsShown() {
                }
            }
    }
}
