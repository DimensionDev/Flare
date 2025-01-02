package dev.dimension.flare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.konyaco.fluent.FluentTheme
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
import compose.icons.fontawesomeicons.solid.House
import compose.icons.fontawesomeicons.solid.MagnifyingGlass
import compose.icons.fontawesomeicons.solid.Pen
import compose.icons.fontawesomeicons.solid.UserPlus
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.ActiveAccountPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.route.HomeTabRoute
import dev.dimension.flare.ui.route.Route
import dev.dimension.flare.ui.route.Router
import kotlinx.collections.immutable.persistentListOf
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource

private val menus =
    persistentListOf(
        HomeTabRoute(
            route = Route.Home,
            routeClass = Route.Home::class,
            title = Res.string.home_timeline,
            icon = FontAwesomeIcons.Solid.House,
        ),
        HomeTabRoute(
            route = Route.Discover,
            routeClass = Route.Discover::class,
            title = Res.string.home_discover,
            icon = FontAwesomeIcons.Solid.MagnifyingGlass,
        ),
    )

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
internal fun FlareApp() {
    val state by producePresenter { presenter() }
    val windowInfo = calculateWindowSizeClass()
    val bigScreen = windowInfo.widthSizeClass >= WindowWidthSizeClass.Medium
    val displayMode =
        if (bigScreen) {
            NavigationDisplayMode.Left
        } else {
            NavigationDisplayMode.LeftCompact
        }
    var selectedIndex by remember { mutableStateOf(0) }
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentEntry?.destination

    fun navigate(route: Route) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }
    LaunchedEffect(selectedIndex) {
        navigate(menus[selectedIndex].route)
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
                            onClick = {},
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
                                    modifier = Modifier.size(36.dp),
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
                            onClick = {},
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
            menus.forEachIndexed { index, menu ->
                menuItem(
                    selected = currentDestination?.hierarchy?.any { it.hasRoute(menu.routeClass) } == true,
                    onClick = { selectedIndex = index },
                    icon = {
                        Icon(menu.icon, contentDescription = stringResource(menu.title))
                    },
                    text = {
                        Text(stringResource(menu.title))
                    },
                )
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
        Router(
            startDestination = menus.first().route,
            navController = navController,
        )
    }
}

@Composable
private fun presenter() =
    run {
        remember { ActiveAccountPresenter() }.invoke()
    }
