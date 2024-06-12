package dev.dimension.flare.common

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.util.lruCache
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

@OptIn(UnstableApi::class)
class PlayerPoll(
    private val context: Context,
) {
    private val players =
        lruCache<String, Player>(
            10,
            create = { uri ->
                ExoPlayer
                    .Builder(context)
                    .build()
                    .apply {
                        setMediaItem(MediaItem.fromUri(uri))
                    }
            },
            onEntryRemoved = { _, _, oldValue, _ ->
                oldValue.release()
            },
        )

    fun get(uri: String): Player = players[uri]

    fun remove(uri: String) {
        players.remove(uri)
    }
}

@Composable
fun Player.observeState(): PlayerState {
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
    return PlayerState(
        playing = playing,
        loading = loading,
        position = position,
        duration = maxDuration,
    )
}

@Immutable
data class PlayerState(
    val playing: Boolean,
    val loading: Boolean,
    val position: Long,
    val duration: Long,
) {
    val progress: Float
        get() =
            if (duration > 0) {
                position.toFloat() / duration
            } else {
                0f
            }
}
