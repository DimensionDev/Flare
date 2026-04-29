package dev.dimension.flare.ui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRailState
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Bars
import compose.icons.fontawesomeicons.solid.ClockRotateLeft
import compose.icons.fontawesomeicons.solid.Gear
import compose.icons.fontawesomeicons.solid.Pen
import compose.icons.fontawesomeicons.solid.PenToSquare
import compose.icons.fontawesomeicons.solid.SquareRss
import dev.dimension.flare.R
import dev.dimension.flare.data.model.AllListTabItem
import dev.dimension.flare.data.model.AllNotificationTabItem
import dev.dimension.flare.data.model.Bluesky
import dev.dimension.flare.data.model.DirectMessageTabItem
import dev.dimension.flare.data.model.DiscoverTabItem
import dev.dimension.flare.data.model.HomeTabItem
import dev.dimension.flare.data.model.HomeTimelineTabItem
import dev.dimension.flare.data.model.Misskey
import dev.dimension.flare.data.model.NotificationTabItem
import dev.dimension.flare.data.model.ProfileTabItem
import dev.dimension.flare.data.model.RssTabItem
import dev.dimension.flare.data.model.SettingsTabItem
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.common.OnNewIntent
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.InAppNotificationComponent
import dev.dimension.flare.ui.component.NavigationSuiteScaffold2
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.component.TabIcon
import dev.dimension.flare.ui.component.TabTitle
import dev.dimension.flare.ui.component.TopLevelBackStack
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.HomeTabsPresenter
import dev.dimension.flare.ui.presenter.home.AllNotificationBadgePresenter
import dev.dimension.flare.ui.presenter.home.DeepLinkPresenter
import dev.dimension.flare.ui.presenter.home.LoggedInPresenter
import dev.dimension.flare.ui.presenter.home.SecondaryTabsPresenter
import dev.dimension.flare.ui.presenter.home.UserPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.route.Route
import dev.dimension.flare.ui.route.Router
import dev.dimension.flare.ui.screen.splash.SplashScreen
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(
    ExperimentalMaterial3AdaptiveNavigationSuiteApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalSharedTransitionApi::class,
)
@Composable
internal fun HomeScreen(afterInit: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val state by producePresenter { presenter(uriHandler = uriHandler) }
    val hapticFeedback = LocalHapticFeedback.current
    state.tabs
        .onSuccess { tabs ->
            val currentRoute =
                remember(
                    tabs,
                    state.topLevelBackStack.takeSuccess()?.topLevelKey,
                ) {
                    state.topLevelBackStack.takeSuccess()?.topLevelKey ?: getDirection(tabs.all.first())
                }
            OnNewIntent(
                withOnCreateIntent = true,
            ) {
                it.dataString?.let { url -> state.deeplinkPresenter.handle(url) }
            }
            LaunchedEffect(Unit) {
                afterInit.invoke()
            }
            val layoutType =
                NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(
                    currentWindowAdaptiveInfoV2(),
                )
            Box {
                NavigationSuiteScaffold2(
                    wideNavigationRailState = state.wideNavigationRailState,
                    modifier = Modifier.fillMaxSize(),
                    bottomBarAutoHideEnabled = state.navigationState.bottomBarAutoHideEnabled,
                    layoutType = layoutType,
                    showFab =
                        state.loggedInState.takeSuccess() == true &&
                            state.topLevelBackStack.takeSuccess()?.currentKey is Route.Home,
                    onFabClicked = {
                        state.navigate(Route.Compose.New)
                    },
                    navigationSuiteColors =
                        NavigationSuiteDefaults.colors(
                            navigationBarContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                    railHeader = {
                        if (layoutType == NavigationSuiteType.NavigationRail) {
                            IconButton(
                                onClick = {
                                    state.openDrawer()
                                },
                                modifier =
                                    Modifier
                                        .padding(
                                            horizontal = 24.dp,
                                        ).padding(top = 12.dp, bottom = 4.dp),
                            ) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.Bars,
                                    contentDescription = null,
                                )
                            }

                            if (layoutType == NavigationSuiteType.NavigationRail &&
                                state.loggedInState.takeSuccess() == true
                            ) {
                                SharedTransitionLayout {
                                    AnimatedContent(
                                        state.wideNavigationRailState.currentValue,
                                        modifier = Modifier.padding(horizontal = 20.dp),
                                    ) { railState ->
                                        when (railState) {
                                            WideNavigationRailValue.Collapsed -> {
                                                FloatingActionButton(
                                                    onClick = {
                                                        state.navigate(Route.Compose.New)
                                                    },
                                                    elevation =
                                                        FloatingActionButtonDefaults.elevation(
                                                            defaultElevation = 0.dp,
                                                        ),
                                                    modifier =
                                                        Modifier
                                                            .sharedElement(
                                                                rememberSharedContentState(key = "compose"),
                                                                animatedVisibilityScope = this@AnimatedContent,
                                                            ),
                                                ) {
                                                    FAIcon(
                                                        imageVector = FontAwesomeIcons.Solid.Pen,
                                                        contentDescription = stringResource(id = R.string.compose_title),
                                                    )
                                                }
                                            }

                                            WideNavigationRailValue.Expanded -> {
                                                ExtendedFloatingActionButton(
                                                    onClick = {
                                                        state.navigate(Route.Compose.New)
                                                    },
                                                    icon = {
                                                        FAIcon(
                                                            imageVector = FontAwesomeIcons.Solid.Pen,
                                                            contentDescription =
                                                                stringResource(
                                                                    id = R.string.compose_title,
                                                                ),
                                                        )
                                                    },
                                                    text = {
                                                        Text(text = stringResource(id = R.string.compose_title))
                                                    },
                                                    elevation =
                                                        FloatingActionButtonDefaults.elevation(
                                                            defaultElevation = 0.dp,
                                                        ),
                                                    modifier =
                                                        Modifier
                                                            .fillMaxWidth()
                                                            .sharedElement(
                                                                rememberSharedContentState(key = "compose"),
                                                                animatedVisibilityScope = this@AnimatedContent,
                                                            ),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    navigationSuiteItems = {
                        tabs.primary.forEach { tab ->
                            item(
                                selected = currentRoute == getDirection(tab),
                                onClick = {
                                    if (currentRoute == getDirection(tab)) {
                                        state.scrollToTopRegistry.scrollToTop()
                                    } else {
                                        state.navigate(getDirection(tab))
                                    }
                                },
                                icon = {
                                    TabIcon(tabItem = tab)
                                },
                                label = {
                                    TabTitle(
                                        title = tab.metaData.title,
                                    )
                                },
                                badge =
                                    if (tab is AllNotificationTabItem) {
                                        {
                                            if (state.notificationState.count > 0) {
                                                Badge {
                                                    Text(text = state.notificationState.count.toString())
                                                }
                                            }
                                        }
                                    } else {
                                        null
                                    },
                                onLongClick =
                                    if (tab is HomeTimelineTabItem || tab is ProfileTabItem) {
                                        {
                                            hapticFeedback.performHapticFeedback(
                                                HapticFeedbackType.LongPress,
                                            )
                                            state.navigate(Route.AccountSelection)
                                        }
                                    } else {
                                        null
                                    },
                            )
                        }
                    },
                    secondaryItems = {
                        if (layoutType != NavigationSuiteType.NavigationBar) {
                            item(
                                selected = currentRoute is Route.DraftBox,
                                onClick = {
                                    state.navigate(Route.DraftBox)
                                },
                                icon = {
                                    FAIcon(
                                        imageVector = FontAwesomeIcons.Solid.PenToSquare,
                                        contentDescription = stringResource(id = R.string.draft_box_title),
                                    )
                                },
                                label = {
                                    Text(text = stringResource(id = R.string.draft_box_title))
                                },
                            )
                            item(
                                selected = currentRoute is Route.Rss.Sources,
                                onClick = {
                                    state.navigate(Route.Rss.Sources)
                                },
                                icon = {
                                    FAIcon(
                                        imageVector = FontAwesomeIcons.Solid.SquareRss,
                                        contentDescription = stringResource(id = R.string.settings_rss_management_title),
                                    )
                                },
                                label = {
                                    Text(text = stringResource(id = R.string.settings_rss_management_title))
                                },
                            )
                            item(
                                selected = currentRoute is Route.Settings.LocalHistory,
                                onClick = {
                                    state.navigate(Route.Settings.LocalHistory)
                                },
                                icon = {
                                    FAIcon(
                                        imageVector = FontAwesomeIcons.Solid.ClockRotateLeft,
                                        contentDescription = stringResource(id = R.string.settings_local_history_title),
                                    )
                                },
                                label = {
                                    Text(text = stringResource(id = R.string.settings_local_history_title))
                                },
                            )
                        }
                        state.secondaryTabsState.onSuccess { secondaryTabs ->
                            secondaryTabs.forEach { item ->
                                expandableItem(
                                    icon = {
                                        item.user.onSuccess {
                                            AvatarComponent(it.avatar)
                                        }
                                    },
                                    label = {
                                        Column {
                                            item.user.onSuccess {
                                                RichText(it.name)
                                                Text(
                                                    it.handle.canonical,
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            }
                                        }
                                    },
                                    children = {
                                        item.tabs.forEach {
                                            item(
                                                selected = currentRoute == getDirection(it),
                                                onClick = {
                                                    if (currentRoute == getDirection(it)) {
                                                        state.scrollToTopRegistry.scrollToTop()
                                                    } else {
                                                        state.navigate(getDirection(it))
                                                    }
                                                },
                                                icon = {
                                                    TabIcon(
                                                        tabItem = it,
                                                        iconOnly = true,
                                                    )
                                                },
                                                label = {
                                                    TabTitle(
                                                        title = it.metaData.title,
                                                    )
                                                },
                                                badge =
                                                    if (it is AllNotificationTabItem) {
                                                        {
                                                            if (state.notificationState.count > 0) {
                                                                Badge {
                                                                    Text(text = state.notificationState.count.toString())
                                                                }
                                                            }
                                                        }
                                                    } else {
                                                        null
                                                    },
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    },
                    footerItems = {
                        if (layoutType == NavigationSuiteType.NavigationBar) {
                            item(
                                selected = currentRoute is Route.DraftBox,
                                onClick = {
                                    state.navigate(Route.DraftBox)
                                },
                                icon = {
                                    FAIcon(
                                        imageVector = FontAwesomeIcons.Solid.PenToSquare,
                                        contentDescription = stringResource(id = R.string.draft_box_title),
                                    )
                                },
                                label = {
                                    Text(text = stringResource(id = R.string.draft_box_title))
                                },
                            )
                            item(
                                selected = currentRoute is Route.Rss.Sources,
                                onClick = {
                                    state.navigate(Route.Rss.Sources)
                                },
                                icon = {
                                    FAIcon(
                                        imageVector = FontAwesomeIcons.Solid.SquareRss,
                                        contentDescription = stringResource(id = R.string.settings_rss_management_title),
                                    )
                                },
                                label = {
                                    Text(text = stringResource(id = R.string.settings_rss_management_title))
                                },
                            )
                            item(
                                selected = currentRoute is Route.Settings.LocalHistory,
                                onClick = {
                                    state.navigate(Route.Settings.LocalHistory)
                                },
                                icon = {
                                    FAIcon(
                                        imageVector = FontAwesomeIcons.Solid.ClockRotateLeft,
                                        contentDescription = stringResource(id = R.string.settings_local_history_title),
                                    )
                                },
                                label = {
                                    Text(text = stringResource(id = R.string.settings_local_history_title))
                                },
                            )
                        }
                        item(
                            selected = currentRoute is Route.Settings.Main,
                            onClick = {
                                state.navigate(Route.Settings.Main)
                            },
                            icon = {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.Gear,
                                    contentDescription = stringResource(id = R.string.settings_title),
                                )
                            },
                            label = {
                                Text(text = stringResource(id = R.string.settings_title))
                            },
                        )
                    },
                ) {
                    CompositionLocalProvider(
                        LocalUriHandler provides
                            remember {
                                object : UriHandler {
                                    override fun openUri(uri: String) {
                                        state.deeplinkPresenter.handle(uri)
                                    }
                                }
                            },
                        LocalScrollToTopRegistry provides state.scrollToTopRegistry,
                    ) {
                        Router(
                            backStack =
                                state.topLevelBackStack.takeSuccess()?.backStack
                                    ?: remember { androidx.compose.runtime.mutableStateListOf(currentRoute) },
                            navigationState = state.navigationState,
                            openDrawer = {
                                state.openDrawer()
                            },
                            navigate = state::navigate,
                            onBack = state::goBack,
                        )
                    }
                }
                InAppNotificationComponent(
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }.onLoading {
            SplashScreen()
        }
}

private fun getDirection(tab: TabItem): Route =
    when (tab) {
        is DiscoverTabItem -> Route.Discover
        is ProfileTabItem -> Route.Profile.Me(tab.account)
        HomeTabItem -> Route.Home
        is TimelineTabItem -> Route.Timeline(tab)
        is NotificationTabItem, AllNotificationTabItem -> Route.Notification
        SettingsTabItem -> Route.Settings.Main
        is AllListTabItem -> Route.Lists.List(tab.account)
        is Bluesky.FeedsTabItem -> Route.Bluesky.Feed(tab.account)
        is DirectMessageTabItem -> Route.DM.List(tab.account)
        is RssTabItem -> Route.Rss.Sources
        is Misskey.AntennasListTabItem -> Route.Misskey.AntennasList(tab.account)
        is Misskey.ChannelListTabItem -> Route.Misskey.ChannelList(tab.account)
    }

@Composable
private fun presenter(uriHandler: UriHandler) =
    run {
        val secondaryTabsPresenter = remember { SecondaryTabsPresenter() }.invoke()
        val loggedInState = remember { LoggedInPresenter() }.invoke()
        val navigationState =
            remember {
                NavigationState()
            }
        val wideNavigationRailState = rememberWideNavigationRailState()
        val tabs =
            remember {
                HomeTabsPresenter()
            }.invoke()
        val scrollToTopRegistry =
            remember {
                ScrollToTopRegistry()
            }
        val notificationState =
            remember {
                AllNotificationBadgePresenter()
            }.invoke()
        val firstDirection =
            remember(tabs.tabs) {
                tabs.tabs.map {
                    getDirection(it.all.first())
                }
            }
        val topLevelBackStack =
            remember(firstDirection) {
                firstDirection.map {
                    TopLevelBackStack(it)
                }
            }
        val topLevelRoutes =
            remember(tabs.tabs) {
                tabs.tabs.map { state ->
                    state.all.map { getDirection(it) }.toSet() +
                        setOf(
                            Route.Settings.Main,
                            Route.DraftBox,
                            Route.Rss.Sources,
                            Route.Settings.LocalHistory,
                        )
                }
            }
        val scope = rememberCoroutineScope()
        val deeplinkPresenter =
            remember(topLevelBackStack, topLevelRoutes) {
                DeepLinkPresenter(
                    onRoute = {
                        val route = Route.from(it)
                        if (route != null) {
                            navigate(
                                route = route,
                                topLevelBackStack = topLevelBackStack.takeSuccess(),
                                topLevelRoutes = topLevelRoutes.takeSuccess(),
                                wideNavigationRailState = wideNavigationRailState,
                                scope = scope,
                            )
                        }
                    },
                    onLink = {
                        uriHandler.openUri(it)
                    },
                )
            }.invoke()
        object {
            val secondaryTabsState = secondaryTabsPresenter.items
            val notificationState = notificationState
            val tabs = tabs.tabs
            val navigationState = navigationState
            val scrollToTopRegistry = scrollToTopRegistry
            val deeplinkPresenter = deeplinkPresenter
            val topLevelBackStack = topLevelBackStack
            val wideNavigationRailState = wideNavigationRailState
            val loggedInState = loggedInState.isLoggedIn

            fun navigate(route: Route) {
                navigate(
                    route = route,
                    topLevelBackStack = topLevelBackStack.takeSuccess(),
                    topLevelRoutes = topLevelRoutes.takeSuccess(),
                    wideNavigationRailState = wideNavigationRailState,
                    scope = scope,
                )
            }

            fun goBack() {
                topLevelBackStack.takeSuccess()?.removeLast()
            }

            fun openDrawer() {
                scope.launch {
                    wideNavigationRailState.toggle()
                }
            }
        }
    }

private fun navigate(
    route: Route,
    topLevelBackStack: TopLevelBackStack<Route>?,
    topLevelRoutes: Set<Route>?,
    wideNavigationRailState: WideNavigationRailState,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    if (topLevelBackStack == null) return
    if (topLevelRoutes?.contains(route) == true) {
        topLevelBackStack.addTopLevel(route)
    } else {
        topLevelBackStack.add(route)
    }
    scope.launch {
        wideNavigationRailState.collapse()
    }
}

@Composable
private fun userPresenter(accountType: AccountType) =
    run {
        remember(accountType) { UserPresenter(accountType, null) }.invoke().user
    }

internal class NavigationState {
    private val bottomBarAutoHideState = mutableStateOf(true)
    private val bottomBarDividerState = mutableStateOf(true)
    val bottomBarAutoHideEnabled: Boolean
        get() = bottomBarAutoHideState.value
    val bottomBarDividerEnabled: Boolean
        get() = bottomBarDividerState.value

    fun enableBottomBarAutoHide() {
        bottomBarAutoHideState.value = true
    }

    fun disableBottomBarAutoHide() {
        bottomBarAutoHideState.value = false
    }

    fun showBottomBarDivider() {
        bottomBarDividerState.value = true
    }

    fun hideBottomBarDivider() {
        bottomBarDividerState.value = false
    }
}

private class ScrollToTopRegistry {
    private val callbacks = mutableSetOf<() -> Unit>()

    fun registerCallback(callback: () -> Unit) {
        callbacks.add(callback)
    }

    fun unregisterCallback(callback: () -> Unit) {
        callbacks.remove(callback)
    }

    fun scrollToTop() {
        callbacks.forEach { it.invoke() }
    }
}

private val LocalScrollToTopRegistry =
    androidx.compose.runtime.staticCompositionLocalOf<ScrollToTopRegistry?> {
        null
    }

@Composable
internal fun RegisterTabCallback(
    lazyListState: LazyStaggeredGridState,
    onRefresh: () -> Unit,
) {
    val onRefreshState by rememberUpdatedState(onRefresh)
    val tabState = LocalScrollToTopRegistry.current
    if (tabState != null) {
        val scope = rememberCoroutineScope()
        val callback: () -> Unit =
            remember(lazyListState, scope) {
                {
                    if (lazyListState.firstVisibleItemIndex == 0 &&
                        lazyListState.firstVisibleItemScrollOffset == 0
                    ) {
                        onRefreshState.invoke()
                    } else {
                        scope.launch {
                            if (lazyListState.firstVisibleItemIndex > 20) {
                                lazyListState.scrollToItem(0)
                            } else {
                                lazyListState.animateScrollToItem(0)
                            }
                        }
                    }
                }
            }
        DisposableEffect(tabState, callback, lazyListState) {
            tabState.registerCallback(callback)
            onDispose {
                tabState.unregisterCallback(callback)
            }
        }
    }
}
