package dev.dimension.flare.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CirclePause
import compose.icons.fontawesomeicons.solid.CirclePlay
import dev.dimension.flare.ui.component.platform.PlatformCircularProgressIndicator
import dev.dimension.flare.ui.component.platform.PlatformIconButton
import dev.dimension.flare.ui.component.platform.PlatformSlider
import kotlin.math.roundToLong

@Immutable
internal interface PlayerState {
    val playing: Boolean
    val loading: Boolean
    val duration: Long
    val progress: Float

    fun play()

    fun pause()

    fun seekTo(position: Long)
}

// @Immutable
// internal data class PlayerState(
//    val playing: Boolean,
//    val loading: Boolean,
//    val position: Long,
//    val duration: Long,
// ) {
//    val progress: Float
//        get() =
//            if (duration > 0) {
//                position.toFloat() / duration
//            } else {
//                0f
//            }
// }

@Composable
internal expect fun rememberPlayerState(uri: String): PlayerState

@Composable
internal fun AudioPlayer(
    uri: String,
    previewUri: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
//    val context = LocalContext.current
//    val player =
//        remember {
//            ExoPlayer
//                .Builder(context)
//                .build()
//                .apply {
//                    setMediaItem(MediaItem.fromUri(uri))
//                }
//        }
//    val state = player.observeState()
//    DisposableEffect(player) {
//        onDispose {
//            player.release()
//        }
//    }
    val state = rememberPlayerState(uri)
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
                PlatformCircularProgressIndicator(
                    modifier =
                        Modifier
                            .padding(horizontal = 8.dp)
                            .size(24.dp),
                )
            } else {
                PlatformIconButton(
                    onClick = {
                        if (state.playing) {
                            state.pause()
                        } else {
                            state.play()
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
                PlatformSlider(
                    progress,
                    onValueChange = {
                        progress = it
                    },
                    onValueChangeFinished = {
                        state.seekTo((progress * state.duration).roundToLong())
                    },
                    enabled = !state.loading,
                )
            }
        }
    }
}
