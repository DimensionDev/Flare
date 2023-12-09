package dev.dimension.flare.ui

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.navigation.material.ExperimentalMaterialNavigationApi
import com.google.accompanist.navigation.material.ModalBottomSheetLayout
import com.google.accompanist.navigation.material.rememberBottomSheetNavigator
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.animations.rememberAnimatedNavHostEngine
import com.ramcosta.composedestinations.navigation.navigate
import com.ramcosta.composedestinations.spec.DirectionDestinationSpec
import com.ramcosta.composedestinations.spec.NavGraphSpec
import dev.dimension.flare.R
import dev.dimension.flare.data.model.AppearanceSettings
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.ui.screen.NavGraphs
import dev.dimension.flare.ui.screen.appCurrentDestinationAsState
import dev.dimension.flare.ui.screen.destinations.DiscoverSearchRouteDestination
import dev.dimension.flare.ui.screen.destinations.HomeRouteDestination
import dev.dimension.flare.ui.screen.destinations.MeRouteDestination
import dev.dimension.flare.ui.screen.destinations.NotificationRouteDestination
import dev.dimension.flare.ui.screen.destinations.SplashRouteDestination
import dev.dimension.flare.ui.screen.startAppDestination
import org.koin.compose.rememberKoinInject


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
            Icons.Default.Notifications
        )

    data object Discover :
        Screen(NavGraphs.root, DiscoverSearchRouteDestination, R.string.home_tab_discover_title, Icons.Default.Search)

    data object Me : Screen(NavGraphs.root, MeRouteDestination, R.string.home_tab_me_title, Icons.Default.AccountCircle)
}

private val items =
    listOf(
        Screen.HomeTimeline,
        Screen.Notification,
        Screen.Discover,
        Screen.Me,
    )


@OptIn(ExperimentalMaterialNavigationApi::class, ExperimentalAnimationApi::class,
    ExperimentalMaterial3AdaptiveNavigationSuiteApi::class
)
@Composable
fun AppContainer(
    modifier: Modifier = Modifier,
) {
    val settingsRepository = rememberKoinInject<SettingsRepository>()
    val appearanceSettings by settingsRepository.appearanceSettings.collectAsState(AppearanceSettings())
    val bottomSheetNavigator = rememberBottomSheetNavigator()
    val navController = rememberNavController(bottomSheetNavigator)
    val currentDestination = navController.appCurrentDestinationAsState().value
        ?: NavGraphs.root.startAppDestination
    CompositionLocalProvider(
        LocalAppearanceSettings provides appearanceSettings,
    ) {
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
            }
        ) {
            ModalBottomSheetLayout(
                bottomSheetNavigator = bottomSheetNavigator
            ) {
                DestinationsNavHost(
                    navController = navController,
                    navGraph = NavGraphs.root,
                    startRoute = SplashRouteDestination,
                    engine = rememberAnimatedNavHostEngine()
                )
            }
        }
    }
}
