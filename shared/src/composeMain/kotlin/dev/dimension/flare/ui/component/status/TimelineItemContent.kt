package dev.dimension.flare.ui.component.status

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
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
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.AnglesUp
import dev.dimension.flare.Res
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.isRefreshing
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.home_timeline_new_toots
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.presenter.home.NotificationBadgePresenter
import dev.dimension.flare.ui.presenter.home.TimelinePresenter
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource

@Composable
fun TimelineComponent(
    presenter: TimelinePresenter,
    accountType: AccountType,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    lazyListState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
) {
    val state by producePresenter(
        key = "$presenter:$accountType",
    ) {
        presenter(presenter, accountType, lazyListState)
    }
    val scope = rememberCoroutineScope()
    RefreshContainer(
        modifier = modifier,
        onRefresh = state::refreshSync,
        isRefreshing = state.isRefreshing,
        indicatorPadding = contentPadding,
        content = {
            LazyStatusVerticalStaggeredGrid(
                state = state.lazyListState,
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize(),
            ) {
                status(state.listState)
            }
            state.listState.onSuccess {
                AnimatedVisibility(
                    state.showNewToots,
                    enter = slideInVertically { -it },
                    exit = slideOutVertically { -it },
                    modifier =
                        Modifier
                            .padding(contentPadding)
                            .align(Alignment.TopCenter),
                ) {
                    FilledTonalButton(
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
        },
    )
}

@Composable
private fun presenter(
    presenter: TimelinePresenter,
    accountType: AccountType,
    lazyListState: LazyStaggeredGridState,
): TimelineItemState {
    val badge =
        remember(accountType) {
            NotificationBadgePresenter(accountType)
        }.body()
    val scope = rememberCoroutineScope()
    val state = presenter.body()
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

    fun onNewTootsShown()

    fun refreshSync()
}
