package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.dimension.flare.ui.component.AdaptiveGrid
import dev.dimension.flare.ui.component.status.mastodon.MediaItem
import dev.dimension.flare.ui.model.UiMedia
import kotlinx.collections.immutable.ImmutableList

@Composable
internal fun StatusMediaComponent(
    data: ImmutableList<UiMedia>,
    onMediaClick: (UiMedia) -> Unit,
    modifier: Modifier = Modifier,
) {
    AdaptiveGrid(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium),
        content = {
            data.forEach { media ->
                MediaItem(
                    media = media,
                    modifier = Modifier
                        .clickable {
                            onMediaClick(media)
                        },
                )
            }
        },
    )
}
