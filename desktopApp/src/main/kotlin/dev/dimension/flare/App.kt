package dev.dimension.flare

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowScope
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Bars
import compose.icons.fontawesomeicons.solid.Bell
import compose.icons.fontawesomeicons.solid.Gear
import compose.icons.fontawesomeicons.solid.House
import compose.icons.fontawesomeicons.solid.MagnifyingGlass
import compose.icons.fontawesomeicons.solid.Pen
import compose.icons.fontawesomeicons.solid.UserPlus
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScrollBar
import dev.dimension.flare.ui.component.InAppNotificationComponent
import dev.dimension.flare.ui.component.LocalGlobalAppearance
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.component.toImageVector
import dev.dimension.flare.ui.model.asText
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.HomeTabsPresenter
import dev.dimension.flare.ui.presenter.home.AllNotificationBadgePresenter
import dev.dimension.flare.ui.presenter.home.CanComposePresenter
import dev.dimension.flare.ui.presenter.home.DeepLinkPresenter
import dev.dimension.flare.ui.presenter.home.LoggedInPresenter
import dev.dimension.flare.ui.presenter.home.LoggedInState
import dev.dimension.flare.ui.presenter.home.SecondaryTabsPresenter
import dev.dimension.flare.ui.presenter.home.UserState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.route.Route
import dev.dimension.flare.ui.route.Router
import dev.dimension.flare.ui.route.TopLevelBackStack
import io.github.composefluent.FluentTheme
import io.github.composefluent.background.Layer
import io.github.composefluent.component.Badge
import io.github.composefluent.component.Button
import io.github.composefluent.component.CardExpanderItem
import io.github.composefluent.component.Expander
import io.github.composefluent.component.FlyoutContainer
import io.github.composefluent.component.FlyoutPlacement
import io.github.composefluent.component.Icon
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun WindowScope.FlareApp(backButtonState: NavigationBackButtonState) {
    val uriHandler = LocalUriHandler.current
    val state by producePresenter { presenter(uriHandler = uriHandler) }
    state.tabs.onSuccess { tabs ->
        state.topLevelBackStack.onSuccess { topLevelBackStack ->
            LaunchedEffect(topLevelBackStack) {
                backButtonState.attach {
                    state.goBack()
                }
            }

            LaunchedEffect(topLevelBackStack.canGoBack) {
                backButtonState.update(topLevelBackStack.canGoBack)
            }
        }
        val currentRoute = state.topLevelBackStack.takeSuccess()?.currentRoute
        val showNavigationLabels = LocalGlobalAppearance.current.showBottomBarLabels

        Row {
            Column(
                modifier =
                    Modifier
                        .background(
                            FluentTheme.colors.background.mica.base,
                        ).fillMaxHeight()
                        .width(72.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(top = LocalWindowPadding.current.calculateTopPadding()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                state.isLoggedIn
                    .onSuccess { loggedIn ->
                        if (!loggedIn) {
                            Button(
                                onClick = {
                                    state.navigate(Route.ServiceSelect)
                                },
                                modifier =
                                    Modifier
                                        .padding(vertical = 4.dp)
                                        .fillMaxWidth(),
                            ) {
                                Column(
                                    modifier =
                                        Modifier
                                            .padding(vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        FontAwesomeIcons.Solid.UserPlus,
                                        contentDescription = stringResource(Res.string.home_login),
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(stringResource(Res.string.home_login), maxLines = 1)
                                }
                            }
                        } else {
                            FlyoutContainer(
                                flyout = {
                                    val scrollableState = rememberScrollState()
                                    FlareScrollBar(
                                        state = scrollableState,
                                    ) {
                                        Column(
                                            modifier =
                                                Modifier
                                                    .widthIn(
                                                        max = 320.dp,
                                                    ).heightIn(
                                                        max = 600.dp,
                                                    ).verticalScroll(scrollableState),
                                        ) {
                                            state.items.onSuccess { items ->
                                                items.forEach { item ->
                                                    item.user.onSuccess { user ->
                                                        var isSubMenuExpanded by remember {
                                                            mutableStateOf(
                                                                false,
                                                            )
                                                        }
                                                        Expander(
                                                            expanded = isSubMenuExpanded,
                                                            onExpandedChanged = {
                                                                isSubMenuExpanded = it
                                                            },
                                                            heading = {
                                                                RichText(
                                                                    text = user.name,
                                                                    maxLines = 1,
                                                                )
                                                            },
                                                            caption = {
                                                                Text(
                                                                    text = user.handle.canonical,
                                                                    maxLines = 1,
                                                                )
                                                            },
                                                            icon = {
                                                                AvatarComponent(
                                                                    data = user.avatar,
                                                                    modifier =
                                                                        Modifier
                                                                            .aspectRatio(1f),
                                                                    size = 24.dp,
                                                                )
                                                            },
                                                        ) {
                                                            item.tabs.forEach { shortcut ->
                                                                CardExpanderItem(
                                                                    onClick = {
                                                                        state.navigate(shortcut)
                                                                        isFlyoutVisible = false
                                                                    },
                                                                    heading = {
                                                                        dev.dimension.flare.ui.component.Text(
                                                                            shortcut.title.asText(),
                                                                        )
                                                                    },
                                                                    icon = {
                                                                        FAIcon(
                                                                            imageVector = shortcut.icon.toImageVector(),
                                                                            contentDescription = null,
                                                                            modifier =
                                                                                Modifier.size(
                                                                                    16.dp,
                                                                                ),
                                                                        )
                                                                    },
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                placement = FlyoutPlacement.EndAlignedTop,
                                adaptivePlacement = true,
                            ) {
                                state.user
                                    .onSuccess {
                                        Box(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth(),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            AvatarComponent(
                                                data = it.avatar,
                                                modifier =
                                                    Modifier
                                                        .clickable {
                                                            isFlyoutVisible = !isFlyoutVisible
                                                        }.aspectRatio(1f),
                                            )
                                        }
                                    }.onLoading {
                                        SubtleButton(
                                            onClick = {
                                                isFlyoutVisible = !isFlyoutVisible
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            FAIcon(
                                                imageVector = FontAwesomeIcons.Solid.Bars,
                                                contentDescription = stringResource(Res.string.home_settings),
//                                        modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }.onError {
                                        SubtleButton(
                                            onClick = {
                                                isFlyoutVisible = !isFlyoutVisible
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            FAIcon(
                                                imageVector = FontAwesomeIcons.Solid.Bars,
                                                contentDescription = stringResource(Res.string.home_settings),
//                                        modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                        }
//                        SubtleButton(
//                            onClick = {
// //                                state.navigate(Route.MeRoute(AccountType.Specific(user.key)))
//                            },
//                        ) {
//                            Column(
//                                modifier =
//                                    Modifier
//                                        .fillMaxWidth(),
//                                horizontalAlignment = Alignment.CenterHorizontally,
//                            ) {
//                            }
//                        }
//                        FlyoutContainer(
//                            flyout = {
//                                Box(
//                                    modifier = Modifier.size(200.dp)
//                                )
//                            },
//                            placement = FlyoutPlacement.EndAlignedTop,
//                            adaptivePlacement = true,
//                        ) {
//                            Box(
//                                modifier =
//                                    Modifier
//                                        .fillMaxWidth(),
//                                contentAlignment = Alignment.Center,
//                            ) {
//                                AvatarComponent(
//                                    data = user.avatar,
//                                    modifier =
//                                        Modifier
//                                            .clickable {
//                                                isFlyoutVisible = !isFlyoutVisible
//                                            }
//                                            .aspectRatio(1f),
//                                )
//                            }
//                        }
//                        Spacer(modifier = Modifier.height(8.dp))
//                        Button(
//                            onClick = {
//                                state.navigate(
//                                    Route.Compose.New,
//                                )
//                            },
//                            modifier =
//                                Modifier
//                                    .padding(horizontal = 8.dp, vertical = 4.dp)
//                                    .fillMaxWidth(),
//                            iconOnly = true,
//                        ) {
//                            Icon(
//                                FontAwesomeIcons.Solid.Pen,
//                                contentDescription = stringResource(Res.string.home_compose),
//                                modifier = Modifier.size(16.dp),
//                            )
//                        }
                    }

                @Composable
                fun buildMenuItem(tab: HomeTabsPresenter.State.HomeTabs) {
                    val selected = currentRoute == getRoute(tab)
                    val color by animateColorAsState(
                        targetValue =
                            if (selected) {
                                FluentTheme.colors.fillAccent.secondary
                            } else {
                                FluentTheme.colors.system.neutral
                            },
                    )
                    NavigationItem(
                        onClick = {
                            if (selected) {
                                state.scrollToTopRegistry.scrollToTop()
                            } else {
                                state.navigate(getRoute(tab))
                            }
                        },
                        icon = {
                            FAIcon(
                                imageVector = tab.icon,
                                contentDescription = stringResource(tab.title),
                                tint = color,
                                modifier =
                                    Modifier
                                        .size(24.dp),
                            )
                        },
                        text =
                            if (showNavigationLabels) {
                                {
                                    Text(
                                        stringResource(tab.title),
                                        maxLines = 1,
                                        color = color,
                                        style = FluentTheme.typography.caption,
                                    )
                                }
                            } else {
                                null
                            },
                        badge =
                            if (tab == HomeTabsPresenter.State.HomeTabs.Notifications) {
                                if (state.notificationState.count > 0) {
                                    {
                                        Text(state.notificationState.count.toString())
                                    }
                                } else {
                                    null
                                }
                            } else {
                                null
                            },
                    )
                }
                tabs.forEach { tab ->
                    buildMenuItem(tab)
                }
                val selected = currentRoute == Route.Settings
                val color by animateColorAsState(
                    targetValue =
                        if (selected) {
                            FluentTheme.colors.fillAccent.secondary
                        } else {
                            FluentTheme.colors.system.neutral
                        },
                )
                if (state.canComposeState.takeSuccess() == true) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier =
                            Modifier
                                .shadow(4.dp, CircleShape)
                                .background(
                                    FluentTheme.colors.fillAccent.secondary,
                                    CircleShape,
                                ).fillMaxWidth(0.66f)
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .clickable {
                                    state.navigate(
                                        Route.Compose.New,
                                    )
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            FontAwesomeIcons.Solid.Pen,
                            contentDescription = stringResource(Res.string.home_compose),
                            modifier = Modifier.size(16.dp),
                            tint = FluentTheme.colors.text.onAccent.primary,
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                NavigationItem(
                    icon = {
                        Icon(
                            FontAwesomeIcons.Solid.Gear,
                            contentDescription = stringResource(Res.string.home_settings),
                            modifier = Modifier.size(24.dp),
                            tint = color,
                        )
                    },
                    text =
                        if (showNavigationLabels) {
                            {
                                Text(
                                    stringResource(Res.string.home_settings),
                                    maxLines = 1,
                                    style = FluentTheme.typography.caption,
                                    color = color,
                                )
                            }
                        } else {
                            null
                        },
                    onClick = {
                        state.navigate(Route.Settings)
                    },
                )
            }
//            CommandBarSeparator()
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
                Layer(
                    modifier = Modifier.fillMaxSize(),
                    color = FluentTheme.colors.background.mica.base,
                    shape = RoundedCornerShape(0),
                    border = null,
                ) {
                    Box {
                        Router(
                            backStack =
                                state.topLevelBackStack.takeSuccess()?.stack
                                    ?: persistentListOf(),
                            navigate = { route -> state.navigate(route) },
                            onBack = { state.goBack() },
                        )
                        Spacer(
                            modifier =
                                Modifier
                                    .fillMaxHeight()
                                    .width(1.dp)
                                    .background(FluentTheme.colors.stroke.divider.default)
                                    .align(Alignment.CenterStart),
                        )
                        InAppNotificationComponent(
                            modifier =
                                Modifier
                                    .align(Alignment.TopCenter),
                        )
                    }
                }
            }
        }
    }
}

private fun getDirection(data: SecondaryTabsPresenter.Tab): Route? =
    when (val target = data.destination) {
        is SecondaryTabsPresenter.Destination.Route -> Route.from(target.route)
        is SecondaryTabsPresenter.Destination.Timeline -> Route.Timeline(target.tabItem)
    }

@Composable
private fun NavigationItem(
    icon: @Composable () -> Unit,
    text: (@Composable () -> Unit)?,
    badge: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SubtleButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .let {
                        if (text != null) {
                            it
                        } else {
                            it.height(40.dp)
                        }
                    },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.spacedBy(
                    if (text != null) 4.dp else 0.dp,
                    Alignment.CenterVertically,
                ),
        ) {
            NavigationItemIcon(
                icon = icon,
                badge = badge,
            )
            text?.invoke()
        }
    }
}

@Composable
private fun NavigationItemIcon(
    icon: @Composable () -> Unit,
    badge: (@Composable () -> Unit)? = null,
) {
    val iconContent = remember(icon) { movableContentOf(icon) }
    if (badge != null) {
        Box {
            iconContent.invoke()
            Badge(
                backgroundColor = FluentTheme.colors.text.accent.tertiary,
                content = {
                    badge.invoke()
                },
            )
        }
    } else {
        iconContent.invoke()
    }
}

private fun getRoute(tab: HomeTabsPresenter.State.HomeTabs): Route =
    when (tab) {
        HomeTabsPresenter.State.HomeTabs.Home -> Route.Home(AccountType.Guest)
        HomeTabsPresenter.State.HomeTabs.Notifications -> Route.Notification
        HomeTabsPresenter.State.HomeTabs.Discover -> Route.Discover
    }

private val HomeTabsPresenter.State.HomeTabs.title: StringResource
    get() =
        when (this) {
            HomeTabsPresenter.State.HomeTabs.Home -> Res.string.home_tab_home_title
            HomeTabsPresenter.State.HomeTabs.Notifications -> Res.string.home_tab_notifications_title
            HomeTabsPresenter.State.HomeTabs.Discover -> Res.string.home_tab_discover_title
        }

private val HomeTabsPresenter.State.HomeTabs.icon: ImageVector
    get() =
        when (this) {
            HomeTabsPresenter.State.HomeTabs.Home -> FontAwesomeIcons.Solid.House
            HomeTabsPresenter.State.HomeTabs.Notifications -> FontAwesomeIcons.Solid.Bell
            HomeTabsPresenter.State.HomeTabs.Discover -> FontAwesomeIcons.Solid.MagnifyingGlass
        }

@Composable
private fun presenter(uriHandler: UriHandler) =
    run {
        val activeAccountPresenter =
            remember {
                dev.dimension.flare.ui.presenter.home
                    .ActiveAccountPresenter()
            }.invoke()
        val secondaryTabsPresenter = remember { SecondaryTabsPresenter() }.invoke()
        val loginState = remember { LoggedInPresenter() }.invoke()
        val canComposeState = remember { CanComposePresenter() }.invoke()
        val tabState = remember { HomeTabsPresenter() }.invoke()
        val allNotificationState = remember { AllNotificationBadgePresenter() }.invoke()
        val scrollToTopRegistry =
            remember {
                ScrollToTopRegistry()
            }
        val topLevelBackStack =
            remember(tabState.tabs) {
                tabState.tabs.map {
                    TopLevelBackStack(
                        getRoute(it.first()),
                        topLevelRoutes = it.map { getRoute(it) },
                    )
                }
            }
        val deeplinkPresenter =
            remember(topLevelBackStack) {
                DeepLinkPresenter(
                    onRoute = {
                        val route = Route.from(it)
                        if (route != null) {
                            when (route) {
                                is Route.UrlRoute -> {
                                    uriHandler.openUri(route.url)
                                }

                                else -> {
                                    topLevelBackStack.takeSuccess()?.push(route)
                                }
                            }
                        }
                    },
                    onLink = {
                        uriHandler.openUri(it)
                    },
                )
            }.invoke()

        object :
            LoggedInState by loginState,
            HomeTabsPresenter.State by tabState,
            SecondaryTabsPresenter.State by secondaryTabsPresenter,
            UserState by activeAccountPresenter {
            val notificationState = allNotificationState
            val canComposeState = canComposeState.canCompose
            val scrollToTopRegistry = scrollToTopRegistry
            val deeplinkPresenter = deeplinkPresenter
            val topLevelBackStack = topLevelBackStack

            fun navigate(route: Route) {
                when (route) {
                    is Route.UrlRoute -> {
                        uriHandler.openUri(route.url)
                    }

                    else -> {
                        topLevelBackStack.takeSuccess()?.push(route)
                    }
                }
            }

            fun navigate(shortcut: SecondaryTabsPresenter.Tab) {
                val route = getDirection(shortcut)
                if (route != null) {
                    navigate(route)
                }
            }

            fun goBack() {
                topLevelBackStack.takeSuccess()?.pop()
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

val LocalWindowPadding =
    androidx.compose.runtime.staticCompositionLocalOf {
        PaddingValues(0.dp)
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

@Composable
internal fun RegisterTabCallback(
    lazyListState: LazyListState,
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
