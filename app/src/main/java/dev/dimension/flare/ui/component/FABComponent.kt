package dev.dimension.flare.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateValue
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dimension.flare.common.PodcastManager
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.screen.media.PodcastContent
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ColumnScope.PodcastFAB(
    onVisibilityChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    podcastManager: PodcastManager = koinInject(),
) {
    val podcast by podcastManager.currentPodcast.collectAsState(null)
    LaunchedEffect(podcast) {
        onVisibilityChanged.invoke(podcast != null)
    }
    AnimatedVisibility(
        podcast != null,
        modifier = modifier,
    ) {
        podcast?.let { data ->
            var showInfoSheet by remember { mutableStateOf(false) }
            ExtendedFloatingActionButton(
                shape = MaterialTheme.shapes.large,
                onClick = {
                    showInfoSheet = true
                },
                icon = {
                    Box {
                        AvatarComponent(
                            data.creator.avatar,
                            size = 36.dp,
                            modifier =
                                Modifier
                                    .graphicsLayer()
                                    .border(
                                        2.dp,
                                        MaterialTheme.colorScheme.primary,
                                        shape = CircleShape,
                                    ),
                        )
                        PulsingCircle(
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                },
                text = {
                    Text(data.title)
                },
            )
            if (showInfoSheet) {
                ModalBottomSheet(
                    onDismissRequest = {
                        showInfoSheet = false
                    },
                ) {
                    Column(
                        modifier =
                            Modifier
                                .padding(
                                    horizontal = screenHorizontalPadding,
                                ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PodcastContent(
                            data = data,
                            isPlaying = UiState.Success(true),
                            toUser = {},
                            onJoinPodcast = {
                                podcastManager.playPodcast(data)
                            },
                            onLeavePodcast = {
                                podcastManager.stopPodcast()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PulsingCircle(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val infiniteTransition = rememberInfiniteTransition()

    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 48f / 36f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
    )

    val strokeWidthDp by infiniteTransition.animateValue(
        initialValue = 4.dp,
        targetValue = 0.dp,
        typeConverter = Dp.VectorConverter,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
    )

    val baseSize = 36.dp
    val strokeWidthPx = with(LocalDensity.current) { strokeWidthDp.toPx() }
    val sizePx = with(LocalDensity.current) { baseSize.toPx() }

    Canvas(
        modifier =
            modifier
                .size(baseSize)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                },
    ) {
        drawCircle(
            color = color,
            radius = sizePx / 2 - strokeWidthPx / 2,
            style = Stroke(width = strokeWidthPx),
        )
    }
}
