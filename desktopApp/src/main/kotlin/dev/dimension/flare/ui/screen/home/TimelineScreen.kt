package dev.dimension.flare.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.AnglesUp
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.RegisterTabCallback
import dev.dimension.flare.Res
import dev.dimension.flare.common.isRefreshing
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.home_timeline_new_toots
import dev.dimension.flare.ui.common.plus
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.presenter.TimelineItemPresenter
import dev.dimension.flare.ui.presenter.invoke
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.ProgressBar
import io.github.composefluent.component.Text
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TimelineScreen(
    tabItem: TimelineTabItem,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    header: @Composable (() -> Unit)? = null,
    onScrollToTop: (() -> Unit)? = null,
) {
    val state by producePresenter(
        "timeline_$tabItem",
    ) {
        presenter(tabItem)
    }
    TimelineContent(
        state = state,
        modifier = modifier,
        contentPadding = contentPadding,
        header = header,
        onScrollToTop = onScrollToTop,
    )
}

@Composable
internal fun TimelineContent(
    state: TimelineItemPresenter.State,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    header: @Composable (() -> Unit)? = null,
    onScrollToTop: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    RegisterTabCallback(
        state.lazyListState,
        onRefresh = state::refreshSync,
    )
    if (onScrollToTop != null) {
        LaunchedEffect(state.lazyListState) {
            snapshotFlow { state.lazyListState.firstVisibleItemIndex }
                .collect {
                    if (it == 0) {
                        onScrollToTop()
                    }
                }
        }
    }
    Box(
        modifier =
            modifier
                .fillMaxSize(),
    ) {
        LazyStatusVerticalStaggeredGrid(
            contentPadding = LocalWindowPadding.current + contentPadding,
            state = state.lazyListState,
        ) {
            if (header != null) {
                item(
                    span = StaggeredGridItemSpan.FullLine,
                ) {
                    header.invoke()
                }
            }
            status(state.listState)
        }
        AnimatedVisibility(
            state.listState.isRefreshing,
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it },
            modifier =
                Modifier
                    .align(Alignment.TopCenter),
        ) {
            ProgressBar(
                modifier =
                    Modifier
                        .fillMaxWidth(),
            )
        }
        AnimatedVisibility(
            state.showNewToots,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(LocalWindowPadding.current)
                    .padding(contentPadding),
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

@Composable
private fun presenter(tabItem: TimelineTabItem) =
    run {
        remember(tabItem.key) {
            TimelineItemPresenter(tabItem)
        }.invoke()
    }
