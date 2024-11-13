package dev.dimension.flare.ui.component

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CirclePause
import compose.icons.fontawesomeicons.solid.CirclePlay
import dev.dimension.flare.common.observeState
import kotlin.math.roundToLong

@OptIn(UnstableApi::class)
@Composable
fun AudioPlayer(
    uri: String,
    previewUri: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player =
        remember {
            ExoPlayer
                .Builder(context)
                .build()
                .apply {
                    setMediaItem(MediaItem.fromUri(uri))
                }
        }
    val state = player.observeState()
    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }
    Row(
        modifier = modifier,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        if (previewUri != null) {
            NetworkImage(
                model = previewUri,
                contentDescription = contentDescription,
                modifier =
                    Modifier
                        .size(48.dp),
            )
        }
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            if (state.loading) {
                CircularProgressIndicator(
                    modifier =
                        Modifier
                            .padding(horizontal = 8.dp)
                            .size(24.dp),
                )
            } else {
                IconButton(
                    onClick = {
                        if (state.playing) {
                            player.pause()
                        } else {
                            player.prepare()
                            player.play()
                        }
                    },
                ) {
                    FAIcon(
                        if (state.playing) {
                            FontAwesomeIcons.Solid.CirclePause
                        } else {
                            FontAwesomeIcons.Solid.CirclePlay
                        },
                        contentDescription = null,
                    )
                }
            }
            AnimatedVisibility(state.duration > 0) {
                var progress by remember(state.progress) { mutableFloatStateOf(state.progress) }
                Slider(
                    progress,
                    onValueChange = {
                        progress = it
                    },
                    onValueChangeFinished = {
                        player.seekTo((progress * state.duration).roundToLong())
                    },
                    enabled = !state.loading,
                )
            }
        }
    }
}
