package dev.dimension.flare.common

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import dev.dimension.flare.ui.model.UiPodcast
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow

internal class PodcastManager(
    private val context: Context,
) {
    val player: ExoPlayer by lazy {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                androidx.media3.common.AudioAttributes
                    .Builder()
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .build(),
                true,
            )
        }
    }
    private var session: MediaSession? = null
    private val _currentPodcast = MutableStateFlow<UiPodcast?>(null)
    val currentPodcast: Flow<UiPodcast?> = _currentPodcast.asSharedFlow()

    fun playPodcast(podcast: UiPodcast) {
        stopPodcast()
        _currentPodcast.value = podcast
        player.setMediaItem(
            androidx.media3.common.MediaItem
                .Builder()
                .setUri(podcast.playbackUrl)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata
                        .Builder()
                        .setTitle(podcast.title)
                        .setArtist(podcast.creator.name.innerText)
                        .build(),
                ).build(),
        )
        player.prepare()
        player.playWhenReady = true
        session = MediaSession.Builder(context, player).build()
    }

    fun stopPodcast() {
        session?.release()
        session = null
        player.stop()
        _currentPodcast.value = null
    }

    fun release() {
        session?.release()
        session = null
        player.release()
    }
}
