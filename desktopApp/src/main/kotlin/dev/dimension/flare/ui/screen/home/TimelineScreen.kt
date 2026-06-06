package dev.dimension.flare.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.AnglesUp
import compose.icons.fontawesomeicons.solid.ArrowsRotate
import compose.icons.fontawesomeicons.solid.Sliders
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.RegisterTabCallback
import dev.dimension.flare.Res
import dev.dimension.flare.common.isRefreshing
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
import dev.dimension.flare.data.model.tab.resolveTimelineAppearance
import dev.dimension.flare.edit_tab_title
import dev.dimension.flare.home_timeline_new_toots
import dev.dimension.flare.refresh
import dev.dimension.flare.ui.common.plus
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScrollBar
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.component.TabIcon
import dev.dimension.flare.ui.component.floatingToolbarVerticalNestedScroll
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.TimelineWithLazyListState
import dev.dimension.flare.ui.presenter.home.HomeTabItemPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.rememberTimelineItemPresenterWithLazyListState
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.ProgressBar
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DeckTimelineScreen(
    id: String,
    toTabSettings: () -> Unit,
) {
    val tabState by producePresenter("deck_timeline_$id") {
        remember { HomeTabItemPresenter(id = id) }.invoke()
    }
    tabState.tabItem.onSuccess { tabItem ->
        val timelineState = rememberTimelineItemPresenterWithLazyListState(tabItem)
        val isTopBarExpanded = remember(tabItem.id) { androidx.compose.runtime.mutableStateOf(true) }
        val timelineAppearance = LocalTimelineAppearance.current
        CompositionLocalProvider(
            LocalTimelineAppearance provides
                remember(
                    tabItem.appearancePatch,
                    timelineAppearance,
                ) {
                    tabItem.resolveTimelineAppearance(timelineAppearance)
                },
        ) {
            Box {
                TimelineContent(
                    state = timelineState,
                    modifier =
                        Modifier
                            .floatingToolbarVerticalNestedScroll(
                                expanded = isTopBarExpanded.value,
                                onExpand = {
                                    isTopBarExpanded.value = true
                                },
                                onCollapse = {
                                    isTopBarExpanded.value = false
                                },
                            ),
                    contentPadding = PaddingValues(top = 48.dp),
                    allowGalleryMode = true,
                    onScrollToTop = {
                        isTopBarExpanded.value = true
                    },
                )
                AnimatedVisibility(
                    visible = isTopBarExpanded.value,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                // prevent click through
                            },
                    enter = slideInVertically { -it },
                    exit = slideOutVertically { -it },
                ) {
                    Box {
                        Box(
                            modifier =
                                Modifier
                                    .matchParentSize()
                                    .background(FluentTheme.colors.background.mica.base)
                                    .blur(32.dp),
                        )
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(LocalWindowPadding.current),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                modifier = Modifier.padding(start = screenHorizontalPadding),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TabIcon(
                                    tabItem = tabItem,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                dev.dimension.flare.ui.component.Text(
                                    text = tabItem.title,
                                )
                            }
                            Row(
                                modifier = Modifier.padding(end = screenHorizontalPadding),
                            ) {
                                SubtleButton(
                                    onClick = toTabSettings,
                                ) {
                                    FAIcon(
                                        imageVector = FontAwesomeIcons.Solid.Sliders,
                                        contentDescription = stringResource(Res.string.edit_tab_title),
                                    )
                                }
                                SubtleButton(
                                    onClick = {
                                        timelineState.refreshSync()
                                    },
                                ) {
                                    FAIcon(
                                        imageVector = FontAwesomeIcons.Solid.ArrowsRotate,
                                        contentDescription = stringResource(Res.string.refresh),
                                    )
                                }
                            }
                        }
                    }
                }
                AnimatedVisibility(
                    timelineState.isRefreshing,
                    enter = slideInVertically { -it },
                    exit = slideOutVertically { -it },
                ) {
                    ProgressBar(
                        modifier =
                            Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
internal fun TimelineScreen(
    tabItem: TimelineTabItemV2,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    header: @Composable (() -> Unit)? = null,
    onScrollToTop: (() -> Unit)? = null,
) {
    val state = rememberTimelineItemPresenterWithLazyListState(tabItem)

    val timelineAppearance = LocalTimelineAppearance.current
    CompositionLocalProvider(
        LocalTimelineAppearance provides
            remember(
                tabItem.appearancePatch,
                timelineAppearance,
            ) {
                tabItem.resolveTimelineAppearance(timelineAppearance)
            },
    ) {
        TimelineContent(
            state = state,
            modifier = modifier,
            contentPadding = contentPadding,
            header = header,
            onScrollToTop = onScrollToTop,
        )
    }
}

@Composable
internal fun TimelineContent(
    state: TimelineWithLazyListState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    header: @Composable (() -> Unit)? = null,
    onScrollToTop: (() -> Unit)? = null,
    allowGalleryMode: Boolean = false,
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
        FlareScrollBar(
            state = state.lazyListState,
            scrollbarPadding =
                PaddingValues(
                    top = contentPadding.calculateTopPadding(),
                ),
        ) {
            LazyStatusVerticalStaggeredGrid(
                contentPadding = LocalWindowPadding.current + contentPadding,
                state = state.lazyListState,
                allowGalleryMode = allowGalleryMode,
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
                Text(
                    text =
                        pluralStringResource(
                            Res.plurals.home_timeline_new_toots,
                            state.newPostsCount,
                            state.newPostsCount,
                        ),
                )
            }
        }
    }
}
