package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.paging.LoadState
import app.cash.paging.compose.LazyPagingItems
import app.cash.paging.compose.collectAsLazyPagingItems
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
        val listState = activeAccountServicePresenter().map { (service, account) ->
            remember(account.accountKey) {
                service.homeTimeline()
            }.collectAsLazyPagingItems()
        }
        var showNewToots by remember { mutableStateOf(false) }
        val refreshing =
            listState is UiState.Loading ||
                    listState is UiState.Success && listState.data.loadState.refresh is LoadState.Loading && listState.data.itemCount != 0
        if (listState is UiState.Success && listState.data.itemCount > 0) {
            LaunchedEffect(Unit) {
                snapshotFlow { listState.data.peek(0)?.statusKey }
                    .mapNotNull { it }
                    .distinctUntilChanged()
                    .drop(1)
                    .collect {
                        showNewToots = true
                    }
            }
        }

        return object : HomeTimelineState(
            refreshing,
            listState,
            showNewToots
        ) {
            override fun refresh() {
                listState.onSuccess {
                    it.refresh()
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
    val refreshing: Boolean,
    val listState: UiState<LazyPagingItems<UiStatus>>,
    val showNewToots: Boolean,
) {
    abstract fun refresh()

    abstract fun onNewTootsShown()

    companion object {
        val Empty = object : HomeTimelineState(
            refreshing = false,
            listState = UiState.Loading(),
            showNewToots = false,
        ) {
            override fun refresh() {
            }

            override fun onNewTootsShown() {
            }
        }
    }
}

class CounterPresenter : PresenterBase<CounterState>() {
    @Composable
    override fun body(): CounterState {
        var count by remember { mutableStateOf(0) }
        return object : CounterState(count.toString()) {
            override fun increment() {
                count++
            }
        }
    }
}
abstract class CounterState(
    val count: String
) {
    abstract fun increment()
    companion object {
        val Empty = object : CounterState("0") {
            override fun increment() {
            }
        }
    }
}