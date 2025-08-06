package dev.dimension.flare.ui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import compose.icons.fontawesomeicons.solid.Gear
import compose.icons.fontawesomeicons.solid.Pen
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.dimension.flare.R
import dev.dimension.flare.data.model.AllListTabItem
import dev.dimension.flare.data.model.Bluesky
import dev.dimension.flare.data.model.DirectMessageTabItem
import dev.dimension.flare.data.model.DiscoverTabItem
import dev.dimension.flare.data.model.HomeTimelineTabItem
import dev.dimension.flare.data.model.Misskey
import dev.dimension.flare.data.model.NotificationTabItem
import dev.dimension.flare.data.model.ProfileTabItem
import dev.dimension.flare.data.model.RssTabItem
import dev.dimension.flare.data.model.SettingsTabItem
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.InAppNotificationComponent
import dev.dimension.flare.ui.component.NavigationSuiteScaffold2
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.component.TopLevelBackStack
import dev.dimension.flare.ui.component.listCard
import dev.dimension.flare.ui.component.platform.isBigScreen
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.isError
import dev.dimension.flare.ui.model.isSuccess
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.HomeTabsPresenter
import dev.dimension.flare.ui.presenter.home.UserPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.route.Route
import dev.dimension.flare.ui.route.Router
import dev.dimension.flare.ui.route.accountTypeOr
import dev.dimension.flare.ui.screen.settings.TabIcon
import dev.dimension.flare.ui.screen.settings.TabTitle
import dev.dimension.flare.ui.screen.splash.SplashScreen
import dev.dimension.flare.ui.theme.MediumAlpha
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.koin.compose.koinInject

@OptIn(
    ExperimentalMaterial3AdaptiveNavigationSuiteApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalSharedTransitionApi::class,
)
@Composable
internal fun HomeScreen(afterInit: () -> Unit) {
    val scope = rememberCoroutineScope()
    val state by producePresenter { presenter() }
    val hapticFeedback = LocalHapticFeedback.current
    val wideNavigationRailState = rememberWideNavigationRailState()
    state.tabs
        .onSuccess { tabs ->
            val rootHazeState = rememberHazeState()
            val topLevelBackStack by producePresenter(
                key = "home_top_level_back_stack_${tabs.all.first().tabItem}",
                useImmediateClock = true,
            ) {
                TopLevelBackStack<Route>(
                    getDirection(tabs.all.first().tabItem),
                )
            }

            fun navigate(route: Route) {
                topLevelBackStack.addTopLevel(route)
                scope.launch {
                    wideNavigationRailState.collapse()
                }
            }

            val currentRoute by remember {
                derivedStateOf {
                    topLevelBackStack.topLevelKey
                }
            }
            val accountType by remember {
                derivedStateOf {
                    currentRoute.accountTypeOr(AccountType.Active)
                }
            }
            val userState by producePresenter(key = "home_account_type_$accountType") {
                userPresenter(accountType)
            }
            userState
                .onSuccess {
                    LaunchedEffect(Unit) {
                        afterInit.invoke()
                    }
                }.onError {
                    LaunchedEffect(Unit) {
                        afterInit.invoke()
                    }
                }
            val layoutType =
                NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(
                    currentWindowAdaptiveInfo(),
                )
            Box {
                NavigationSuiteScaffold2(
                    wideNavigationRailState = wideNavigationRailState,
                    modifier = Modifier.fillMaxSize().hazeSource(rootHazeState),
                    bottomBarAutoHideEnabled = state.navigationState.bottomBarAutoHideEnabled,
                    layoutType = layoutType,
                    showFab = userState.isSuccess && accountType !is AccountType.Guest,
                    onFabClicked = {
                        navigate(Route.Compose.New(accountType))
                    },
                    navigationSuiteColors =
                        NavigationSuiteDefaults.colors(
                            navigationBarContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                    railHeader = {
                        HomeRailHeader(
                            wideNavigationRailState,
                            userState,
                            layoutType,
                            currentRoute,
                            ::navigate,
                        )
                    },
                    navigationSuiteItems = {
                        tabs.primary.forEach { (tab, badgeState) ->
                            item(
                                selected = currentRoute == getDirection(tab),
                                onClick = {
                                    if (currentRoute == getDirection(tab)) {
                                        state.scrollToTopRegistry.scrollToTop()
                                    } else {
                                        navigate(getDirection(tab))
                                    }
                                },
                                icon = {
                                    TabIcon(
                                        accountType = tab.account,
                                        icon = tab.metaData.icon,
                                        title = tab.metaData.title,
                                    )
                                },
                                label = {
                                    TabTitle(
                                        title = tab.metaData.title,
                                    )
                                },
                                badge =
                                    if (badgeState.isSuccess) {
                                        {
                                            badgeState.onSuccess {
                                                if (it > 0) {
                                                    Badge {
                                                        Text(text = it.toString())
                                                    }
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
                                            navigate(Route.AccountSelection)
                                        }
                                    } else {
                                        null
                                    },
                            )
                        }
                    },
                    secondaryItems = {
                        tabs.secondary.forEach { (tab, badgeState) ->
                            item(
                                selected = currentRoute == getDirection(tab),
                                onClick = {
                                    if (currentRoute == getDirection(tab)) {
                                        state.scrollToTopRegistry.scrollToTop()
                                    } else {
                                        navigate(getDirection(tab))
                                    }
                                },
                                icon = {
                                    TabIcon(
                                        accountType = tab.account,
                                        icon = tab.metaData.icon,
                                        title = tab.metaData.title,
                                        iconOnly = tabs.secondaryIconOnly,
                                    )
                                },
                                label = {
                                    TabTitle(
                                        title = tab.metaData.title,
                                    )
                                },
                                badge =
                                    if (badgeState.isSuccess) {
                                        {
                                            badgeState.onSuccess {
                                                if (it > 0) {
                                                    Badge {
                                                        Text(text = it.toString())
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        null
                                    },
                            )
                        }
                    },
                    footerItems = {
                        if (!userState.isError) {
                            item(
                                selected = currentRoute is Route.Settings.Main,
                                onClick = {
                                    navigate(Route.Settings.Main)
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
                        }
                    },
                ) {
                    CompositionLocalProvider(
                        LocalScrollToTopRegistry provides state.scrollToTopRegistry,
                    ) {
                        Router(
                            topLevelBackStack = topLevelBackStack,
                            navigationState = state.navigationState,
                            openDrawer = {
                                scope.launch {
                                    wideNavigationRailState.toggle()
                                }
                            },
                        )
                    }
                }
                InAppNotificationComponent(
                    modifier = Modifier.align(Alignment.TopCenter),
                    hazeState = rootHazeState,
                )
            }
        }.onLoading {
            SplashScreen()
        }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
private fun HomeRailHeader(
    wideNavigationRailState: WideNavigationRailState,
    userState: UiState<UiUserV2>,
    layoutType: NavigationSuiteType,
    currentRoute: Route,
    navigate: (Route) -> Unit,
) {
    val scope = rememberCoroutineScope()
    SharedTransitionLayout {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedContent(
                wideNavigationRailState.currentValue,
            ) { railState ->
                when (railState) {
                    WideNavigationRailValue.Collapsed ->
                        Box(
                            modifier =
                                Modifier
                                    .padding(horizontal = 20.dp),
                        ) {
                            userState
                                .onSuccess { user ->
                                    AvatarComponent(
                                        user.avatar,
                                        size = 56.dp,
                                        modifier =
                                            Modifier
                                                .sharedElement(
                                                    rememberSharedContentState(
                                                        key = "avatar",
                                                    ),
                                                    animatedVisibilityScope = this@AnimatedContent,
                                                ).clickable {
                                                    scope.launch {
                                                        wideNavigationRailState.toggle()
                                                    }
                                                }.clip(CircleShape),
                                    )
                                }.onLoading {
                                    Box(modifier = Modifier.size(56.dp))
                                }
                        }

                    WideNavigationRailValue.Expanded ->
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .let {
                                        if (isBigScreen() || layoutType != NavigationSuiteType.NavigationBar) {
                                            it
                                        } else {
                                            it
                                                .padding(horizontal = 16.dp)
                                                .listCard()
                                                .background(MaterialTheme.colorScheme.surface)
                                        }
                                    }.fillMaxWidth()
                                    .clickable {
                                        userState.onSuccess { user ->
                                            navigate(
                                                Route.Profile.Me(
                                                    accountType =
                                                        AccountType.Specific(
                                                            user.key,
                                                        ),
                                                ),
                                            )
                                        }
                                    },
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .padding(
                                            horizontal =
                                                if (layoutType ==
                                                    NavigationSuiteType.NavigationBar
                                                ) {
                                                    16.dp
                                                } else {
                                                    24.dp
                                                },
                                            vertical = 16.dp,
                                        ),
                            ) {
                                AvatarComponent(
                                    data = userState.takeSuccess()?.avatar,
                                    size = 64.dp,
                                    modifier =
                                        Modifier
                                            .sharedElement(
                                                rememberSharedContentState(
                                                    key = "avatar",
                                                ),
                                                animatedVisibilityScope = this@AnimatedContent,
                                            ).clip(CircleShape),
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                userState.onSuccess { user ->
                                    RichText(
                                        text = user.name,
                                        textStyle = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        user.handle,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier =
                                            Modifier
                                                .alpha(MediumAlpha),
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    navigate(Route.AccountSelection)
                                },
                                modifier =
                                    Modifier
                                        .padding(
                                            horizontal = 8.dp,
                                        ),
                            ) {
                                FAIcon(
                                    FontAwesomeIcons.Solid.EllipsisVertical,
                                    contentDescription = null,
                                )
                            }
                        }
                }
            }
            if (layoutType == NavigationSuiteType.NavigationRail &&
                !userState.isError
            ) {
                AnimatedContent(
                    wideNavigationRailState.currentValue,
                    modifier = Modifier.padding(horizontal = 20.dp),
                ) { railState ->
                    when (railState) {
                        WideNavigationRailValue.Collapsed ->
                            FloatingActionButton(
                                onClick = {
                                    navigate(
                                        Route.Compose.New(
                                            currentRoute.accountTypeOr(
                                                AccountType.Active,
                                            ),
                                        ),
                                    )
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

                        WideNavigationRailValue.Expanded ->
                            ExtendedFloatingActionButton(
                                onClick = {
                                    navigate(
                                        Route.Compose.New(
                                            currentRoute.accountTypeOr(
                                                AccountType.Active,
                                            ),
                                        ),
                                    )
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

private fun getDirection(
    tab: TabItem,
    accountType: AccountType = tab.account,
): Route =
    when (tab) {
        is DiscoverTabItem -> Route.Discover(accountType)
        is ProfileTabItem -> Route.Profile.Me(accountType)
        is HomeTimelineTabItem -> Route.Home(accountType)
        is TimelineTabItem -> Route.Timeline(accountType, tab)
        is NotificationTabItem -> Route.Notification(accountType)
        SettingsTabItem -> Route.Settings.Main
        is AllListTabItem -> Route.Lists.List(accountType)
        is Bluesky.FeedsTabItem -> Route.Bluesky.Feed(accountType)
        is DirectMessageTabItem -> Route.DM.List(accountType)
        is RssTabItem -> Route.Rss.Sources
        is Misskey.AntennasListTabItem -> Route.Misskey.AntennasList(accountType)
    }

@Composable
private fun presenter(settingsRepository: SettingsRepository = koinInject()) =
    run {
        val navigationState =
            remember {
                NavigationState()
            }
        val tabs =
            remember {
                HomeTabsPresenter(settingsRepository.tabSettings)
            }.invoke()
        val scrollToTopRegistry =
            remember {
                ScrollToTopRegistry()
            }
        object {
            val tabs = tabs.tabs
            val navigationState = navigationState
            val scrollToTopRegistry = scrollToTopRegistry
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
