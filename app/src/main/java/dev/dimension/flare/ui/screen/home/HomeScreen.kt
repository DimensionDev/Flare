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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import dev.dimension.flare.ui.composeFlatMap
import dev.dimension.flare.ui.theme.FlareTheme

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object HomeTimeline : Screen("HomeTimeline", "Home", Icons.Default.Home)
    object Notification : Screen("Notification", "Notification", Icons.Default.Notifications)
}

private val items = listOf(
    Screen.HomeTimeline,
    Screen.Notification,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
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
//                            navController.navigate("Compose")
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
                        currentScreen?.title?.let {
                            AnimatedContent(
                                targetState = it,
                                label = "Title",
                                transitionSpec = {
                                    slideInVertically { it } togetherWith slideOutVertically { -it }
                                }
                            ) {
                                Text(text = it)
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
                modifier = Modifier.padding(it),
                enterTransition = {
                    fadeIn() + slideInHorizontally { it / 4 }
                },
                exitTransition = {
                    fadeOut() + slideOutHorizontally { -it / 4 }
                }
            ) {
                composable("HomeTimeline") {
                    HomeTimelineScreen()
                }
                composable("Notification") {
                    NotificationScreen()
                }
            }
        }
    }
}


@Composable
private fun HomePresenter() = run {
    val account by activeAccountPresenter()
    val user = account.composeFlatMap {
        when (it) {
            is UiAccount.Mastodon -> {
                val state by mastodonUserDataPresenter(account = it)
                state
            }

            null -> UiState.Error(Throwable("Account is null"))
        }
    }
    object {
        val user = user
    }
}
