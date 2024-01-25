package dev.dimension.flare.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.ramcosta.composedestinations.annotation.RootNavGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.dimension.flare.R
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.StatusEvent
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.ActiveAccountPresenter
import dev.dimension.flare.ui.presenter.home.ActiveAccountState
import dev.dimension.flare.ui.presenter.home.HomeTimelinePresenter
import dev.dimension.flare.ui.presenter.home.HomeTimelineState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.screen.destinations.ComposeRouteDestination
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@RootNavGraph(start = true) // sets this as the start destination of the default nav graph
@Destination(
    wrappers = [ThemeWrapper::class],
)
@Composable
internal fun HomeRoute(
    navigator: DestinationsNavigator,
    drawerState: DrawerState,
) {
    val scope = rememberCoroutineScope()
    HomeTimelineScreen(
        toCompose = {
            navigator.navigate(ComposeRouteDestination)
        },
        toQuickMenu = {
            scope.launch {
                drawerState.open()
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeTimelineScreen(
    toCompose: () -> Unit,
    toQuickMenu: () -> Unit,
) {
    val state by producePresenter {
        homeTimelinePresenter()
    }
    val lazyListState = rememberLazyStaggeredGridState()
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
    val topAppBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.home_tab_home_title))
                },
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    state.user.onSuccess {
                        IconButton(
                            onClick = toQuickMenu,
                        ) {
                            AvatarComponent(it.avatarUrl, size = 24.dp)
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = toCompose,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                )
            }
        },
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { contentPadding ->
        RefreshContainer(
            modifier =
                Modifier
                    .fillMaxSize(),
            onRefresh = state::refresh,
            indicatorPadding = contentPadding,
            content = {
                LazyStatusVerticalStaggeredGrid(
                    state = lazyListState,
                    contentPadding = contentPadding,
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
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
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
private fun homeTimelinePresenter(statusEvent: StatusEvent = koinInject()) =
    run {
        val state = remember { HomeTimelinePresenter() }.invoke()
        val accountState = remember { ActiveAccountPresenter() }.invoke()
        var showNewToots by remember { mutableStateOf(false) }
        val listState = state.listState
        if (listState is UiState.Success && listState.data.itemCount > 0) {
            LaunchedEffect(Unit) {
                snapshotFlow {
                    if (listState.data.itemCount > 0) {
                        listState.data.peek(0)?.statusKey
                    } else {
                        null
                    }
                }
                    .mapNotNull { it }
                    .distinctUntilChanged()
                    .drop(1)
                    .collect {
                        showNewToots = true
                    }
            }
        }
        object : ActiveAccountState by accountState, HomeTimelineState by state {
            val statusEvent = statusEvent
            val showNewToots = showNewToots

            fun onNewTootsShown() {
                showNewToots = false
            }
        }
    }
