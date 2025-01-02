package dev.dimension.flare.ui.route

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.konyaco.fluent.component.Text
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.screen.home.HomeTimelineScreen
import kotlinx.collections.immutable.persistentMapOf
import kotlin.reflect.typeOf

private val typeMap =
    persistentMapOf(
        typeOf<MicroBlogKey>() to MicroblogKeyNavType,
    )

@Composable
internal fun Router(
    startDestination: Route,
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        modifier = modifier,
        startDestination = startDestination,
        typeMap = typeMap,
    ) {
        composable<Route.Home> {
            HomeTimelineScreen()
        }
        composable<Route.Discover> {
            Text("Discover")
        }
        composable<Route.Settings> {
            Text("Settings")
        }
        composable<Route.Profile>(
            typeMap = typeMap,
        ) {
        }
    }
}
