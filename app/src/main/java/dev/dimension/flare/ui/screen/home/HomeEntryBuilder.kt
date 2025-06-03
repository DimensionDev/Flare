package dev.dimension.flare.ui.screen.home

import androidx.compose.material3.DrawerState
import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import dev.dimension.flare.ui.route.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun EntryProviderBuilder<NavKey>.homeEntryBuilder(
    navigate: (Route) -> Unit,
    onBack: () -> Unit,
    scope: CoroutineScope,
    drawerState: DrawerState,
) {
    entry<Route.Home> { args ->
        HomeTimelineScreen(
            accountType = args.accountType,
            toCompose = {
                navigate(Route.Compose.New(args.accountType))
            },
            toQuickMenu = {
                scope.launch {
                    drawerState.open()
                }
            },
            toLogin = {
                navigate(Route.ServiceSelect.Selection)
            },
            toTabSettings = {
                navigate(Route.TabSettings(args.accountType))
            },
        )
    }
    entry<Route.Timeline> { args ->
        TimelineScreen(
            tabItem = args.tabItem,
            toCompose = {
                navigate(Route.Compose.New(accountType = args.accountType))
            },
            toQuickMenu = {
                scope.launch {
                    drawerState.open()
                }
            },
            toLogin = {
                navigate(Route.ServiceSelect.Selection)
            },
        )
    }
    entry<Route.Discover> { args ->
        DiscoverScreen(
            accountType = args.accountType,
            onUserClick = { navigate(Route.Profile.User(args.accountType, it)) },
            onAccountClick = {
                scope.launch {
                    drawerState.open()
                }
            },
        )
    }
    entry<Route.Notification> { args ->
        NotificationScreen(
            accountType = args.accountType,
            toQuickMenu = {
                scope.launch {
                    drawerState.open()
                }
            },
        )
    }
    entry<Route.Search> { args ->
        SearchScreen(
            initialQuery = args.query,
            accountType = args.accountType,
            onAccountClick = {
                scope.launch {
                    drawerState.open()
                }
            },
            onUserClick = { navigate(Route.Profile.User(args.accountType, it)) },
        )
    }
    entry<Route.TabSettings> { args ->
        TabSettingScreen(
            accountType = args.accountType,
            onBack = onBack,
        )
    }
}
