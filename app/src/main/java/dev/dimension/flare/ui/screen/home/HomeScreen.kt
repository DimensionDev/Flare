package dev.dimension.flare.ui.screen.home

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.navigation.material.ExperimentalMaterialNavigationApi
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.animations.defaults.RootNavGraphDefaultAnimations
import com.ramcosta.composedestinations.animations.rememberAnimatedNavHostEngine
import com.ramcosta.composedestinations.navigation.DependenciesContainerBuilder
import com.ramcosta.composedestinations.navigation.dependency
import com.ramcosta.composedestinations.navigation.navigate
import com.ramcosta.composedestinations.spec.DirectionDestinationSpec
import com.ramcosta.composedestinations.spec.NavGraphSpec
import com.ramcosta.composedestinations.utils.composable
import dev.dimension.flare.R
import dev.dimension.flare.ui.screen.NavGraphs
import dev.dimension.flare.ui.screen.destinations.DiscoverRouteDestination
import dev.dimension.flare.ui.screen.destinations.HomeRouteDestination
import dev.dimension.flare.ui.screen.destinations.MeRouteDestination
import dev.dimension.flare.ui.screen.destinations.NotificationRouteDestination
import dev.dimension.flare.ui.screen.destinations.SettingsRouteDestination
import dev.dimension.flare.ui.theme.FlareTheme

sealed class Screen(
    val navGraph: NavGraphSpec,
    val direction: DirectionDestinationSpec,
    @StringRes val title: Int,
    val icon: ImageVector,
    var scrollToTop: () -> Unit = {},
) {
    data object HomeTimeline :
        Screen(
            NavGraphs.root,
            HomeRouteDestination,
            R.string.home_tab_home_title,
            Icons.Default.Home,
        )

    data object Notification :
        Screen(
            NavGraphs.root,
            NotificationRouteDestination,
            R.string.home_tab_notifications_title,
            Icons.Default.Notifications,
        )

    data object Discover :
        Screen(
            NavGraphs.root,
            DiscoverRouteDestination,
            R.string.home_tab_discover_title,
            Icons.Default.Search,
        )

    data object Me : Screen(
        NavGraphs.root,
        MeRouteDestination,
        R.string.home_tab_me_title,
        Icons.Default.AccountCircle,
    )

    data object Settings :
        Screen(
            NavGraphs.root,
            SettingsRouteDestination,
            R.string.settings_title,
            Icons.Default.Settings,
        )
}

private val allScreens =
    listOf(
        Screen.HomeTimeline,
        Screen.Notification,
        Screen.Discover,
        Screen.Me,
        Screen.Settings,
    )

private val menuItems =
    listOf(
        Screen.HomeTimeline,
        Screen.Notification,
        Screen.Discover,
        Screen.Me,
    )

@OptIn(
    ExperimentalMaterial3AdaptiveNavigationSuiteApi::class,
    ExperimentalMaterial3AdaptiveApi::class,
)
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute by remember {
        derivedStateOf {
            navBackStackEntry?.destination?.route
        }
    }

    FlareTheme {
        val layoutType =
            NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(
                currentWindowAdaptiveInfo(),
            )
        NavigationSuiteScaffold(
            modifier = modifier,
            layoutType = layoutType,
            navigationSuiteItems = {
                val items =
                    if (layoutType == NavigationSuiteType.NavigationBar) {
                        menuItems
                    } else {
                        allScreens
                    }
                items.forEach { destination ->
                    item(
                        selected = currentRoute == destination.direction.route,
                        onClick = {
                            if (currentRoute == destination.direction.route) {
                                destination.scrollToTop()
                            } else {
                                navController.navigate(destination.direction) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                destination.icon,
                                contentDescription = stringResource(destination.title),
                            )
                        },
                        label = { Text(stringResource(destination.title)) },
                    )
                }
            },
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.HomeTimeline.direction.route,
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
                allScreens.forEach { screen ->
                    composable(screen.direction) {
                        Router(screen.navGraph, screen.direction) {
                            dependency(screen)
                        }
                    }
                }
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
