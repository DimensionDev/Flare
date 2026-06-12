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
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.SquareRss
import compose.icons.fontawesomeicons.solid.List
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.toUiTimelineTabItem
import dev.dimension.flare.data.platform.MisskeyPlatformSpec
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.route.Route
import dev.dimension.flare.ui.screen.home.TimelineScreen

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
internal fun EntryProviderScope<NavKey>.misskeyEntryBuilder(
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
                MisskeyPlatformSpec.antennaTimelineSpec.candidate(
                    data =
                        TimelineSpec.AccountResourceData(
                            accountKey = (args.accountType as AccountType.Specific).accountKey,
                            resourceId = args.antennaId,
                        ),
                    title = UiText.Raw(args.title),
                    icon = IconType.Material(UiIcon.Rss),
                ).toUiTimelineTabItem()
            },
            onBack = onBack,
        )
    }

    entry<Route.Misskey.ChannelList>(
        metadata = ListDetailSceneStrategy.listPane(
            sceneKey = "misskey_channels_list",
            detailPlaceholder = {
                ChannelsPlaceholder()
            }
        )
    ) { args ->
        ChannelListScreen(
            accountType = args.accountType,
            toTimeline = {
                navigate(Route.Misskey.ChannelTimeline(args.accountType, it.id, it.title))
            },
            onBack = onBack
        )
    }

    entry<Route.Misskey.ChannelTimeline>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "misskey_channels_list",
        )
    ) { args ->
        TimelineScreen(
            tabItem = remember(args) {
                MisskeyPlatformSpec.channelTimelineSpec.candidate(
                    data =
                        TimelineSpec.AccountResourceData(
                            accountKey = (args.accountType as AccountType.Specific).accountKey,
                            resourceId = args.channelId,
                        ),
                    title = UiText.Raw(args.title),
                    icon = IconType.Material(UiIcon.List),
                ).toUiTimelineTabItem()
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

@Composable
internal fun ChannelsPlaceholder(
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
                FontAwesomeIcons.Solid.List,
                contentDescription = null,
                modifier = Modifier.size(64.dp)
            )
        }
    }
}
