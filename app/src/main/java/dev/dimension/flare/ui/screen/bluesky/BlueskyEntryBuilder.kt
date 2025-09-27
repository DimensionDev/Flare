package dev.dimension.flare.ui.screen.bluesky

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import dev.dimension.flare.ui.route.Route

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal fun EntryProviderBuilder<NavKey>.blueskyEntryBuilder(
    navigate: (Route) -> Unit,
    onBack: () -> Unit
) {
    entry<Route.Bluesky.Feed>(
        metadata = ListDetailSceneStrategy.listPane(
            sceneKey = "BlueskyFeed",
            detailPlaceholder = {
                BlueskyFeedDetailPlaceholder()
            }
        )
    ) { args ->
        BlueskyFeedsScreen(
            accountType = args.accountType,
            toFeed = { uiList ->
                navigate(Route.Bluesky.FeedDetail(args.accountType, uiList.id))
            },
            onBack = onBack,
        )
    }

    entry<Route.Bluesky.FeedDetail>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "BlueskyFeed",
        )
    ) { args ->
        BlueskyFeedScreen(
            accountType = args.accountType,
            uri = args.feedId,
            onBack = onBack
        )
    }
}
