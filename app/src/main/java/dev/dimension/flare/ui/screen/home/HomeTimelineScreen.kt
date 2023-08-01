package dev.dimension.flare.ui.screen.home

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
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
import androidx.paging.compose.collectAsLazyPagingItems
import com.moriatsushi.koject.compose.rememberInject
import dev.dimension.flare.R
import dev.dimension.flare.data.datasource.mastodon.homeTimelineDataSource
import dev.dimension.flare.data.repository.UiAccount
import dev.dimension.flare.data.repository.activeAccountPresenter
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.UiState
import dev.dimension.flare.ui.component.status.DefaultMastodonStatusEvent
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.flatMap
import dev.dimension.flare.ui.onSuccess
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.M)
@Composable
internal fun HomeTimelineScreen() {
    val state by producePresenter {
        HomeTimelinePresenter()
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
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = lazyListState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            with(state.listState) {
                status(
                    event = state.eventHandler
                )
            }
        }
        state.listState.onSuccess {
            AnimatedVisibility(
                state.showNewToots,
                enter = slideInVertically { -it },
                exit = slideOutVertically { -it },
                modifier = Modifier.align(Alignment.TopCenter),
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
}


@Composable
private fun HomeTimelinePresenter(
    defaultEvent: DefaultMastodonStatusEvent = rememberInject(),
) = run {
    val account by activeAccountPresenter()
    val listState = account.flatMap {
        when (it) {
            is UiAccount.Mastodon -> UiState.Success(homeTimelineDataSource(account = it).collectAsLazyPagingItems())
        }
    }
    var showNewToots by remember { mutableStateOf(false) }
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
        val listState = listState
        val eventHandler = defaultEvent
        val showNewToots = showNewToots
        fun onNewTootsShown() {
            showNewToots = false
        }
    }
}