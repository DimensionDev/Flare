package dev.dimension.flare.ui.screen.rss

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import androidx.navigation3.ui.DialogSceneStrategy
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.SquareRss
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.route.Route

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal fun EntryProviderBuilder<NavKey>.rssEntryBuilder(
    navigate: (Route) -> Unit,
    onBack: () -> Unit
) {
    entry<Route.Rss.Sources>(
        metadata = ListDetailSceneStrategy.listPane(
            sceneKey = "Rss",
            detailPlaceholder = {
                RssPlaceholder()
            },
        )
    ) {
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
    entry<Route.Rss.Timeline>(
        metadata = ListDetailSceneStrategy.listPane(
            sceneKey = "Rss",
            detailPlaceholder = {
                RssPlaceholder()
            },
        )
    ) { args ->
        CompositionLocalProvider(
            LocalUriHandler provides
                    remember {
                        object : UriHandler {
                            override fun openUri(uri: String) {
                                navigate(Route.Rss.Detail(uri))
                            }
                        }
                    },
        ) {
            RssTimelineScreen(
                id = args.id,
                onBack = onBack,
            )
        }
    }
    entry<Route.Rss.Detail>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Rss",
        )
    ) { args ->
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


@Composable
internal fun RssPlaceholder(
    modifier: Modifier = Modifier,
) {
    FlareScaffold(
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.CenterVertically),
        ) {
            FAIcon(
                FontAwesomeIcons.Solid.SquareRss,
                contentDescription = null,
                modifier = Modifier.size(64.dp)
            )
        }
    }
}
