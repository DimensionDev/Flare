package dev.dimension.flare.ui.screen.rss

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.SquareRss
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.RssTimelineTabItem
import dev.dimension.flare.data.model.TabMetaData
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.ui.component.BottomSheetSceneStrategy
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.route.Route
import dev.dimension.flare.ui.screen.home.TimelineScreen

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
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
                navigate(
                    Route.Rss.Timeline(
                        id = it.id,
                        title = it.title,
                        url = it.url,
                    )
                )
            },
            onBack = onBack,
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
        TimelineScreen(
            tabItem = remember {
                RssTimelineTabItem(
                    feedUrl = args.url,
                    metaData = TabMetaData(
                        title = TitleType.Text(args.title ?: args.url),
                        icon = IconType.Url(
                            url = UiRssSource.favIconUrl(args.url),
                        )
                    )
                )
            },
            onBack = onBack,
        )
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
        metadata = BottomSheetSceneStrategy.bottomSheet()
    ) {
        RssSourceEditSheet(
            onDismissRequest = onBack,
            id = null,
        )
    }
    entry<Route.Rss.Edit>(
        metadata = BottomSheetSceneStrategy.bottomSheet()
    ) { args ->
        RssSourceEditSheet(
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
