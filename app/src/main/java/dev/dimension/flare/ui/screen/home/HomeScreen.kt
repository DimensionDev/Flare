package dev.dimension.flare.ui.screen.home

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.eygraber.compose.placeholder.material3.placeholder
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.animations.NavHostAnimatedDestinationStyle
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.generated.destinations.ComposeRouteDestination
import com.ramcosta.composedestinations.generated.destinations.DiscoverRouteDestination
import com.ramcosta.composedestinations.generated.destinations.MeRouteDestination
import com.ramcosta.composedestinations.generated.destinations.NotificationRouteDestination
import com.ramcosta.composedestinations.generated.destinations.SettingsRouteDestination
import com.ramcosta.composedestinations.generated.destinations.TabSplashScreenDestination
import com.ramcosta.composedestinations.generated.destinations.TimelineRouteDestination
import com.ramcosta.composedestinations.navigation.DependenciesContainerBuilder
import com.ramcosta.composedestinations.navigation.dependency
import com.ramcosta.composedestinations.spec.Direction
import com.ramcosta.composedestinations.spec.NavHostGraphSpec
import com.ramcosta.composedestinations.spec.Route
import com.ramcosta.composedestinations.utils.composable
import com.ramcosta.composedestinations.utils.dialogComposable
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import dev.dimension.flare.R
import dev.dimension.flare.data.model.DiscoverTabItem
import dev.dimension.flare.data.model.NotificationTabItem
import dev.dimension.flare.data.model.ProfileTabItem
import dev.dimension.flare.data.model.SettingsTabItem
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.NavigationSuiteScaffold2
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.ActiveAccountPresenter
import dev.dimension.flare.ui.presenter.home.UserPresenter
import dev.dimension.flare.ui.presenter.home.UserState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.screen.compose.ComposeRoute
import dev.dimension.flare.ui.screen.settings.AccountItem
import dev.dimension.flare.ui.screen.settings.TabIcon
import dev.dimension.flare.ui.screen.settings.TabTitle
import dev.dimension.flare.ui.screen.splash.SplashScreen
import dev.dimension.flare.ui.screen.splash.SplashScreenArgs
import dev.dimension.flare.ui.theme.FlareTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

data class RootNavController(
    val navController: NavController,
)

@OptIn(
    ExperimentalMaterial3AdaptiveNavigationSuiteApi::class,
    ExperimentalSharedTransitionApi::class,
)
@Composable
internal fun HomeScreen(
    afterInit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val state by producePresenter { presenter() }
    val navController = rememberNavController()
    val rootNavController = remember(navController) { RootNavController(navController) }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute by remember {
        derivedStateOf {
            navBackStackEntry?.destination?.route
        }
    }
    state.tabs
        .onSuccess { tabs ->
            LaunchedEffect(Unit) {
                afterInit.invoke()
            }
            val currentTab by remember {
                derivedStateOf {
                    tabs.all.firstOrNull { it.tabItem.key == currentRoute }?.tabItem
                }
            }

            val accountTypeState by producePresenter(key = "home_account_type_${currentTab?.account}") {
                accountTypePresenter(currentTab?.account ?: AccountType.Active)
            }
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            val layoutType =
                NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(
                    currentWindowAdaptiveInfo(),
                )
            val actualLayoutType = state.navigationState.type ?: layoutType
            BackHandler(
                enabled = drawerState.isOpen,
                onBack = {
                    scope.launch {
                        drawerState.close()
                    }
                },
            )
            FlareTheme {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet(
                            content = {
                                Column(
                                    modifier =
                                        Modifier
                                            .padding(horizontal = 12.dp)
                                            .weight(1f)
                                            .verticalScroll(rememberScrollState()),
                                ) {
                                    DrawerHeader(
                                        accountTypeState = accountTypeState,
                                        currentTab = currentTab,
                                        navController = navController,
                                        showFab = layoutType != NavigationSuiteType.NavigationBar,
                                    )
                                    if (layoutType != NavigationSuiteType.NavigationBar) {
                                        tabs.primary.forEach { (tab, tabState) ->
                                            NavigationDrawerItem(
                                                selected = currentRoute == tab.key,
                                                onClick = {
                                                    if (currentRoute == tab.key) {
                                                        tabState.onClick()
                                                    } else {
                                                        navController.navigate(tab.key) {
                                                            popUpTo(navController.graph.findStartDestination().id) {
                                                                saveState = true
                                                            }
                                                            launchSingleTop = true
                                                            restoreState = true
                                                        }
                                                    }
                                                    scope.launch {
                                                        drawerState.close()
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
                                            )
                                        }
                                    }
                                    if (tabs.secondary.isNotEmpty()) {
                                        HorizontalDivider()
                                    }
                                    tabs.secondary.forEach { (tab, tabState) ->
                                        NavigationDrawerItem(
                                            selected = currentRoute == tab.key,
                                            onClick = {
                                                if (currentRoute == tab.key) {
                                                    tabState.onClick()
                                                } else {
                                                    navController.navigate(tab.key) {
                                                        popUpTo(navController.graph.findStartDestination().id) {
                                                            saveState = true
                                                        }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }
                                                scope.launch {
                                                    drawerState.close()
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
                                        )
                                    }
                                }
                                NavigationDrawerItem(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    label = {
                                        Text(stringResource(R.string.settings_title))
                                    },
                                    selected = currentRoute == SettingsRouteDestination.route,
                                    onClick = {
                                        navController
                                            .navigate(SettingsRouteDestination.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        scope.launch {
                                            drawerState.close()
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            Icons.Default.Settings,
                                            contentDescription = stringResource(R.string.settings_title),
                                        )
                                    },
                                )
                            },
                        )
                    },
                    gesturesEnabled = state.navigationState.drawerEnabled && actualLayoutType != NavigationSuiteType.NavigationDrawer,
                ) {
                    NavigationSuiteScaffold2(
                        layoutType = actualLayoutType,
                        modifier = modifier,
                        drawerHeader = {
                            DrawerHeader(accountTypeState, currentTab, navController)
                        },
                        railHeader = {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        drawerState.open()
                                    }
                                },
                            ) {
                                accountTypeState.user
                                    .onSuccess {
                                        AvatarComponent(it.avatarUrl)
                                    }.onLoading {
                                        AvatarComponent(null, modifier = Modifier.placeholder(true))
                                    }.onError {
                                        Icon(imageVector = Icons.Default.Menu, contentDescription = null)
                                    }
                            }
                            accountTypeState.user.onSuccess {
                                FloatingActionButton(
                                    onClick = {
                                        currentTab?.let {
                                            navController.toDestinationsNavigator().navigate(
                                                direction =
                                                    ComposeRouteDestination(
                                                        it.account,
                                                    ),
                                            )
                                        }
                                    },
                                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = stringResource(id = R.string.compose_title),
                                    )
                                }
                            }
                        },
                        navigationSuiteItems = {
                            tabs.primary.forEach { (tab, tabState) ->
                                item(
                                    selected = currentRoute == tab.key,
                                    onClick = {
                                        if (currentRoute == tab.key) {
                                            tabState.onClick()
                                        } else {
                                            navController.navigate(tab.key) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
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
                                )
                            }
                        },
                        secondaryItems = {
                            tabs.secondary.forEach { (tab, tabState) ->
                                item(
                                    selected = currentRoute == tab.key,
                                    onClick = {
                                        if (currentRoute == tab.key) {
                                            tabState.onClick()
                                        } else {
                                            navController.navigate(tab.key) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
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
                                )
                            }
                        },
                        footerItems = {
                            accountTypeState.user.onSuccess {
                                item(
                                    selected = currentRoute == SettingsRouteDestination.route,
                                    onClick = {
                                        navController
                                            .navigate(SettingsRouteDestination.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
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
                        NavHost(
                            navController = navController,
                            startDestination =
                                tabs.primary
                                    .first()
                                    .tabItem.key,
                            enterTransition = {
                                slideInVertically(tween(durationMillis = 700)) { 80 } +
                                    fadeIn(
                                        tween(durationMillis = 700),
                                        0.8f,
                                    )
                            },
                            exitTransition = {
                                slideOutVertically(tween(durationMillis = 700)) { 80 } +
                                    fadeOut(
                                        tween(
                                            durationMillis = 700,
                                        ),
                                    )
                            },
                        ) {
                            tabs.all.forEach { (tab, tabState) ->
                                composable(tab.key) {
                                    SharedTransitionLayout {
                                        Router(
                                            modifier = Modifier.fillMaxSize(),
                                            navGraph = NavGraphs.root,
                                            direction = TabSplashScreenDestination,
                                        ) {
                                            dependency(rootNavController)
                                            dependency(SplashScreenArgs(getDirection(tab, tab.account)))
                                            dependency(tabState)
                                            dependency(drawerState)
                                            dependency(this@SharedTransitionLayout)
                                            dependency(state.navigationState)
                                        }
                                    }
                                }
                            }
                            composable(SettingsRouteDestination) {
                                Router(
                                    modifier = Modifier.fillMaxSize(),
                                    navGraph = NavGraphs.root,
                                    direction = SettingsRouteDestination,
                                ) {
                                    dependency(rootNavController)
                                    dependency(drawerState)
                                    dependency(state.navigationState)
                                }
                            }
                            dialogComposable(ComposeRouteDestination) {
                                ComposeRoute(
                                    navigator = destinationsNavigator(navController),
                                    accountType = navArgs.accountType,
                                )
                            }
                        }
                    }
                }
            }
        }.onLoading {
            SplashScreen()
        }
}

@Composable
private fun ColumnScope.DrawerHeader(
    accountTypeState: UserState,
    currentTab: TabItem?,
    navController: NavHostController,
    showFab: Boolean = true,
) {
    if (accountTypeState.user is UiState.Error) {
        ListItem(
            headlineContent = {
                Text(text = stringResource(id = R.string.app_name))
            },
        )
    } else {
        AccountItem(
            userState = accountTypeState.user,
            onClick = {},
        )
    }
    if (showFab) {
        accountTypeState.user.onSuccess {
            ExtendedFloatingActionButton(
                onClick = {
                    currentTab?.let {
                        navController.toDestinationsNavigator().navigate(
                            direction =
                                ComposeRouteDestination(
                                    it.account,
                                ),
                        )
                    }
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(id = R.string.compose_title),
                    )
                },
                text = {
                    Text(text = stringResource(id = R.string.compose_title))
                },
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private fun getDirection(
    tab: TabItem,
    accountType: AccountType,
): Direction =
    when (tab) {
        is DiscoverTabItem -> {
            DiscoverRouteDestination(accountType)
        }

        is ProfileTabItem -> {
            MeRouteDestination(accountType)
        }

        is TimelineTabItem -> {
            TimelineRouteDestination(tab)
        }

        is NotificationTabItem -> {
            NotificationRouteDestination(accountType)
        }

        SettingsTabItem -> {
            SettingsRouteDestination
        }
    }

@Composable
internal fun Router(
    navGraph: NavHostGraphSpec,
    direction: Route,
    modifier: Modifier = Modifier,
    dependenciesContainerBuilder: @Composable DependenciesContainerBuilder<*>.() -> Unit = {},
) {
    val innerNavController = rememberNavController()
    val uriHandler = LocalUriHandler.current
    CompositionLocalProvider(
        LocalUriHandler provides
            remember {
                ProxyUriHandler(
                    navController = innerNavController,
                    actualUriHandler = uriHandler,
                )
            },
    ) {
        DestinationsNavHost(
            modifier = modifier,
            navController = innerNavController,
            navGraph = navGraph,
            defaultTransitions = DefaultFadingTransitions,
            startRoute = direction,
            dependenciesContainerBuilder = dependenciesContainerBuilder,
        )
    }
}

private object DefaultFadingTransitions : NavHostAnimatedDestinationStyle() {
    override val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
        {
            fadeIn(animationSpec = tween())
//            slideInHorizontally(tween()) { it / 3 } + fadeIn()
        }

    override val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
        {
            fadeOut(animationSpec = tween())
//            slideOutHorizontally(tween()) { -it / 3 } + fadeOut()
        }

//    override val popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
//        {
//            slideInHorizontally(tween()) { -it / 3 } + fadeIn()
//        }
//
//    override val popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
//        {
//            slideOutHorizontally(tween()) { it / 3 } + fadeOut()
//        }
}

private class ProxyUriHandler(
    private val navController: NavController,
    private val actualUriHandler: UriHandler,
) : UriHandler {
    override fun openUri(uri: String) {
        if (uri.startsWith("flare://")) {
            navController.navigate(Uri.parse(uri))
        } else {
            actualUriHandler.openUri(uri)
        }
    }
}

@Composable
private fun presenter(settingsRepository: SettingsRepository = koinInject()) =
    run {
        val account =
            remember {
                ActiveAccountPresenter()
            }.invoke()
        val navigationState =
            remember {
                NavigationState()
            }
        val tabs =
            account.user.flatMap(
                onError = {
                    UiState.Success(
                        HomeTabState(
                            primary =
                                TimelineTabItem.guest
                                    .map {
                                        HomeTabItem(it)
                                    }.toImmutableList(),
                            secondary = persistentListOf(),
                            secondaryIconOnly = true,
                        ),
                    )
                },
            ) { user ->
                settingsRepository.tabSettings.collectAsUiState().value.flatMap(
                    onError = {
                        UiState.Success(
                            HomeTabState(
                                primary =
                                    TimelineTabItem.default
                                        .map {
                                            HomeTabItem(it)
                                        }.toImmutableList(),
                                secondary =
                                    TimelineTabItem
                                        .defaultSecondary(user)
                                        .map {
                                            HomeTabItem(it)
                                        }.toImmutableList(),
                                secondaryIconOnly = true,
                            ),
                        )
                    },
                ) { tabSettings ->
                    val secondary =
                        tabSettings.secondaryItems ?: TimelineTabItem.defaultSecondary(user)
                    UiState.Success(
                        HomeTabState(
                            primary =
                                tabSettings.items
                                    .map {
                                        HomeTabItem(it)
                                    }.toImmutableList(),
                            secondary =
                                secondary
                                    .filter {
                                        tabSettings.items.none { item -> item.key == it.key }
                                    }.map {
                                        HomeTabItem(it)
                                    }.toImmutableList(),
                            secondaryIconOnly = tabSettings.secondaryItems == null,
                        ),
                    )
                }
            }
        object {
            val tabs = tabs
            val navigationState = navigationState
        }
    }

@Composable
private fun accountTypePresenter(accountType: AccountType) =
    run {
        remember(accountType) { UserPresenter(accountType, null) }.invoke()
    }

@Immutable
private data class HomeTabItem(
    val tabItem: TabItem,
    val tabState: TabState = TabState(),
)

@Immutable
private data class HomeTabState(
    val primary: ImmutableList<HomeTabItem>,
    val secondary: ImmutableList<HomeTabItem>,
    val secondaryIconOnly: Boolean = false,
) {
    val all: ImmutableList<HomeTabItem>
        get() = (primary + secondary).toImmutableList()
}

internal class TabState {
    private val callbacks = mutableListOf<() -> Unit>()

    fun registerCallback(callback: () -> Unit) {
        callbacks.add(callback)
    }

    fun unregisterCallback(callback: () -> Unit) {
        callbacks.remove(callback)
    }

    fun onClick() {
        callbacks.lastOrNull()?.invoke()
    }
}

internal class NavigationState {
    private val state = mutableStateOf<NavigationSuiteType?>(null)
    private val drawerState = mutableStateOf(true)
    val type: NavigationSuiteType?
        get() = state.value

    val drawerEnabled: Boolean
        get() = drawerState.value

    fun hide() {
        state.value = NavigationSuiteType.None
    }

    fun show() {
        state.value = null
    }

    fun enableDrawer() {
        drawerState.value = true
    }

    fun disableDrawer() {
        drawerState.value = false
    }
}

@Composable
internal fun RegisterTabCallback(
    tabState: TabState,
    lazyListState: LazyStaggeredGridState,
) {
    val scope = rememberCoroutineScope()
    val callback: () -> Unit =
        remember {
            {
                scope.launch {
                    if (lazyListState.firstVisibleItemIndex > 20) {
                        lazyListState.scrollToItem(0)
                    } else {
                        lazyListState.animateScrollToItem(0)
                    }
                }
            }
        }
    DisposableEffect(Unit) {
        tabState.registerCallback(callback)
        onDispose {
            tabState.unregisterCallback(callback)
        }
    }
}
