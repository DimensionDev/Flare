package dev.dimension.flare.ui.screen.home

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.dimension.flare.ui.component.BottomSheetSceneStrategy
import dev.dimension.flare.ui.route.Route

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
internal fun EntryProviderScope<NavKey>.homeEntryBuilder(
    navigate: (Route) -> Unit,
    onBack: () -> Unit,
    openDrawer: () -> Unit,
) {
    entry<Route.Home>(
        metadata = ListDetailSceneStrategy.listPane(
            "home",
        )
    ) { args ->
        HomeTimelineScreen(
            accountType = args.accountType,
            toCompose = {
                navigate(Route.Compose.New(args.accountType))
            },
            toQuickMenu = {
                openDrawer.invoke()
            },
            toLogin = {
                navigate(Route.ServiceSelect.Selection)
            },
            toTabSettings = {
                navigate(Route.TabSettings)
            },
        )
    }
    entry<Route.Timeline> { args ->
        TimelineScreen(
            tabItem = args.tabItem,
            toLogin = {
                navigate(Route.ServiceSelect.Selection)
            },
            onBack = onBack,
        )
    }
    entry<Route.Discover> { args ->
        DiscoverScreen(
            accountType = args.accountType,
            onUserClick = { navigate(Route.Profile.User(args.accountType, it)) },
            onAccountClick = {
                openDrawer.invoke()
            },
        )
    }
    entry<Route.Notification> { args ->
        NotificationScreen(
            accountType = args.accountType,
            toQuickMenu = {
                openDrawer.invoke()
            },
        )
    }
    entry<Route.Search> { args ->
        SearchScreen(
            initialQuery = args.query,
            accountType = args.accountType,
            onAccountClick = {
                openDrawer.invoke()
            },
            onUserClick = { navigate(Route.Profile.User(args.accountType, it)) },
        )
    }
    entry<Route.TabSettings>(
        metadata = ListDetailSceneStrategy.extraPane(
            "home",
        )
    ) { args ->
        TabSettingScreen(
            onBack = onBack,
            toAddRssSource = {
                navigate(Route.Rss.Create)
            }
        )
    }
    entry<Route.AccountSelection>(
        metadata = BottomSheetSceneStrategy.bottomSheet()
    ) {
        AccountSelectionModal(
            onBack = onBack,
            navigate = navigate,
        )
    }
}
