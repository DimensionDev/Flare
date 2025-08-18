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
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Gear
import compose.icons.fontawesomeicons.solid.Pen
import compose.icons.fontawesomeicons.solid.UserPlus
import dev.dimension.flare.data.model.AllListTabItem
import dev.dimension.flare.data.model.Bluesky
import dev.dimension.flare.data.model.DirectMessageTabItem
import dev.dimension.flare.data.model.DiscoverTabItem
import dev.dimension.flare.data.model.Misskey
import dev.dimension.flare.data.model.NotificationTabItem
import dev.dimension.flare.data.model.ProfileTabItem
import dev.dimension.flare.data.model.RssTabItem
import dev.dimension.flare.data.model.SettingsTabItem
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TabSettings
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
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
import dev.dimension.flare.ui.route.Route.AllLists
import dev.dimension.flare.ui.route.Route.BlueskyFeeds
import dev.dimension.flare.ui.route.Route.DirectMessage
import dev.dimension.flare.ui.route.Route.Discover
import dev.dimension.flare.ui.route.Route.MeRoute
import dev.dimension.flare.ui.route.Route.Notification
import dev.dimension.flare.ui.route.Route.Timeline
import dev.dimension.flare.ui.route.Router
import dev.dimension.flare.ui.route.StackManager
import dev.dimension.flare.ui.route.rememberStackManager
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Badge
import io.github.composefluent.component.BadgeStatus
import io.github.composefluent.component.Button
import io.github.composefluent.component.Icon
import io.github.composefluent.component.MenuItemSeparator
import io.github.composefluent.component.NavigationDefaults
import io.github.composefluent.component.NavigationDisplayMode
import io.github.composefluent.component.NavigationView
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text
import io.github.composefluent.component.menuItem
import io.github.composefluent.component.rememberNavigationState
import io.ktor.http.Url
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FlareApp(
    onRawImage: (String) -> Unit,
    onStatusMedia: (AccountType, MicroBlogKey, Int) -> Unit,
) {
    val state by producePresenter { presenter() }
    val bigScreen = isBigScreen()
    val displayMode =
        if (bigScreen) {
            NavigationDisplayMode.Left
        } else {
            NavigationDisplayMode.LeftCompact
        }
    var selectedIndex by remember { mutableStateOf(0) }
    val uriHandler = LocalUriHandler.current

    state.tabs.onSuccess { tabs ->
        val stackManager =
            rememberStackManager(startRoute = getRoute(tabs.primary.first().tabItem), key = tabs)
        val currentRoute =
            remember(stackManager.stack) {
                stackManager.current
            }

        fun navigate(route: Route) {
            stackManager.push(route)
        }

        fun goBack() {
            stackManager.pop()
        }

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
            backButton = {
                NavigationDefaults.BackButton(
                    onClick = {
                        goBack()
                    },
                    disabled = !stackManager.canGoBack,
                )
            },
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
                                            Text(
                                                user.handle,
                                                style = FluentTheme.typography.caption,
                                                maxLines = 1,
                                            )
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
                        selected = currentRoute == getRoute(tab.tabItem),
                        onClick = {
                            if (selectedIndex == index) {
                                state.scrollToTopRegistry.scrollToTop()
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
                    selected = currentRoute == Route.Settings,
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
                        ProxyUriHandler(
                            stackManager = stackManager,
                            actualUriHandler = uriHandler,
                            onRawImage = onRawImage,
                            onStatusMedia = onStatusMedia,
                        )
                    },
                LocalScrollToTopRegistry provides state.scrollToTopRegistry,
            ) {
                Router(
                    manager = stackManager,
                )
            }
        }
    }
}

private fun getRoute(tab: TabItem): Route =
    when (tab) {
        is DiscoverTabItem -> Discover(tab.account)
        is ProfileTabItem -> MeRoute(tab.account)
        is TimelineTabItem -> Timeline(tab)
        is NotificationTabItem -> Notification(tab.account)
        SettingsTabItem -> Route.Settings
        is AllListTabItem -> AllLists(tab.account)
        is Bluesky.FeedsTabItem -> BlueskyFeeds(tab.account)
        is DirectMessageTabItem -> DirectMessage(tab.account)
        is RssTabItem -> Route.Rss
        is Misskey.AntennasListTabItem -> Route.Rss
    }

@Composable
private fun presenter() =
    run {
        val accountState = remember { ActiveAccountPresenter() }.invoke()
        val tabState = remember { HomeTabsPresenter(flowOf(TabSettings())) }.invoke()
        val scrollToTopRegistry =
            remember {
                ScrollToTopRegistry()
            }
        object : UserState by accountState, HomeTabsPresenter.State by tabState {
            val scrollToTopRegistry = scrollToTopRegistry
        }
    }

private class ProxyUriHandler(
    private val stackManager: StackManager,
    private val actualUriHandler: UriHandler,
    private val onRawImage: (String) -> Unit,
    private val onStatusMedia: (AccountType, MicroBlogKey, Int) -> Unit,
) : UriHandler {
    override fun openUri(uri: String) {
        if (uri.startsWith("flare://")) {
            val data = Url(uri)
            when (data.host) {
                "RawImage" -> {
                    val rawImage = data.segments.getOrNull(0)
                    if (rawImage != null) {
                        onRawImage(rawImage)
                    }
                }

                "StatusMedia" -> {
                    val accountKey = data.parameters["accountKey"]?.let(MicroBlogKey::valueOf)
                    val statusKey = data.segments.getOrNull(0)?.let(MicroBlogKey::valueOf)
                    val index = data.segments.getOrNull(1)?.toIntOrNull()
                    val accountType = accountKey?.let(AccountType::Specific) ?: AccountType.Guest
                    if (statusKey != null && index != null) {
                        onStatusMedia(accountType, statusKey, index)
                    }
                }

                else -> {
                    Route.parse(uri)?.let {
                        stackManager.push(it)
                    } ?: run {
                        // If the URI does not match any known route, we can handle it as a custom URI scheme
                        // For example, you might want to log it or show an error
                        println("Unhandled URI: $uri")
                    }
                }
            }
        } else {
            actualUriHandler.openUri(uri)
        }
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
