package dev.dimension.flare.ui.screen.rss

import android.os.Parcelable
import androidx.activity.compose.BackHandler
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.CreateRssSourceRouteDestination
import com.ramcosta.composedestinations.generated.destinations.EditRssSourceRouteDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.theme.rememberNavAnimX
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Destination<RootGraph>(
    wrappers = [ThemeWrapper::class],
)
@Composable
internal fun RssRoute(navigator: DestinationsNavigator) {
    RssScreen(
        navigator = navigator,
    )
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun RssScreen(navigator: DestinationsNavigator) {
    val scope = rememberCoroutineScope()
    val scaffoldNavigator =
        rememberListDetailPaneScaffoldNavigator<RssPaneNavArgs>()
    NavigableListDetailPaneScaffold(
        navigator = scaffoldNavigator,
        listPane = {
            AnimatedPane {
                val navAnimX = rememberNavAnimX()
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = RssRoute.List,
                    enterTransition = navAnimX.enterTransition,
                    exitTransition = navAnimX.exitTransition,
                    popEnterTransition = navAnimX.popEnterTransition,
                    popExitTransition = navAnimX.popExitTransition,
                ) {
                    composable<RssRoute.List> {
                        RssSourcesScreen(
                            onAdd = {
                                navigator.navigate(CreateRssSourceRouteDestination)
                            },
                            onEdit = {
                                navigator.navigate(EditRssSourceRouteDestination(it))
                            },
                            onClicked = {
                                navController.navigate(RssRoute.Timeline(id = it))
                            },
                        )
                    }
                    composable<RssRoute.Timeline> {
                        BackHandler(
                            scaffoldNavigator.canNavigateBack(),
                        ) {
                            scope.launch {
                                scaffoldNavigator.navigateBack()
                            }
                        }
                        val data = it.toRoute<RssRoute.Timeline>()
                        CompositionLocalProvider(
                            LocalUriHandler provides
                                remember {
                                    object : UriHandler {
                                        override fun openUri(uri: String) {
                                            scope.launch {
                                                scaffoldNavigator.navigateTo(
                                                    ListDetailPaneScaffoldRole.Detail,
                                                    RssPaneNavArgs(url = uri),
                                                )
                                            }
                                        }
                                    }
                                },
                        ) {
                            RssTimelineScreen(
                                id = data.id,
                                onBack = {
                                    navController.navigateUp()
                                },
                            )
                        }
                    }
                }
            }
        },
        detailPane = {
            AnimatedPane {
                scaffoldNavigator.currentDestination?.contentKey?.url?.let { url ->
                    RssDetailScreen(
                        url = url,
                        onBack = {
                            scope.launch {
                                scaffoldNavigator.navigateBack()
                            }
                        },
                    )
                }
            }
        },
    )
}

@Serializable
private sealed interface RssRoute {
    @Serializable
    data object List : RssRoute

    @Serializable
    data class Timeline(
        val id: Int,
    ) : RssRoute
}

@Parcelize
private data class RssPaneNavArgs(
    val url: String? = null,
) : Parcelable
