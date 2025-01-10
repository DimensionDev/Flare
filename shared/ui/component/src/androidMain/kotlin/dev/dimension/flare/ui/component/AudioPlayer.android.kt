package dev.dimension.flare.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

@Composable
internal actual fun rememberPlayerState(uri: String): PlayerState {
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
    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }
    return player.observeState()
}

@Composable
private fun Player.observeState(): PlayerState {
    var playing by remember(this) { mutableStateOf(false) }
    var loading by remember(this) { mutableStateOf(false) }
    var position by remember(this) { mutableLongStateOf(0L) }
    var maxDuration by remember(this) { mutableLongStateOf(0L) }

    if (playing) {
        LaunchedEffect(Unit) {
            while (true) {
                position = currentPosition
                delay(1.seconds / 30)
            }
        }
    }
    val listener =
        remember(this) {
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    playing = isPlaying
                }

                override fun onIsLoadingChanged(isLoading: Boolean) {
                    loading = isLoading
                    if (!isLoading) {
                        maxDuration = duration
                    }
                }
            }
        }
    DisposableEffect(this) {
        addListener(listener)
        onDispose {
            removeListener(listener)
        }
    }
    return object : PlayerState {
        override val playing: Boolean
            get() = playing
        override val loading: Boolean
            get() = loading
        override val duration: Long
            get() = maxDuration
        override val progress: Float
            get() = position.toFloat() / maxDuration.toFloat()

        override fun play() {
            this@observeState.prepare()
            this@observeState.play()
        }

        override fun pause() {
            this@observeState.pause()
        }

        override fun seekTo(position: Long) {
            this@observeState.seekTo(position)
        }
    }
}
