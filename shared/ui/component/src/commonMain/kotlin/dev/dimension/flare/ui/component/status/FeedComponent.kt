package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.component.platform.isBigScreen
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.theme.PlatformTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding

@Composable
internal fun FeedComponent(
    data: UiTimeline.ItemContent.Feed,
    modifier: Modifier = Modifier,
) {
    val bigScreen = isBigScreen()
    val uriHandler = LocalUriHandler.current
    Column(
        modifier =
            Modifier
                .clickable {
                    uriHandler.openUri(data.url)
                }.let {
                    if (bigScreen) {
                        it
                    } else {
                        it.padding(
                            horizontal = screenHorizontalPadding,
                            vertical = 8.dp,
                        )
                    }
                }.then(modifier),
    ) {
        data.image?.let {
            NetworkImage(
                model = it,
                contentDescription = data.title,
                modifier =
                    Modifier
                        .aspectRatio(16f / 9f)
                        .clip(
                            PlatformTheme.shapes.medium,
                        ),
            )
        }
        Column(
            modifier =
                Modifier
                    .let {
                        if (bigScreen) {
                            it.padding(8.dp)
                        } else {
                            it
                        }
                    },
        ) {
            PlatformText(
                text = data.title,
                style = PlatformTheme.typography.title,
            )
            data.description?.let {
                PlatformText(
                    text = it,
                    style = PlatformTheme.typography.caption,
                    maxLines = 2,
                    color = PlatformTheme.colorScheme.caption,
                )
            }
        }
    }
}
