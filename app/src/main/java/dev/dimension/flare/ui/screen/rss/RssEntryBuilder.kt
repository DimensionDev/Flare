package dev.dimension.flare.ui.screen.rss

import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import androidx.navigation3.ui.DialogSceneStrategy
import dev.dimension.flare.ui.route.Route

internal fun EntryProviderBuilder<NavKey>.rssEntryBuilder(
    navigate: (Route) -> Unit,
    onBack: () -> Unit
) {
    entry<Route.Rss.Sources> {
        RssSourcesScreen(
            onAdd = {
                navigate(Route.Rss.Create)
            },
            onEdit = {
                navigate(Route.Rss.Edit(it))
            },
            onClicked = {
                navigate(Route.Rss.Timeline(it))
            },
        )
    }
    entry<Route.Rss.Timeline> { args ->
        RssTimelineScreen(
            id = args.id,
            onBack = onBack,
        )
    }
    entry<Route.Rss.Detail> { args ->
        RssDetailScreen(
            url = args.url,
            onBack = onBack,
        )
    }
    entry<Route.Rss.Create>(
        metadata = DialogSceneStrategy.dialog()
    ) {
        RssSourceEditDialog(
            onDismissRequest = onBack,
            id = null,
        )
    }
    entry<Route.Rss.Edit>(
        metadata = DialogSceneStrategy.dialog()
    ) { args ->
        RssSourceEditDialog(
            onDismissRequest = onBack,
            id = args.id,
        )
    }
}