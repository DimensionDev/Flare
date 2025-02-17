package dev.dimension.flare.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.unit.dp
import com.konyaco.fluent.component.AccentButton
import com.konyaco.fluent.component.ProgressBar
import com.konyaco.fluent.component.Text
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.AnglesUp
import dev.dimension.flare.RegisterTabCallback
import dev.dimension.flare.Res
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.isRefreshing
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.home_timeline_new_toots
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.presenter.home.NotificationBadgePresenter
import dev.dimension.flare.ui.presenter.home.UserPresenter
import dev.dimension.flare.ui.presenter.home.UserState
import dev.dimension.flare.ui.presenter.invoke
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TimelineScreen(tabItem: TimelineTabItem) {
    val scope = rememberCoroutineScope()
    val state by producePresenter(
        "timeline_$tabItem",
    ) {
        presenter(tabItem)
    }
    val listState = rememberLazyStaggeredGridState()
    RegisterTabCallback(listState, onRefresh = state::refreshSync)
    Box(
        modifier =
            Modifier
                .fillMaxSize(),
    ) {
        LazyStatusVerticalStaggeredGrid(
            contentPadding = PaddingValues(16.dp),
            state = listState,
        ) {
            status(state.listState)
        }
        if (state.listState.isRefreshing) {
            ProgressBar(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
            )
        }
        state.listState.onSuccess {
            AnimatedVisibility(
                state.showNewToots,
                enter = slideInVertically { -it },
                exit = slideOutVertically { -it },
                modifier =
                    Modifier
                        .align(Alignment.TopCenter),
            ) {
                AccentButton(
                    onClick = {
                        state.onNewTootsShown()
                        scope.launch {
                            state.lazyListState.scrollToItem(0)
                        }
                    },
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.AnglesUp,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = stringResource(Res.string.home_timeline_new_toots))
                }
            }
        }
    }
}

@Composable
private fun presenter(tabItem: TimelineTabItem) =
    run {
        val state = timelineItemPresenter(tabItem)
        val accountState =
            remember(tabItem.account) {
                UserPresenter(
                    accountType = tabItem.account,
                    userKey = null,
                )
            }.invoke()
        object : UserState by accountState, TimelineItemState by state {
        }
    }

@Composable
internal fun timelineItemPresenter(timelineTabItem: TimelineTabItem): TimelineItemState {
    val timelinePresenter =
        remember(timelineTabItem) {
            timelineTabItem.createPresenter()
        }
    val badge =
        remember(timelineTabItem) {
            NotificationBadgePresenter(timelineTabItem.account)
        }.invoke()
    val scope = rememberCoroutineScope()
    val state = timelinePresenter()
    var showNewToots by remember { mutableStateOf(false) }
    state.listState.onSuccess {
        LaunchedEffect(Unit) {
            snapshotFlow {
                if (itemCount > 0) {
                    peek(0)?.itemKey
                } else {
                    null
                }
            }.mapNotNull { it }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    showNewToots = true
                }
        }
    }
    val lazyListState = rememberLazyStaggeredGridState()
    val isAtTheTop by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0
        }
    }
    LaunchedEffect(isAtTheTop, showNewToots) {
        if (isAtTheTop) {
            showNewToots = false
        }
    }
    return object : TimelineItemState {
        override val listState = state.listState
        override val showNewToots = showNewToots
        override val isRefreshing = state.listState.isRefreshing
        override val lazyListState = lazyListState
        override val timelineTabItem = timelineTabItem

        override fun onNewTootsShown() {
            showNewToots = false
        }

        override fun refreshSync() {
            scope.launch {
                state.refresh()
            }
            badge.refresh()
        }
    }
}

@Immutable
internal interface TimelineItemState {
    val listState: PagingState<UiTimeline>
    val showNewToots: Boolean
    val isRefreshing: Boolean
    val lazyListState: LazyStaggeredGridState
    val timelineTabItem: TimelineTabItem

    fun onNewTootsShown()

    fun refreshSync()
}
