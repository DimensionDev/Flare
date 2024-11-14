package dev.dimension.flare.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ComposeRouteDestination
import com.ramcosta.composedestinations.generated.destinations.ServiceSelectRouteDestination
import com.ramcosta.composedestinations.generated.destinations.TabSettingRouteDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Pen
import compose.icons.fontawesomeicons.solid.Sliders
import dev.dimension.flare.R
import dev.dimension.flare.data.model.HomeTimelineTabItem
import dev.dimension.flare.data.model.TabMetaData
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.component.LocalBottomBarHeight
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.component.status.TimelineComponent
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.TimelinePresenter
import dev.dimension.flare.ui.presenter.home.UserPresenter
import dev.dimension.flare.ui.presenter.home.UserState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.screen.settings.TabTitle
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.koin.compose.koinInject

@Destination<RootGraph>(
    wrappers = [ThemeWrapper::class],
)
@Composable
internal fun HomeTimelineRoute(
    navigator: DestinationsNavigator,
    drawerState: DrawerState,
    accountType: AccountType,
) {
    val scope = rememberCoroutineScope()
    HomeTimelineScreen(
        accountType = accountType,
        toCompose = {
            navigator.navigate(ComposeRouteDestination(accountType = accountType))
        },
        toQuickMenu = {
            scope.launch {
                drawerState.open()
            }
        },
        toLogin = {
            navigator.navigate(ServiceSelectRouteDestination)
        },
        toTabSettings = {
            navigator.navigate(TabSettingRouteDestination(accountType))
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTimelineScreen(
    accountType: AccountType,
    toCompose: () -> Unit,
    toQuickMenu: () -> Unit,
    toLogin: () -> Unit,
    toTabSettings: () -> Unit,
) {
    val state by producePresenter(key = "home_timeline_$accountType") {
        timelinePresenter(accountType)
    }
    val scope = rememberCoroutineScope()
    state.pagerState.onSuccess { pagerState ->
        state.tabState.onSuccess { tabState ->
            val lazyListState = tabState[pagerState.currentPage].lazyListState
            RegisterTabCallback(lazyListState = lazyListState)
        }
    }

    val topAppBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    FlareScaffold(
        topBar = {
            FlareTopAppBar(
                title = {
                    state.pagerState.onSuccess { pagerState ->
                        state.tabState.onSuccess { tabs ->
                            if (tabs.size > 1) {
                                SecondaryScrollableTabRow(
                                    selectedTabIndex = pagerState.currentPage,
                                    edgePadding = 0.dp,
                                    divider = {},
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    state.tabState.onSuccess { tabs ->
                                        tabs.forEachIndexed { index, tab ->
                                            Tab(
                                                selected = index == pagerState.currentPage,
                                                onClick = {
                                                    scope.launch {
                                                        pagerState.scrollToPage(index)
                                                    }
                                                },
                                            ) {
                                                TabTitle(
                                                    tab.metaData.title,
                                                    modifier =
                                                        Modifier
                                                            .padding(8.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                TabTitle(title = tabs[0].metaData.title)
                            }
                        }
                    }
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
                        }.onSuccess {
                            IconButton(
                                onClick = toTabSettings,
                            ) {
                                FAIcon(
                                    FontAwesomeIcons.Solid.Sliders,
                                    contentDescription = null,
                                )
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
        state.pagerState.onSuccess { pagerState ->
            state.tabState.onSuccess { tabs ->
                if (tabs.size == 1) {
                    // workaround for a bug in HorizontalPager with Drawer
                    // https://issuetracker.google.com/issues/167408603
                    TimelineComponent(
                        presenter = tabs[0].presenter,
                        accountType = accountType,
                        contentPadding = contentPadding,
                        modifier = Modifier.fillMaxSize(),
                        lazyListState = tabs[0].lazyListState,
                    )
                } else {
                    HorizontalPager(
                        state = pagerState,
                    ) { index ->
                        val tab = tabs[index]
                        TimelineComponent(
                            presenter = tab.presenter,
                            accountType = accountType,
                            contentPadding = contentPadding,
                            modifier = Modifier.fillMaxSize(),
                            lazyListState = tab.lazyListState,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun timelinePresenter(
    accountType: AccountType,
    settingsRepository: SettingsRepository = koinInject(),
) = run {
    val accountState =
        remember(accountType) {
            UserPresenter(
                accountType = accountType,
                userKey = null,
            )
        }.invoke()
    val tabSettings by settingsRepository.tabSettings.collectAsUiState()
    val tabs =
        remember(accountType, accountState, tabSettings) {
            accountState.user.flatMap(
                onError = {
                    UiState.Success(
                        listOf(HomeTimelineTabItem(accountType = AccountType.Guest)),
                    )
                },
            ) { user ->
                tabSettings.map {
                    it.homeTabs.getOrDefault(
                        user.key,
                        listOf(HomeTimelineTabItem(accountType = AccountType.Specific(user.key))),
                    )
                }
            }
        }
    val pagerState =
        tabs.map {
            rememberPagerState { it.size }
        }
    val tabState =
        remember(tabs) {
            tabs.map {
                it.map {
                    it.createPresenter() to it.metaData
                }
            }
        }.map {
            it.map {
                TimelineTabState(
                    presenter = it.first,
                    lazyListState = rememberLazyStaggeredGridState(),
                    metaData = it.second,
                )
            }
        }

    object : UserState by accountState {
        val pagerState = pagerState
        val tabState = tabState
    }
}

private data class TimelineTabState(
    val presenter: TimelinePresenter,
    val lazyListState: LazyStaggeredGridState,
    val metaData: TabMetaData,
)
