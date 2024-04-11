package dev.dimension.flare.ui.screen.home

import android.net.Uri
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.eygraber.compose.placeholder.material3.placeholder
import com.google.accompanist.navigation.material.ExperimentalMaterialNavigationApi
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.animations.defaults.RootNavGraphDefaultAnimations
import com.ramcosta.composedestinations.animations.rememberAnimatedNavHostEngine
import com.ramcosta.composedestinations.navigation.DependenciesContainerBuilder
import com.ramcosta.composedestinations.navigation.dependency
import com.ramcosta.composedestinations.navigation.navigate
import com.ramcosta.composedestinations.spec.Direction
import com.ramcosta.composedestinations.spec.NavGraphSpec
import com.ramcosta.composedestinations.spec.Route
import com.ramcosta.composedestinations.utils.composable
import com.ramcosta.composedestinations.utils.dialogComposable
import dev.dimension.flare.R
import dev.dimension.flare.data.model.DiscoverTabItem
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
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.ActiveAccountPresenter
import dev.dimension.flare.ui.presenter.home.UserPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.screen.NavGraphs
import dev.dimension.flare.ui.screen.compose.ComposeRoute
import dev.dimension.flare.ui.screen.destinations.ComposeRouteDestination
import dev.dimension.flare.ui.screen.destinations.DiscoverRouteDestination
import dev.dimension.flare.ui.screen.destinations.HomeRouteDestination
import dev.dimension.flare.ui.screen.destinations.MeRouteDestination
import dev.dimension.flare.ui.screen.destinations.NotificationRouteDestination
import dev.dimension.flare.ui.screen.destinations.SettingsRouteDestination
import dev.dimension.flare.ui.screen.destinations.TabSplashScreenDestination
import dev.dimension.flare.ui.screen.settings.AccountItem
import dev.dimension.flare.ui.screen.settings.TabIcon
import dev.dimension.flare.ui.screen.settings.TabTitle
import dev.dimension.flare.ui.screen.splash.SplashScreen
import dev.dimension.flare.ui.screen.splash.SplashScreenArgs
import dev.dimension.flare.ui.theme.FlareTheme
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

data class RootNavController(
    val navController: NavController,
)

@OptIn(
    ExperimentalMaterial3AdaptiveNavigationSuiteApi::class,
    ExperimentalMaterial3AdaptiveApi::class,
)
@Composable
internal fun HomeScreen(modifier: Modifier = Modifier) {
    val state by producePresenter { presenter() }
    val navController = rememberNavController()
    val rootNavController = remember(navController) { RootNavController(navController) }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute by remember {
        derivedStateOf {
            navBackStackEntry?.destination?.route
        }
    }
    state.tabs.onSuccess { tabs ->
        val currentTab by remember {
            derivedStateOf {
                tabs.entries.firstOrNull { it.key.key == currentRoute }?.key
            }
        }

        val accountTypeState by producePresenter(key = "home_account_type_${currentTab?.account}") {
            accountTypePresenter(currentTab?.account ?: AccountType.Active)
        }
        FlareTheme {
            NavigationSuiteScaffold2(
                layoutType =
                    NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(
                        currentWindowAdaptiveInfo(),
                    ),
                modifier = modifier,
                drawerHeader = {
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
                    accountTypeState.user.onSuccess {
                        ExtendedFloatingActionButton(
                            onClick = {
                                currentTab?.let {
                                    navController.navigate(direction = ComposeRouteDestination(it.account))
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
                    }
                },
                railHeader = {
                    IconButton(onClick = { /*TODO*/ }) {
                        accountTypeState.user.onSuccess {
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
                                    navController.navigate(direction = ComposeRouteDestination(it.account))
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
                    tabs.forEach { (tab, tabState) ->
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
                footerItems = {
                    accountTypeState.user.onSuccess {
                        item(
                            selected = currentRoute == SettingsRouteDestination.route,
                            onClick = {
                                navController.navigate(direction = SettingsRouteDestination) {
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
                    startDestination = tabs.keys.first().key,
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
                    tabs.forEach { (tab, tabState) ->
                        composable(tab.key) {
                            Router(
                                modifier = Modifier.fillMaxSize(),
                                navGraph = NavGraphs.root,
                                direction = TabSplashScreenDestination,
                            ) {
                                dependency(rootNavController)
                                dependency(SplashScreenArgs(getDirection(tab, tab.account)))
                                dependency(tabState)
                            }
                        }
                    }
                    composable(SettingsRouteDestination) {
                        Router(
                            navGraph = NavGraphs.root,
                            direction = SettingsRouteDestination,
                        )
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
    }.onLoading {
        SplashScreen()
    }
}

private fun getDirection(
    tab: TabItem,
    accountType: AccountType,
): Direction {
    return when (tab) {
        is DiscoverTabItem -> {
            DiscoverRouteDestination(accountType)
        }

        is ProfileTabItem -> {
            MeRouteDestination(accountType)
        }

        is TimelineTabItem -> {
            when (tab.type) {
                TimelineTabItem.Type.Home -> HomeRouteDestination(accountType)
                TimelineTabItem.Type.Notifications -> NotificationRouteDestination(accountType)
            }
        }

        SettingsTabItem -> {
            SettingsRouteDestination
        }
    }
}

@Composable
@OptIn(ExperimentalMaterialNavigationApi::class, ExperimentalAnimationApi::class)
internal fun Router(
    navGraph: NavGraphSpec,
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
            engine =
                rememberAnimatedNavHostEngine(
                    rootDefaultAnimations =
                        RootNavGraphDefaultAnimations(
                            enterTransition = {
                                slideInHorizontally(tween()) { it / 3 } + fadeIn()
                            },
                            exitTransition = {
                                slideOutHorizontally(tween()) { -it / 3 } + fadeOut()
                            },
                            popEnterTransition = {
                                slideInHorizontally(tween()) { -it / 3 } + fadeIn()
                            },
                            popExitTransition = {
                                slideOutHorizontally(tween()) { it / 3 } + fadeOut()
                            },
                        ),
                ),
            startRoute = direction,
            dependenciesContainerBuilder = dependenciesContainerBuilder,
        )
    }
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
        val tabs =
            account.user.flatMap(
                onError = {
                    UiState.Success(TimelineTabItem.guest)
                },
            ) {
                settingsRepository.tabSettings.collectAsUiState().value.map {
                    it.items
                }
            }.map {
                it.associateWith {
                    TabState()
                }.toImmutableMap()
            }
        object {
            val tabs = tabs
        }
    }

@Composable
private fun accountTypePresenter(accountType: AccountType) =
    run {
        remember(accountType) { UserPresenter(accountType, null) }.invoke()
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
