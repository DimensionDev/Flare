package dev.dimension.flare.common

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.DefaultHlsDataSourceFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import dev.dimension.flare.ui.model.UiPodcast
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow

internal class PodcastManager(
    private val context: Context,
) : AutoCloseable {
    private var currentController: MediaController? = null
    private val _currentPodcast = MutableStateFlow<UiPodcast?>(null)
    val currentPodcast: Flow<UiPodcast?> = _currentPodcast.asSharedFlow()

    @OptIn(UnstableApi::class)
    fun playPodcast(podcast: UiPodcast) {
        stopPodcast()
        _currentPodcast.value = podcast
        val sessionToken =
            SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture =
            MediaController
                .Builder(context, sessionToken)
                .buildAsync()
        controllerFuture.addListener({
            currentController =
                controllerFuture.get().also {
                    it.playWhenReady = true
                    it.setMediaItem(
                        androidx.media3.common.MediaItem
                            .Builder()
                            .setMediaId("podcast_session")
                            .setUri(podcast.playbackUrl)
                            .setMediaMetadata(
                                androidx.media3.common.MediaMetadata
                                    .Builder()
                                    .setTitle(podcast.title)
                                    .setArtist(podcast.creator.name.innerText)
                                    .setArtworkUri(podcast.creator.avatar.toUri())
                                    .build(),
                            ).build(),
                    )
                    it.prepare()
                }
        }, MoreExecutors.directExecutor())
    }

    fun stopPodcast() {
        currentController?.stop()
        currentController?.release()
        currentController = null
        _currentPodcast.value = null
    }

    override fun close() {
        stopPodcast()
    }
}

internal class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onTaskRemoved(rootIntent: Intent?) {
        pauseAllPlayersAndStopSelf()
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val player =
            ExoPlayer
                .Builder(this)
                .setMediaSourceFactory(
                    HlsMediaSource.Factory(DefaultHlsDataSourceFactory(DefaultHttpDataSource.Factory())),
                ).setAudioAttributes(
                    androidx.media3.common.AudioAttributes
                        .Builder()
                        .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                        .build(),
                    true,
                ).build()
        mediaSession =
            MediaSession
                .Builder(this, player)
                .setCommandButtonsForMediaItems(
                    listOf(),
                ).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
