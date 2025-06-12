package dev.dimension.flare.ui.screen.media

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import dev.dimension.flare.ui.component.BottomSheetSceneStrategy
import dev.dimension.flare.ui.component.FullScreenSceneStrategy
import dev.dimension.flare.ui.route.Route

@OptIn(ExperimentalMaterial3Api::class)
internal fun EntryProviderBuilder<NavKey>.mediaEntryBuilder(
    navigate: (Route) -> Unit,
    onBack: () -> Unit,
) {
    entry<Route.Media.Image>(
        metadata = FullScreenSceneStrategy.fullScreen()
    ) { args ->
        MediaScreen(
            uri = args.uri,
            previewUrl = args.previewUrl,
            onDismiss = onBack,
        )
    }

    entry<Route.Media.StatusMedia>(
        metadata = FullScreenSceneStrategy.fullScreen()
    ) { args ->
        StatusMediaScreen(
            statusKey = args.statusKey,
            accountType = args.accountType,
            index = args.index,
            preview = args.preview,
            onDismiss = onBack,
            toAltText = { media ->
                media.description?.let { navigate(Route.Status.AltText(it)) }
            },
        )
    }

    entry<Route.Media.Podcast>(
        metadata = BottomSheetSceneStrategy.bottomSheet()
    ) { args ->
        PodcastScreen(
            accountType = args.accountType,
            id = args.id,
            toUser = { userKey ->
                navigate(Route.Profile.User(args.accountType, userKey))
            },
        )
    }
}