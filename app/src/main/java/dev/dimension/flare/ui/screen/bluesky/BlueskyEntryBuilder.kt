package dev.dimension.flare.ui.screen.bluesky

import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import dev.dimension.flare.ui.route.Route

internal fun EntryProviderBuilder<NavKey>.blueskyEntryBuilder(
    navigate: (Route) -> Unit,
    onBack: () -> Unit
) {
    entry<Route.Bluesky.Feed> { args ->
        BlueskyFeedsScreen(
            accountType = args.accountType,
            toFeed = { uiList ->
                navigate(Route.Bluesky.FeedDetail(args.accountType, uiList.id))
            }
        )
    }

    entry<Route.Bluesky.FeedDetail> { args ->
        BlueskyFeedScreen(
            accountType = args.accountType,
            uri = args.feedId,
            onBack = onBack
        )
    }
}
