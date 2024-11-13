package dev.dimension.flare.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import compose.icons.fontawesomeicons.solid.Pen
import dev.dimension.flare.R
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.LocalBottomBarHeight
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.component.status.TimelineComponent
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.UserPresenter
import dev.dimension.flare.ui.presenter.home.UserState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.screen.settings.TabTitle
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter

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
    val state by producePresenter(key = "timeline_${tabItem.key}") {
        timelinePresenter(tabItem)
    }
    val lazyListState = rememberLazyStaggeredGridState()
    RegisterTabCallback(lazyListState = lazyListState)
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
                    state.user
                        .onError {
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
        TimelineComponent(
            presenter = state.presenter,
            accountType = tabItem.account,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            lazyListState = lazyListState,
        )
    }
}

@Composable
private fun timelinePresenter(tabItem: TimelineTabItem) =
    run {
        val presenter =
            remember(tabItem) {
                tabItem.createPresenter()
            }
        val accountState =
            remember(tabItem.account) {
                UserPresenter(
                    accountType = tabItem.account,
                    userKey = null,
                )
            }.invoke()
        object : UserState by accountState {
            val presenter = presenter
        }
    }
