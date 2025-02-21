package dev.dimension.flare.ui.route

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.konyaco.fluent.component.Text
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.screen.home.TimelineScreen
import dev.dimension.flare.ui.screen.serviceselect.ServiceSelectScreen
import kotlinx.collections.immutable.persistentMapOf
import kotlin.reflect.KType
import kotlin.reflect.typeOf

private val typeMaps =
    persistentMapOf(
        typeOf<MicroBlogKey>() to MicroblogKeyNavType,
        typeOf<AccountType>() to JsonSerializableNavType(AccountType.serializer()),
        typeOf<TimelineTabItem>() to JsonSerializableNavType(TimelineTabItem.serializer()),
    )

@OptIn(ExperimentalComposeUiApi::class)
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
        typeMap = typeMaps,
    ) {
        screen<Route.Timeline> { (_, args) ->
            TimelineScreen(args.tabItem)
        }
        screen<Route.Discover> {
            Text("Discover")
        }
        screen<Route.Settings> {
            Text("Settings")
        }
        screen<Route.Rss> {
            Text("Rss")
        }
        screen<Route.Profile> {
        }
        screen<Route.MeRoute> {
        }
        screen<Route.ServiceSelect> {
            ServiceSelectScreen(
                onBack = navController::navigateUp,
                onVVO = {
                },
                onXQT = {
                },
            )
        }
        screen<Route.AllLists> {
        }
        screen<Route.BlueskyFeeds> {
        }
        screen<Route.DirectMessage> {
        }

        screen<Route.Notification> {
        }
    }
}

private inline fun <reified T : Any> NavGraphBuilder.screen(
    typeMap: Map<KType, NavType<*>> = typeMaps,
    deepLinks: List<NavDeepLink> = emptyList(),
    noinline enterTransition: (
        AnimatedContentTransitionScope<NavBackStackEntry>.() -> @JvmSuppressWildcards
        EnterTransition?
    )? =
        null,
    noinline exitTransition: (
        AnimatedContentTransitionScope<NavBackStackEntry>.() -> @JvmSuppressWildcards
        ExitTransition?
    )? =
        null,
    noinline popEnterTransition: (
        AnimatedContentTransitionScope<NavBackStackEntry>.() -> @JvmSuppressWildcards
        EnterTransition?
    )? =
        enterTransition,
    noinline popExitTransition: (
        AnimatedContentTransitionScope<NavBackStackEntry>.() -> @JvmSuppressWildcards
        ExitTransition?
    )? =
        exitTransition,
    noinline sizeTransform: (
        AnimatedContentTransitionScope<NavBackStackEntry>.() -> @JvmSuppressWildcards
        SizeTransform?
    )? =
        null,
    noinline content: @Composable AnimatedContentScope.(Pair<NavBackStackEntry, T>) -> Unit,
) {
    composable<T>(
        typeMap = typeMap,
        deepLinks = deepLinks,
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition,
        sizeTransform = sizeTransform,
    ) {
        val args =
            remember(it) {
                it.toRoute<T>()
            }
        content(it to args)
    }
}
