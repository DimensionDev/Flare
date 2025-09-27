package dev.dimension.flare.ui.screen.misskey

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
import dev.dimension.flare.data.model.Misskey
import dev.dimension.flare.data.model.TabMetaData
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.route.Route
import dev.dimension.flare.ui.screen.home.TimelineScreen

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
internal fun EntryProviderBuilder<NavKey>.misskeyEntryBuilder(
    navigate: (Route) -> Unit,
    onBack: () -> Unit,
) {
    entry<Route.Misskey.AntennasList>(
        metadata = ListDetailSceneStrategy.listPane(
            sceneKey = "misskey_antennas_list",
            detailPlaceholder = {
                AntennasPlaceholder()
            }
        )
    ) { args ->
        AntennasListScreen(
            accountType = args.accountType,
            toTimeline = {
                navigate(Route.Misskey.AntennaTimeline(args.accountType, it.id, it.title))
            },
            onBack = onBack
        )
    }

    entry<Route.Misskey.AntennaTimeline>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "misskey_antennas_list",
        )
    ) { args ->
        TimelineScreen(
            tabItem = remember(args) {
                Misskey.AntennasTimelineTabItem(
                    account = args.accountType,
                    id = args.antennaId,
                    metaData = TabMetaData(
                        title = TitleType.Text(args.title),
                        icon = IconType.Material(IconType.Material.MaterialIcon.Rss),
                    ),
                )
            },
            onBack = onBack,
        )
    }
}


@Composable
internal fun AntennasPlaceholder(
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
