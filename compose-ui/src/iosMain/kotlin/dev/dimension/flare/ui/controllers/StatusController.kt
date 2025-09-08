package dev.dimension.flare.ui.controllers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.slapps.cupertino.CupertinoText
import com.slapps.cupertino.theme.CupertinoTheme
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.AnglesUp
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.ComponentAppearance
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.LocalComponentAppearance
import dev.dimension.flare.ui.component.floatingToolbarVerticalNestedScroll
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.StatusItem
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.presenter.TimelineItemPresenter
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import platform.UIKit.UIViewController

public fun StatusController(
    data: UiTimeline?,
    detailStatusKey: MicroBlogKey?,
    heightChanged: (Int) -> Unit,
): UIViewController = ComposeUIViewController {
    CupertinoTheme {
        CompositionLocalProvider(
            LocalComponentAppearance provides ComponentAppearance()
        ) {
            StatusItem(
                item = data,
                detailStatusKey = detailStatusKey,
                horizontalPadding = 0.dp,
                modifier = Modifier.onGloballyPositioned {
                    heightChanged(it.size.height)
                    println("Height: ${it.size.height}")
                }
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
public fun TimelineController(
    state: TimelineItemPresenter.State,
): UIViewController = ComposeUIViewController(
    configure = {
        parallelRendering = true
        enableBackGesture = false
        opaque = false
    }
) {
    CupertinoTheme {
        CompositionLocalProvider(
            LocalComponentAppearance provides ComponentAppearance()
        ) {
            val scope = rememberCoroutineScope()
            Box(
                modifier =
                    Modifier
                        .fillMaxSize(),
            ) {
                LazyStatusVerticalStaggeredGrid(
                    state = state.lazyListState,
                ) {
                    status(state.listState)
                }
                AnimatedVisibility(
                    state.showNewToots,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier.clickable {
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
                        CupertinoText(text = "sadsa")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class, FlowPreview::class)
public fun TimelineTabItemController(
    data: TimelineTabItem,
    topPadding: Int,
    onExpanded: () -> Unit,
    onCollapsed: () -> Unit,
): UIViewController = ComposeUIViewController(
    configure = {
        parallelRendering = true
        enableBackGesture = false
//        opaque = false
    }
) {
    val scope = rememberCoroutineScope()
    var isExpanded by remember { mutableStateOf(false) }
    CupertinoTheme {
        CompositionLocalProvider(
            LocalComponentAppearance provides ComponentAppearance()
        ) {

            val state by producePresenter {
                remember {
                    TimelineItemPresenter(data)
                }.body()
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(CupertinoTheme.colorScheme.systemGroupedBackground),
            ) {
                LazyStatusVerticalStaggeredGrid(
                    state = state.lazyListState,
                    modifier = Modifier
                        .floatingToolbarVerticalNestedScroll(
                            expanded = isExpanded,
                            onExpand = {
                                isExpanded = true
                                onExpanded()
                            },
                            onCollapse = {
                                isExpanded = false
                                onCollapsed()
                            }
                        )
                ) {
                    item(
                        span = StaggeredGridItemSpan.FullLine
                    ) {
                        Column {
                            Spacer(
                                modifier = Modifier
                                    .windowInsetsTopHeight(WindowInsets.safeContent)
                            )
                            Spacer(
                                modifier = Modifier
                                    .height(topPadding.dp)
                            )
                        }
                    }
                    status(state.listState)
                }
                AnimatedVisibility(
                    state.showNewToots,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier.clickable {
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
                        CupertinoText(text = "sadsa")
                    }
                }
            }
        }
    }
}
