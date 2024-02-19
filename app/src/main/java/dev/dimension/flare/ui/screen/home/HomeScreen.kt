package dev.dimension.flare.ui.screen.home

import android.net.Uri
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.navigation.material.ExperimentalMaterialNavigationApi
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.animations.defaults.RootNavGraphDefaultAnimations
import com.ramcosta.composedestinations.animations.rememberAnimatedNavHostEngine
import com.ramcosta.composedestinations.navigation.DependenciesContainerBuilder
import com.ramcosta.composedestinations.navigation.dependency
import com.ramcosta.composedestinations.spec.DirectionDestinationSpec
import com.ramcosta.composedestinations.spec.NavGraphSpec
import com.ramcosta.composedestinations.utils.composable
import dev.dimension.flare.data.model.DiscoverTabItem
import dev.dimension.flare.data.model.ProfileTabItem
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.ActiveAccountPresenter
import dev.dimension.flare.ui.presenter.home.ActiveAccountState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.screen.NavGraphs
import dev.dimension.flare.ui.screen.destinations.DiscoverRouteDestination
import dev.dimension.flare.ui.screen.destinations.HomeRouteDestination
import dev.dimension.flare.ui.screen.destinations.MeRouteDestination
import dev.dimension.flare.ui.screen.destinations.NotificationRouteDestination
import dev.dimension.flare.ui.screen.destinations.SettingsRouteDestination
import dev.dimension.flare.ui.screen.settings.TabIcon
import dev.dimension.flare.ui.screen.settings.TabTitle
import dev.dimension.flare.ui.screen.splash.SplashScreen
import dev.dimension.flare.ui.theme.FlareTheme
import kotlinx.collections.immutable.toImmutableList
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
        FlareTheme {
            val layoutType =
                NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(
                    currentWindowAdaptiveInfo(),
                )

            NavigationSuiteScaffold(
                modifier = modifier,
                layoutType = layoutType,
                navigationSuiteItems = {
                    tabs.forEach { tab ->
                        item(
                            selected = currentRoute == tab.key,
                            onClick = {
                                navController.navigate(tab.key) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                TabIcon(
                                    accountType = tab.account,
                                    icon = tab.metaData.icon,
                                    title = tab.metaData.title,
                                    modifier = Modifier,
                                )
                            },
                            label = {
                                TabTitle(
                                    title = tab.metaData.title,
                                    modifier = Modifier,
                                )
                            },
                        )
                    }
                },
            ) {
                NavHost(
                    navController = navController,
                    startDestination = tabs.first().key,
                    // NavigationSuiteScaffold should have consumed the insets, but it doesn't
                    modifier =
                        Modifier.let {
                            if (layoutType == NavigationSuiteType.NavigationBar) {
                                it.consumeWindowInsets(WindowInsets.navigationBars)
                            } else {
                                it
                            }
                        },
                ) {
                    tabs.forEach { tab ->
                        composable(tab.key) {
                            Router(
                                navGraph = NavGraphs.root,
                                direction = getDirection(tab),
                            ) {
                                dependency(rootNavController)
                            }
                        }
                    }
                    composable(SettingsRouteDestination) {
                        Router(
                            navGraph = NavGraphs.root,
                            direction = SettingsRouteDestination,
                        )
                    }
                }
            }
        }
    }.onLoading {
        SplashScreen()
    }
}

private fun getDirection(tab: TabItem): DirectionDestinationSpec {
    return when (tab) {
        is DiscoverTabItem -> {
            DiscoverRouteDestination
        }

        is ProfileTabItem -> {
            MeRouteDestination
        }

        is TimelineTabItem -> {
            when (tab.type) {
                TimelineTabItem.Type.Home -> HomeRouteDestination
                TimelineTabItem.Type.Notifications -> NotificationRouteDestination
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterialNavigationApi::class, ExperimentalAnimationApi::class)
internal fun Router(
    navGraph: NavGraphSpec,
    direction: DirectionDestinationSpec,
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
            navController = innerNavController,
            navGraph = navGraph,
            engine =
                rememberAnimatedNavHostEngine(
                    rootDefaultAnimations = RootNavGraphDefaultAnimations.ACCOMPANIST_FADING,
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
        val tabSettings by settingsRepository.tabSettings.collectAsUiState()
        val activeAccountState = remember { ActiveAccountPresenter() }.invoke()

        object : ActiveAccountState by activeAccountState {
            val tabs =
                tabSettings.map {
                    it.items.toImmutableList()
                }
        }
    }
