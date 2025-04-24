package dev.dimension.flare.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dimension.flare.common.PodcastManager
import dev.dimension.flare.ui.model.UiPodcast
import org.koin.compose.koinInject

@Composable
internal fun ColumnScope.PodcastFAB(
    toPodcast: (data: UiPodcast) -> Unit,
    modifier: Modifier = Modifier,
    compat: Boolean = false,
    podcastManager: PodcastManager = koinInject(),
) {
    val podcast by podcastManager.currentPodcast.collectAsState(null)
    AnimatedVisibility(
        podcast != null,
    ) {
        podcast?.let { data ->
            if (compat) {
                FloatingActionButton(
                    modifier = modifier,
                    onClick = {
                        toPodcast(data)
                    },
                ) {
                    AvatarComponent(
                        data.creator.avatar,
                        size = 24.dp,
                        modifier =
                            Modifier
                                .border(
                                    4.dp,
                                    MaterialTheme.colorScheme.primary,
                                    shape = CircleShape,
                                ),
                    )
                }
            } else {
                ExtendedFloatingActionButton(
                    modifier = modifier,
                    onClick = {
                        toPodcast(data)
                    },
                    icon = {
                        AvatarComponent(
                            data.creator.avatar,
                            size = 24.dp,
                            modifier =
                                Modifier
                                    .border(
                                        4.dp,
                                        MaterialTheme.colorScheme.primary,
                                        shape = CircleShape,
                                    ),
                        )
                    },
                    text = {
                        Text(data.title)
                    },
                )
            }
        }
    }
}
