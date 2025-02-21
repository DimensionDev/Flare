package dev.dimension.flare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.konyaco.fluent.FluentTheme
import com.konyaco.fluent.component.Badge
import com.konyaco.fluent.component.BadgeStatus
import com.konyaco.fluent.component.Button
import com.konyaco.fluent.component.Icon
import com.konyaco.fluent.component.MenuItemSeparator
import com.konyaco.fluent.component.NavigationDisplayMode
import com.konyaco.fluent.component.NavigationView
import com.konyaco.fluent.component.SubtleButton
import com.konyaco.fluent.component.Text
import com.konyaco.fluent.component.menuItem
import com.konyaco.fluent.component.rememberNavigationState
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Gear
import compose.icons.fontawesomeicons.solid.Pen
import compose.icons.fontawesomeicons.solid.UserPlus
import dev.dimension.flare.data.model.AllListTabItem
import dev.dimension.flare.data.model.Bluesky
import dev.dimension.flare.data.model.DirectMessageTabItem
import dev.dimension.flare.data.model.DiscoverTabItem
import dev.dimension.flare.data.model.NotificationTabItem
import dev.dimension.flare.data.model.ProfileTabItem
import dev.dimension.flare.data.model.RssTabItem
import dev.dimension.flare.data.model.SettingsTabItem
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TabSettings
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.component.TabIcon
import dev.dimension.flare.ui.component.TabTitle
import dev.dimension.flare.ui.component.platform.isBigScreen
import dev.dimension.flare.ui.model.isSuccess
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.HomeTabsPresenter
import dev.dimension.flare.ui.presenter.home.ActiveAccountPresenter
import dev.dimension.flare.ui.presenter.home.UserState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.route.Route
import dev.dimension.flare.ui.route.Router
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FlareApp(navController: NavHostController = rememberNavController()) {
    val state by producePresenter { presenter() }
    val bigScreen = isBigScreen()
    val displayMode =
        if (bigScreen) {
            NavigationDisplayMode.Left
        } else {
            NavigationDisplayMode.LeftCompact
        }
    var selectedIndex by remember { mutableStateOf(0) }
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentEntry?.destination
    val uriHandler = LocalUriHandler.current

    fun navigate(route: Route) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }
    state.tabs.onSuccess { tabs ->
        LaunchedEffect(selectedIndex) {
            val tab = tabs.all[selectedIndex]
            navigate(getRoute(tab.tabItem))
        }
        val navigationState = rememberNavigationState()
        LaunchedEffect(bigScreen) {
            navigationState.expanded = bigScreen
        }
        NavigationView(
            state = navigationState,
            displayMode = displayMode,
            menuItems = {
                state.user
                    .onSuccess { user ->
                        item {
                            SubtleButton(
                                onClick = {
                                },
                            ) {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AvatarComponent(
                                        data = user.avatar,
                                        modifier =
                                            Modifier
                                                .aspectRatio(1f),
                                    )
                                    if (navigationState.expanded) {
                                        Column {
                                            RichText(user.name, maxLines = 1)
                                            Text(user.handle, style = FluentTheme.typography.caption, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            Button(
                                onClick = {},
                                modifier =
                                    Modifier
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                        .fillMaxWidth(),
                            ) {
                                Icon(
                                    FontAwesomeIcons.Solid.Pen,
                                    contentDescription = stringResource(Res.string.home_compose),
                                    modifier = Modifier.size(16.dp),
                                )
                                if (navigationState.expanded) {
                                    Text(stringResource(Res.string.home_compose), maxLines = 1)
                                }
                            }
                        }
                    }.onError {
                        item {
                            Button(
                                onClick = {
                                    navigate(Route.ServiceSelect)
                                },
                                modifier =
                                    Modifier
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                        .fillMaxWidth(),
                            ) {
                                Icon(
                                    FontAwesomeIcons.Solid.UserPlus,
                                    contentDescription = stringResource(Res.string.home_login),
                                    modifier = Modifier.size(16.dp),
                                )
                                if (navigationState.expanded) {
                                    Text(stringResource(Res.string.home_login), maxLines = 1)
                                }
                            }
                        }
                    }
                item {
                    MenuItemSeparator()
                }

                fun buildMenuItem(
                    tab: HomeTabsPresenter.State.HomeTabState.HomeTabItem,
                    index: Int,
                ) {
                    menuItem(
                        selected = currentDestination?.hierarchy?.any { it.hasRoute(getRoute(tab.tabItem)::class) } == true,
                        onClick = {
                            if (selectedIndex == index) {
                                tab.tabState.onClick()
                            } else {
                                selectedIndex = index
                            }
                        },
                        icon = {
                            TabIcon(
                                tab.tabItem,
                                iconOnly = tabs.secondaryIconOnly,
                            )
                        },
                        text = {
                            TabTitle(tab.tabItem.metaData.title)
                        },
                        badge =
                            if (tab.badgeCountState.isSuccess) {
                                {
                                    tab.badgeCountState.onSuccess { count ->
                                        if (count > 0) {
                                            Badge(
                                                status = BadgeStatus.Attention,
                                                content = { Text(count.toString()) },
                                            )
                                        }
                                    }
                                }
                            } else {
                                null
                            },
                    )
                }
                tabs.primary.forEachIndexed { index, tab ->
                    if (tab.tabItem !is SettingsTabItem) {
                        buildMenuItem(tab, index)
                    }
                }
                if (tabs.secondary.any()) {
                    item {
                        MenuItemSeparator()
                    }
                    tabs.secondary.forEachIndexed { index, tab ->
                        buildMenuItem(tab, index + tabs.primary.size)
                    }
                }
            },
            title = {
                Text(stringResource(Res.string.app_name))
            },
            contentPadding = PaddingValues(top = 8.dp),
            footerItems = {
                menuItem(
                    selected = currentDestination?.hierarchy?.any { it.hasRoute(Route.Settings::class) } == true,
                    onClick = {
                        navigate(Route.Settings)
                    },
                    icon = {
                        Icon(
                            FontAwesomeIcons.Solid.Gear,
                            contentDescription = stringResource(Res.string.home_settings),
                        )
                    },
                    text = {
                        Text(stringResource(Res.string.home_settings))
                    },
                )
            },
        ) {
            val currentTab =
                remember(tabs, selectedIndex) {
                    tabs.all.getOrNull(selectedIndex)
                }
            CompositionLocalProvider(
                LocalUriHandler provides
                    remember {
                        ProxyUriHandler(navController, uriHandler)
                    },
                LocalTabState provides currentTab?.tabState,
            ) {
                Router(
                    startDestination = getRoute(tabs.primary.first().tabItem),
                    navController = navController,
                )
            }
        }
    }
}

private fun getRoute(tab: TabItem): Route =
    when (tab) {
        is DiscoverTabItem -> Route.Discover(tab.account)
        is ProfileTabItem -> Route.MeRoute(tab.account)
        is TimelineTabItem -> Route.Timeline(tab)
        is NotificationTabItem -> Route.Notification(tab.account)
        SettingsTabItem -> Route.Settings
        is AllListTabItem -> Route.AllLists(tab.account)
        is Bluesky.FeedsTabItem -> Route.BlueskyFeeds(tab.account)
        is DirectMessageTabItem -> Route.DirectMessage(tab.account)
        is RssTabItem -> Route.Rss
    }

@Composable
private fun presenter() =
    run {
        val accountState = remember { ActiveAccountPresenter() }.invoke()
        val tabState = remember { HomeTabsPresenter(flowOf(TabSettings())) }.invoke()
        object : UserState by accountState, HomeTabsPresenter.State by tabState {
        }
    }

private class ProxyUriHandler(
    private val navController: NavController,
    private val actualUriHandler: UriHandler,
) : UriHandler {
    override fun openUri(uri: String) {
        if (uri.startsWith("flare://")) {
            navController.navigate(uri)
        } else {
            actualUriHandler.openUri(uri)
        }
    }
}

private val LocalTabState =
    androidx.compose.runtime.staticCompositionLocalOf<HomeTabsPresenter.State.HomeTabState.HomeTabItem.TabState?> {
        null
    }

@Composable
internal fun RegisterTabCallback(
    lazyListState: LazyStaggeredGridState,
    onRefresh: () -> Unit,
) {
    val tabState = LocalTabState.current
    val onRefreshState by rememberUpdatedState(onRefresh)
    if (tabState != null) {
        val scope = rememberCoroutineScope()
        val callback: () -> Unit =
            remember(lazyListState, scope) {
                {
                    if (lazyListState.firstVisibleItemIndex == 0) {
                        onRefreshState()
                    } else {
                        scope.launch {
                            if (lazyListState.firstVisibleItemIndex > 40) {
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
