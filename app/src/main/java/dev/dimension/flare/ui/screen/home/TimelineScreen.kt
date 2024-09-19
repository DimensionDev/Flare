package dev.dimension.flare.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ComposeRouteDestination
import com.ramcosta.composedestinations.generated.destinations.ServiceSelectRouteDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.AnglesUp
import compose.icons.fontawesomeicons.solid.Pen
import dev.dimension.flare.R
import dev.dimension.flare.common.isRefreshing
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.LocalBottomBarHeight
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.TimelineState
import dev.dimension.flare.ui.presenter.home.UserPresenter
import dev.dimension.flare.ui.presenter.home.UserState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.screen.settings.TabTitle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

// @RootNavGraph(start = true) // sets this as the start destination of the default nav graph
@Destination<RootGraph>(
    wrappers = [ThemeWrapper::class],
)
@Composable
internal fun TimelineRoute(
    navigator: DestinationsNavigator,
    tabItem: TimelineTabItem,
    drawerState: DrawerState,
) {
    val scope = rememberCoroutineScope()
    TimelineScreen(
        tabItem = tabItem,
        toCompose = {
            navigator.navigate(ComposeRouteDestination(accountType = tabItem.account))
        },
        toQuickMenu = {
            scope.launch {
                drawerState.open()
            }
        },
        toLogin = {
            navigator.navigate(ServiceSelectRouteDestination)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimelineScreen(
    tabItem: TimelineTabItem,
    toCompose: () -> Unit,
    toQuickMenu: () -> Unit,
    toLogin: () -> Unit,
) {
    val state by producePresenter(key = "home_timeline_${tabItem.key}") {
        timelinePresenter(tabItem)
    }
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyStaggeredGridState()
    RegisterTabCallback(lazyListState = lazyListState)
    val isAtTheTop by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0
        }
    }
    LaunchedEffect(isAtTheTop, state.showNewToots) {
        if (isAtTheTop) {
            state.onNewTootsShown()
        }
    }
    val topAppBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    FlareScaffold(
        topBar = {
            TopAppBar(
                title = {
                    TabTitle(title = tabItem.metaData.title)
                },
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    if (LocalBottomBarHeight.current != 0.dp) {
                        state.user.onSuccess {
                            IconButton(
                                onClick = toQuickMenu,
                            ) {
                                AvatarComponent(it.avatar, size = 24.dp)
                            }
                        }
                    }
                },
                actions = {
                    state.user.onError {
                        TextButton(onClick = toLogin) {
                            Text(text = stringResource(id = R.string.login_button))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            state.user.onSuccess {
                AnimatedVisibility(
                    visible = topAppBarScrollBehavior.state.heightOffset == 0f && LocalBottomBarHeight.current != 0.dp,
                    enter = scaleIn(),
                    exit = scaleOut(),
                ) {
                    FloatingActionButton(
                        onClick = toCompose,
                    ) {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.Pen,
                            contentDescription = stringResource(id = R.string.compose_title),
                        )
                    }
                }
            }
        },
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { contentPadding ->
        RefreshContainer(
            modifier =
                Modifier
                    .fillMaxSize(),
            onRefresh = state::refreshSync,
            isRefreshing = state.isRefreshing,
            indicatorPadding = contentPadding,
            content = {
                LazyStatusVerticalStaggeredGrid(
                    state = lazyListState,
                    contentPadding = contentPadding,
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
                                    lazyListState.scrollToItem(0)
                                }
                            },
                        ) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.AnglesUp,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = stringResource(id = R.string.home_timeline_new_toots))
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun timelinePresenter(tabItem: TimelineTabItem) =
    run {
        val scope = rememberCoroutineScope()
        val state = remember(tabItem.account) { tabItem.createPresenter() }.invoke()
        val accountState =
            remember(tabItem.account) {
                UserPresenter(
                    accountType = tabItem.account,
                    userKey = null,
                )
            }.invoke()
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
        object : UserState by accountState, TimelineState by state {
            val showNewToots = showNewToots
            val isRefreshing = state.listState.isRefreshing

            fun onNewTootsShown() {
                showNewToots = false
            }

            fun refreshSync() {
                scope.launch {
                    state.refresh()
                }
            }
        }
    }
