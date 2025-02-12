package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowWidthSizeClass
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.theme.screenHorizontalPadding

@Composable
internal fun FeedComponent(
    data: UiTimeline.ItemContent.Feed,
    modifier: Modifier = Modifier,
) {
    val windowInfo = currentWindowAdaptiveInfo()
    val bigScreen = windowInfo.windowSizeClass.windowWidthSizeClass != WindowWidthSizeClass.COMPACT
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
                            MaterialTheme.shapes.medium,
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
            Text(
                text = data.title,
                style = MaterialTheme.typography.titleMedium,
            )
            data.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
