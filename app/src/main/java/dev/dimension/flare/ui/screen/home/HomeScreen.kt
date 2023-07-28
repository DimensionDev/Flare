package dev.dimension.flare.ui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.placeholder.material3.placeholder
import dev.dimension.flare.data.repository.UiAccount
import dev.dimension.flare.data.repository.activeAccountPresenter
import dev.dimension.flare.data.repository.mastodonUserDataPresenter
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.UiState
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.flatMap
import dev.dimension.flare.ui.screen.profile.ProfileScreen
import dev.dimension.flare.ui.theme.FlareTheme

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val showAppbar: Boolean = true
) {
    object HomeTimeline : Screen("HomeTimeline", "Home", Icons.Default.Home)
    object Notification : Screen("Notification", "Notification", Icons.Default.Notifications)
    object Me : Screen("Me", "Me", Icons.Default.AccountCircle, showAppbar = false)
}

private val items = listOf(
    Screen.HomeTimeline,
    Screen.Notification,
    Screen.Me,
)

@Composable
@Preview(showBackground = true)
fun HomeScreenPreview() {
    HomeScreen(toCompose = {})
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    toCompose: () -> Unit,
) {
    val state by producePresenter {
        HomePresenter()
    }
    val navController = rememberNavController()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentScreen = remember(currentDestination) {
        items.find { it.route == currentDestination?.route }
    }
    FlareTheme {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            floatingActionButton = {
                AnimatedVisibility(
                    currentScreen == Screen.HomeTimeline,
                    enter = scaleIn(),
                    exit = scaleOut(),
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
                TopAppBar(
                    title = {
                        currentScreen?.let {
                            AnimatedContent(
                                targetState = it,
                                label = "Title",
                                transitionSpec = {
                                    slideInVertically { it } togetherWith slideOutVertically { -it }
                                }
                            ) {
                                Text(text = it.title)
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
                                    onClick = {
                                    }
                                ) {
                                    NetworkImage(
                                        model = user.data.avatarUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                    )
                                }
                            }
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = null) },
                            label = { Text(screen.title) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        ) {
            NavHost(
                navController = navController,
                startDestination = "HomeTimeline",
                modifier = Modifier
                    .padding(it)
                    .consumeWindowInsets(WindowInsets.systemBars),
                enterTransition = {
                    fadeIn() + slideInHorizontally { it / 4 }
                },
                exitTransition = {
                    fadeOut() + slideOutHorizontally { -it / 4 }
                }
            ) {
                composable(Screen.HomeTimeline.route) {
                    HomeTimelineScreen()
                }
                composable(Screen.Notification.route) {
                    NotificationScreen()
                }
                composable(Screen.Me.route) {
                    when (val data = state.user) {
                        is UiState.Error -> Unit
                        is UiState.Loading -> Unit
                        is UiState.Success -> ProfileScreen(
                            userKey = data.data.userKey,
                            showTopBar = false,
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun HomePresenter() = run {
    val account by activeAccountPresenter()
    val user = account.flatMap {
        when (it) {
            is UiAccount.Mastodon -> {
                val state by mastodonUserDataPresenter(account = it)
                state
            }
        }
    }
    object {
        val user = user
    }
}
