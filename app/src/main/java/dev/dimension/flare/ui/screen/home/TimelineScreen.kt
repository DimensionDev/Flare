package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import dev.dimension.flare.R
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.TabTitle
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.presenter.home.UserPresenter
import dev.dimension.flare.ui.presenter.invoke
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimelineScreen(
    tabItem: TimelineTabItem,
    toLogin: (() -> Unit)? = null,
    onBack: () -> Unit,
) {
    val state by producePresenter(key = "timeline_screen_${tabItem.key}") {
        timelinePresenter(tabItem)
    }
//    RegisterTabCallback(
//        lazyListState = state.lazyListState,
//        onRefresh = {
//            state.refreshSync()
//        },
//    )
    val scope = rememberCoroutineScope()
    val listState = rememberLazyStaggeredGridState()
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    FlareScaffold(
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = {
                    TabTitle(title = tabItem.metaData.title)
                },
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    BackButton(onBack)
                },
                actions = {
                    if (toLogin != null) {
                        state.user
                            .onError {
                                TextButton(onClick = toLogin) {
                                    Text(text = stringResource(id = R.string.login_button))
                                }
                            }
                    }
                },
                modifier =
                    Modifier
                        .clickable(
                            onClick = {
                                scope.launch {
                                    listState.animateScrollToItem(0)
                                }
                            },
                            indication = null,
                            interactionSource =
                                remember {
                                    androidx.compose.foundation.interaction
                                        .MutableInteractionSource()
                                },
                        ),
            )
        },
        modifier =
            Modifier
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { contentPadding ->
        TimelineItemContent(
            item = tabItem,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
            lazyStaggeredGridState = listState,
        )
    }
}

@Composable
private fun timelinePresenter(tabItem: TimelineTabItem) =
    run {
        remember(tabItem.account) {
            UserPresenter(
                accountType = tabItem.account,
                userKey = null,
            )
        }.invoke()
    }
