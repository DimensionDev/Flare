package dev.dimension.flare.ui.screen.rss

import android.os.Parcelable
import androidx.activity.compose.BackHandler
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.CreateRssSourceRouteDestination
import com.ramcosta.composedestinations.generated.destinations.EditRssSourceRouteDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.dimension.flare.ui.component.ThemeWrapper
import kotlinx.parcelize.Parcelize

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
    val scaffoldNavigator =
        rememberListDetailPaneScaffoldNavigator<RssPaneNavArgs>()
    BackHandler(
        scaffoldNavigator.canNavigateBack(),
    ) {
        scaffoldNavigator.navigateBack()
    }
    ListDetailPaneScaffold(
        directive = scaffoldNavigator.scaffoldDirective,
        value = scaffoldNavigator.scaffoldValue,
        listPane = {
            AnimatedPane {
                RssSourcesScreen(
                    onAdd = {
                        navigator.navigate(CreateRssSourceRouteDestination)
                    },
                    onEdit = {
                        navigator.navigate(EditRssSourceRouteDestination(it))
                    },
                    onClicked = {
                        scaffoldNavigator.navigateTo(
                            ListDetailPaneScaffoldRole.Detail,
                            RssPaneNavArgs(it),
                        )
                    },
                )
            }
        },
        detailPane = {
            AnimatedPane {
                scaffoldNavigator.currentDestination?.content?.let { args ->
                    RssTimelineScreen(
                        id = args.id,
                        onBack = {
                            scaffoldNavigator.navigateBack()
                        },
                    )
                }
            }
        },
    )
}

@Parcelize
private data class RssPaneNavArgs(
    val id: Int,
) : Parcelable
