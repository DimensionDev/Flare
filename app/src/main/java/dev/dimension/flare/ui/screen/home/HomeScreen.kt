package dev.dimension.flare.ui.screen.home

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.dimension.flare.R
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.component.placeholder.placeholder
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.home.ActiveAccountPresenter
import dev.dimension.flare.ui.screen.destinations.ComposeRouteDestination
import dev.dimension.flare.ui.screen.destinations.ProfileRouteDestination
import dev.dimension.flare.ui.screen.destinations.QuickMenuDialogRouteDestination
import dev.dimension.flare.ui.screen.profile.ProfileScreen
import kotlin.math.roundToInt

sealed class Screen(
    val route: String,
    @StringRes val title: Int,
    val icon: ImageVector,
) {
    data object HomeTimeline :
        Screen("HomeTimeline", R.string.home_tab_home_title, Icons.Default.Home)

    data object Notification :
        Screen("Notification", R.string.home_tab_notifications_title, Icons.Default.Notifications)

    data object Discover :
        Screen("Discover", R.string.home_tab_discover_title, Icons.Default.Search)

    data object Me : Screen("Me", R.string.home_tab_me_title, Icons.Default.AccountCircle)
}

private val items =
    listOf(
        Screen.HomeTimeline,
        Screen.Notification,
        Screen.Discover,
        Screen.Me,
    )

@Composable
@Preview(showBackground = true)
fun HomeScreenPreview() {
    HomeScreen(
        toCompose = {},
        toUser = {},
        toQuickMenu = {},
    )
}

@Destination(
    wrappers = [ThemeWrapper::class],
)
@Composable
fun HomeRoute(navigator: DestinationsNavigator) {
    HomeScreen(
        toCompose = {
            navigator.navigate(ComposeRouteDestination)
        },
        toUser = {
            navigator.navigate(ProfileRouteDestination(it))
        },
        toQuickMenu = {
            navigator.navigate(QuickMenuDialogRouteDestination)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    toCompose: () -> Unit,
    toUser: (MicroBlogKey) -> Unit,
    toQuickMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by producePresenter {
        homePresenter()
    }
    val discoverSearchState by producePresenter("discoverSearchPresenter") { discoverSearchPresenter() }
    val navController = rememberNavController()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentScreen =
        remember(currentDestination) {
            items.find { it.route == currentDestination?.route }
        }

    val bottomBarHeightPx =
        with(LocalDensity.current) {
            val navigationBar = WindowInsets.navigationBars
            remember(navigationBar) {
                80.0.dp.roundToPx().toFloat() + navigationBar.getBottom(this)
            }
        }
    var bottomBarOffsetHeightPx by rememberSaveable { mutableFloatStateOf(0f) }
    val nestedScrollConnection =
        remember(bottomBarHeightPx) {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    val delta = available.y
                    val newOffset = bottomBarOffsetHeightPx + delta
                    bottomBarOffsetHeightPx = newOffset.coerceIn(-bottomBarHeightPx, 0f)

                    return Offset.Zero
                }
            }
        }

    Scaffold(
        modifier =
            modifier
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .nestedScroll(nestedScrollConnection),
        floatingActionButton = {
            AnimatedVisibility(
                currentScreen == Screen.HomeTimeline && bottomBarOffsetHeightPx > -(bottomBarHeightPx / 2),
                enter = scaleIn(),
                exit = scaleOut(),
                modifier =
                    Modifier
                        .offset { IntOffset(x = 0, y = -bottomBarOffsetHeightPx.roundToInt()) },
            ) {
                FloatingActionButton(
                    onClick = {
                        toCompose.invoke()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                    )
                }
            }
        },
        topBar = {
            if (currentScreen == Screen.Discover) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    DiscoverSearch(user = state.user, state = discoverSearchState, onAccountClick = toQuickMenu)
                }
            } else {
                TopAppBar(
                    title = {
                        currentScreen?.let {
                            AnimatedContent(
                                targetState = it,
                                label = "Title",
                            ) {
                                Text(text = stringResource(id = it.title))
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        when (val user = state.user) {
                            is UiState.Error -> Unit
                            is UiState.Loading -> {
                                IconButton(
                                    onClick = {
                                    },
                                ) {
                                    Icon(
                                        Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        modifier = Modifier.placeholder(true, shape = CircleShape),
                                    )
                                }
                            }

                            is UiState.Success -> {
                                IconButton(
                                    onClick = toQuickMenu,
                                ) {
                                    NetworkImage(
                                        model = user.data.avatarUrl,
                                        contentDescription = null,
                                        modifier =
                                            Modifier
                                                .size(24.dp)
                                                .clip(CircleShape),
                                    )
                                }
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            NavigationBar(
                modifier =
                    Modifier
                        .offset { IntOffset(x = 0, y = -bottomBarOffsetHeightPx.roundToInt()) },
            ) {
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(stringResource(id = screen.title)) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        },
    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.HomeTimeline.route,
            modifier =
                Modifier
                    .consumeWindowInsets(WindowInsets.systemBars),
        ) {
            composable(Screen.HomeTimeline.route) {
                HomeTimelineScreen(contentPadding)
            }
            composable(Screen.Notification.route) {
                NotificationScreen(contentPadding)
            }
            composable(Screen.Me.route) {
                when (state.user) {
                    is UiState.Error -> Unit
                    is UiState.Loading -> Unit
                    is UiState.Success ->
                        ProfileScreen(
                            showTopBar = false,
                            contentPadding = contentPadding,
                        )
                }
            }
            composable(Screen.Discover.route) {
                DiscoverScreen(
                    contentPadding,
                    onUserClick = toUser,
                    onHashtagClick = {
                        discoverSearchState.setQuery(it)
                        discoverSearchState.search(it)
                        discoverSearchState.setSearching(true)
                        discoverSearchState.setCommited(true)
                    },
                )
            }
        }
    }
}

@Composable
private fun homePresenter() =
    run {
        remember { ActiveAccountPresenter() }.invoke()
    }
