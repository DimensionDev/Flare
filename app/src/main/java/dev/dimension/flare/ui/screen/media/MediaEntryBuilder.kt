package dev.dimension.flare.ui.screen.media

import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import dev.dimension.flare.ui.route.Route
import org.koin.compose.koinInject

internal fun EntryProviderBuilder<NavKey>.mediaEntryBuilder(
    navigate: (Route) -> Unit,
    onBack: () -> Unit,
) {
    entry<Route.Media.Image> { args ->
        MediaScreen(
            uri = args.uri,
            previewUrl = args.previewUrl,
            onDismiss = onBack,
        )
    }

    entry<Route.Media.StatusMedia> { args ->
        StatusMediaScreen(
            statusKey = args.statusKey,
            accountType = args.accountType,
            index = args.index,
            preview = args.preview,
            onDismiss = onBack,
            toAltText = { media ->
                media.description?.let { navigate(Route.Status.AltText(it)) }
            },
            playerPool = koinInject(),
        )
    }

    entry<Route.Media.Podcast> { args ->
        PodcastScreen(
            accountType = args.accountType,
            id = args.id,
            toUser = { userKey ->
                navigate(Route.Profile.User(args.accountType, userKey))
            },
        )
    }
}