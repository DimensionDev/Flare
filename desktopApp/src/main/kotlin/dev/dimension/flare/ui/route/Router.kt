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
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.konyaco.fluent.component.Text
import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.screen.feeds.FeedScreen
import dev.dimension.flare.ui.screen.home.DiscoverScreen
import dev.dimension.flare.ui.screen.home.NotificationScreen
import dev.dimension.flare.ui.screen.home.ProfileScreen
import dev.dimension.flare.ui.screen.home.TimelineScreen
import dev.dimension.flare.ui.screen.list.ListScreen
import dev.dimension.flare.ui.screen.serviceselect.ServiceSelectScreen
import dev.dimension.flare.ui.screen.status.StatusScreen
import dev.dimension.flare.ui.screen.status.VVOCommentScreen
import dev.dimension.flare.ui.screen.status.VVOStatusScreen
import dev.dimension.flare.ui.screen.status.action.AddReactionSheet
import dev.dimension.flare.ui.screen.status.action.BlueskyReportStatusDialog
import dev.dimension.flare.ui.screen.status.action.DeleteStatusConfirmDialog
import dev.dimension.flare.ui.screen.status.action.MastodonReportDialog
import dev.dimension.flare.ui.screen.status.action.MisskeyReportDialog
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
        screen<Route.Discover> { (_, args) ->
            DiscoverScreen(args.accountType)
        }
        screen<Route.Settings> {
            Text("Settings")
        }
        screen<Route.Rss> {
            Text("Rss")
        }
        screen<Route.Profile> { (_, args) ->
            ProfileScreen(
                accountType = args.accountType,
                userKey = args.userKey,
            )
        }
        composable(
            AppDeepLink.Profile.ROUTE,
        ) {
            val userKey = it.arguments?.getString("userKey")?.let(MicroBlogKey::valueOf)
            val accountKey =
                it.arguments
                    ?.getString("accountKey")
                    ?.let(MicroBlogKey::valueOf)
                    ?.let(AccountType::Specific)
            if (userKey != null && accountKey != null) {
                ProfileScreen(
                    accountType = accountKey,
                    userKey = userKey,
                )
            }
        }
        screen<Route.MeRoute> { (_, args) ->
            ProfileScreen(
                accountType = args.accountType,
                userKey = null,
            )
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
        screen<Route.AllLists> { (_, args) ->
            ListScreen(args.accountType)
        }
        screen<Route.BlueskyFeeds> { (_, args) ->
            FeedScreen(args.accountType)
        }
        screen<Route.DirectMessage> {
        }
        screen<Route.Notification> { (_, args) ->
            NotificationScreen(args.accountType)
        }
        dialog(AppDeepLink.Bluesky.ReportStatus.ROUTE) {
            val accountType =
                it.arguments
                    ?.getString("accountKey")
                    ?.let(MicroBlogKey::valueOf)
                    ?.let(AccountType::Specific)
            val statusKey =
                it.arguments
                    ?.getString("statusKey")
                    ?.let(MicroBlogKey::valueOf)
            if (accountType != null && statusKey != null) {
                BlueskyReportStatusDialog(
                    accountType = accountType,
                    statusKey = statusKey,
                    onBack = navController::navigateUp,
                )
            }
        }
        dialog(AppDeepLink.DeleteStatus.ROUTE) {
            val accountType =
                it.arguments
                    ?.getString("accountKey")
                    ?.let(MicroBlogKey::valueOf)
                    ?.let(AccountType::Specific)
            val statusKey =
                it.arguments
                    ?.getString("statusKey")
                    ?.let(MicroBlogKey::valueOf)
            if (accountType != null && statusKey != null) {
                DeleteStatusConfirmDialog(
                    accountType = accountType,
                    statusKey = statusKey,
                    onBack = navController::navigateUp,
                )
            }
        }
        dialog(AppDeepLink.Mastodon.ReportStatus.ROUTE) {
            val accountType =
                it.arguments
                    ?.getString("accountKey")
                    ?.let(MicroBlogKey::valueOf)
                    ?.let(AccountType::Specific)
            val statusKey =
                it.arguments
                    ?.getString("statusKey")
                    ?.let(MicroBlogKey::valueOf)
            val userKey =
                it.arguments
                    ?.getString("userKey")
                    ?.let(MicroBlogKey::valueOf)
            if (accountType != null && statusKey != null && userKey != null) {
                MastodonReportDialog(
                    accountType = accountType,
                    statusKey = statusKey,
                    userKey = userKey,
                    onBack = navController::navigateUp,
                )
            }
        }
        dialog(AppDeepLink.Misskey.ReportStatus.ROUTE) {
            val accountType =
                it.arguments
                    ?.getString("accountKey")
                    ?.let(MicroBlogKey::valueOf)
                    ?.let(AccountType::Specific)
            val statusKey =
                it.arguments
                    ?.getString("statusKey")
                    ?.let(MicroBlogKey::valueOf)
            val userKey =
                it.arguments
                    ?.getString("userKey")
                    ?.let(MicroBlogKey::valueOf)
            if (accountType != null && statusKey != null && userKey != null) {
                MisskeyReportDialog(
                    accountType = accountType,
                    statusKey = statusKey,
                    userKey = userKey,
                    onBack = navController::navigateUp,
                )
            }
        }
        composable(AppDeepLink.StatusDetail.ROUTE) {
            val statusKey =
                it.arguments
                    ?.getString("statusKey")
                    ?.let(MicroBlogKey::valueOf)
            val accountKey =
                it.arguments
                    ?.getString("accountKey")
                    ?.let(MicroBlogKey::valueOf)
                    ?.let(AccountType::Specific)
            if (statusKey != null && accountKey != null) {
                StatusScreen(
                    statusKey = statusKey,
                    onBack = navController::navigateUp,
                    accountType = accountKey,
                )
            }
        }
        composable(AppDeepLink.VVO.CommentDetail.ROUTE) {
            val statusKey =
                it.arguments
                    ?.getString("statusKey")
                    ?.let(MicroBlogKey::valueOf)
            val accountKey =
                it.arguments
                    ?.getString("accountKey")
                    ?.let(MicroBlogKey::valueOf)
                    ?.let(AccountType::Specific)
            if (statusKey != null && accountKey != null) {
                VVOCommentScreen(
                    commentKey = statusKey,
                    onBack = navController::navigateUp,
                    accountType = accountKey,
                )
            }
        }
        composable(AppDeepLink.VVO.StatusDetail.ROUTE) {
            val statusKey =
                it.arguments
                    ?.getString("statusKey")
                    ?.let(MicroBlogKey::valueOf)
            val accountKey =
                it.arguments
                    ?.getString("accountKey")
                    ?.let(MicroBlogKey::valueOf)
                    ?.let(AccountType::Specific)
            if (statusKey != null && accountKey != null) {
                VVOStatusScreen(
                    statusKey = statusKey,
                    onBack = navController::navigateUp,
                    accountType = accountKey,
                )
            }
        }
        dialog(AppDeepLink.AddReaction.ROUTE) {
            val statusKey =
                it.arguments
                    ?.getString("statusKey")
                    ?.let(MicroBlogKey::valueOf)
            val accountKey =
                it.arguments
                    ?.getString("accountKey")
                    ?.let(MicroBlogKey::valueOf)
                    ?.let(AccountType::Specific)
            if (statusKey != null && accountKey != null) {
                AddReactionSheet(
                    statusKey = statusKey,
                    accountType = accountKey,
                    onBack = navController::navigateUp,
                )
            }
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
