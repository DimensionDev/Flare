package dev.dimension.flare.ui.screen.gallery

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.dimension.flare.ui.route.Route

internal fun EntryProviderScope<NavKey>.galleryEntryBuilder(
    navigate: (Route) -> Unit,
    onBack: () -> Unit,
) {
    entry<Route.Gallery.Detail> { args ->
        GalleryDetailScreen(
            statusKey = args.statusKey,
            accountType = args.accountType,
            navigate = navigate,
            onBack = onBack,
        )
    }

    entry<Route.Gallery.Comments> { args ->
        GalleryCommentsScreen(
            statusKey = args.statusKey,
            accountType = args.accountType,
            onBack = onBack,
        )
    }
}
