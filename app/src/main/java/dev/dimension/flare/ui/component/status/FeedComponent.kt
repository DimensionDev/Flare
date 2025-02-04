package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.model.UiTimeline

@Composable
internal fun FeedComponent(
    data: UiTimeline.ItemContent.Feed,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier =
            modifier
                .clickable {
                    uriHandler.openUri(data.url)
                },
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
