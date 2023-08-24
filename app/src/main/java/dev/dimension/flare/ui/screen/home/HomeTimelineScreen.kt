package dev.dimension.flare.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.moriatsushi.koject.compose.rememberInject
import dev.dimension.flare.R
import dev.dimension.flare.data.datasource.mastodon.homeTimelineDataSource
import dev.dimension.flare.data.repository.app.UiAccount
import dev.dimension.flare.data.repository.app.activeAccountPresenter
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.UiState
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.status.StatusEvent
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.flatMap
import dev.dimension.flare.ui.onSuccess
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeTimelineScreen(
    contentPadding: PaddingValues
) {
    val state by producePresenter {
        homeTimelinePresenter()
    }
    val lazyListState = rememberLazyListState()

    val isAtTheTop by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0
        }
    }
    LaunchedEffect(isAtTheTop) {
        if (isAtTheTop) {
            state.onNewTootsShown()
        }
    }

    val scope = rememberCoroutineScope()
    RefreshContainer(
        modifier = Modifier
            .fillMaxSize(),
        onRefresh = state::refresh,
        refreshing = state.refreshing,
        indicatorPadding = contentPadding,
        content = {
            LazyColumn(
                state = lazyListState,
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                with(state.listState) {
                    with(state.statusEvent) {
                        status()
                    }
                }
            }
            state.listState.onSuccess {
                AnimatedVisibility(
                    state.showNewToots,
                    enter = slideInVertically { -it },
                    exit = slideOutVertically { -it },
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    FilledTonalButton(
                        onClick = {
                            state.onNewTootsShown()
                            scope.launch {
                                lazyListState.animateScrollToItem(0)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = stringResource(id = R.string.home_timeline_new_toots))
                    }
                }
            }
        }
    )
}

@Composable
private fun homeTimelinePresenter(
    statusEvent: StatusEvent = rememberInject()
) = run {
    val account by activeAccountPresenter()
    val listState = account.flatMap {
        when (it) {
            is UiAccount.Mastodon -> UiState.Success(homeTimelineDataSource(account = it).collectAsLazyPagingItems())
            is UiAccount.Misskey -> UiState.Success(
                dev.dimension.flare.data.datasource.misskey.homeTimelineDataSource(
                    account = it
                ).collectAsLazyPagingItems()
            )

            is UiAccount.Bluesky -> UiState.Success(
                dev.dimension.flare.data.datasource.bluesky.homeTimelineDataSource(
                    account = it
                ).collectAsLazyPagingItems()
            )
        }
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
    object {
        val refreshing = refreshing
        val listState = listState
        val showNewToots = showNewToots
        val statusEvent = statusEvent
        fun onNewTootsShown() {
            showNewToots = false
        }

        fun refresh() {
            listState.onSuccess {
                it.refresh()
            }
        }
    }
}
