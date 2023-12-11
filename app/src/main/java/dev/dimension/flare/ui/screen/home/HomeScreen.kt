package dev.dimension.flare.ui.screen.home

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigation.suite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigation.suite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.navigation.material.ExperimentalMaterialNavigationApi
import com.google.accompanist.navigation.material.ModalBottomSheetLayout
import com.google.accompanist.navigation.material.rememberBottomSheetNavigator
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.animations.rememberAnimatedNavHostEngine
import com.ramcosta.composedestinations.navigation.navigate
import com.ramcosta.composedestinations.spec.DirectionDestinationSpec
import com.ramcosta.composedestinations.spec.NavGraphSpec
import com.ramcosta.composedestinations.utils.composable
import dev.dimension.flare.R
import dev.dimension.flare.ui.screen.NavGraphs
import dev.dimension.flare.ui.screen.appCurrentDestinationAsState
import dev.dimension.flare.ui.screen.destinations.DiscoverRouteDestination
import dev.dimension.flare.ui.screen.destinations.HomeRouteDestination
import dev.dimension.flare.ui.screen.destinations.MeRouteDestination
import dev.dimension.flare.ui.screen.destinations.NotificationRouteDestination
import dev.dimension.flare.ui.theme.FlareTheme

sealed class Screen(
    val navGraph: NavGraphSpec,
    val direction: DirectionDestinationSpec,
    @StringRes val title: Int,
    val icon: ImageVector,
) {
    data object HomeTimeline :
        Screen(NavGraphs.root, HomeRouteDestination, R.string.home_tab_home_title, Icons.Default.Home)

    data object Notification :
        Screen(
            NavGraphs.root,
            NotificationRouteDestination,
            R.string.home_tab_notifications_title,
            Icons.Default.Notifications,
        )

    data object Discover :
        Screen(NavGraphs.root, DiscoverRouteDestination, R.string.home_tab_discover_title, Icons.Default.Search)

    data object Me : Screen(NavGraphs.root, MeRouteDestination, R.string.home_tab_me_title, Icons.Default.AccountCircle)
}

private val items =
    listOf(
        Screen.HomeTimeline,
        Screen.Notification,
        Screen.Discover,
        Screen.Me,
    )

@OptIn(
    ExperimentalMaterialNavigationApi::class,
    ExperimentalAnimationApi::class,
    ExperimentalMaterial3AdaptiveNavigationSuiteApi::class,
)
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val currentDestination =
        navController.appCurrentDestinationAsState().value
            ?: items.first().direction.route
    FlareTheme {
        NavigationSuiteScaffold(
            modifier = modifier,
            navigationSuiteItems = {
                items.forEach { destination ->
                    item(
                        selected = currentDestination == destination.direction,
                        onClick = {
                            navController.navigate(destination.direction) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = stringResource(destination.title)) },
                        label = { Text(stringResource(destination.title)) },
                    )
                }
            },
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.HomeTimeline.direction.route,
            ) {
                items.forEach {
                    composable(it.direction) {
                        val bottomSheetNavigator = rememberBottomSheetNavigator()
                        val innerNavController = rememberNavController(bottomSheetNavigator)
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
                            ModalBottomSheetLayout(
                                bottomSheetNavigator = bottomSheetNavigator,
                            ) {
                                DestinationsNavHost(
                                    navController = innerNavController,
                                    navGraph = it.navGraph,
                                    engine = rememberAnimatedNavHostEngine(),
                                    startRoute = it.direction,
                                )
                            }
                        }
                    }
                }
            }
        }
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
