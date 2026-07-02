package dev.dimension.flare.ui.screen.media

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.dimension.flare.ui.component.DialogSceneStrategy2
import dev.dimension.flare.ui.component.BottomSheetSceneStrategy
import dev.dimension.flare.ui.route.Route

@OptIn(ExperimentalMaterial3Api::class)
internal fun EntryProviderScope<NavKey>.mediaEntryBuilder(
    navigate: (Route) -> Unit,
    onBack: () -> Unit,
    uriHandler: UriHandler,
) {
    entry<Route.Media.Image>(
        metadata = DialogSceneStrategy2.dialog(
            dialogProperties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            )
        )
    ) { args ->
        MediaScreen(
            uri = args.uri,
            previewUrl = args.previewUrl,
            customHeaders = args.customHeaders,
            onDismiss = onBack,
            toAltText = { media ->
                media.description?.let { navigate(Route.Status.AltText(it)) }
            },
        )
    }

    entry<Route.Media.RawMedia>(
        metadata = DialogSceneStrategy2.dialog(
            dialogProperties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            )
        )
    ) { args ->
        RawMediaScreen(
            medias = args.medias,
            index = args.index,
            preview = args.preview,
            onDismiss = onBack,
            toAltText = { media ->
                media.description?.let { navigate(Route.Status.AltText(it)) }
            },
            uriHandler = uriHandler,
        )
    }

    entry<Route.Media.StatusMedia>(
        metadata = DialogSceneStrategy2.dialog(
            dialogProperties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            )
        )
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
            uriHandler = uriHandler
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
