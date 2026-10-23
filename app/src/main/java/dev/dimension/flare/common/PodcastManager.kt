package dev.dimension.flare.common

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.compose.runtime.Stable
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.common.util.concurrent.MoreExecutors
import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.data.network.ProxyNetwork
import dev.dimension.flare.ui.component.Media3VideoCacheManager
import dev.dimension.flare.ui.component.media
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiPodcast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

@Stable
@Single
internal class PodcastManager(
    @Provided private val context: Context,
) : AutoCloseable {
    private var currentController: MediaController? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var artworkJob: Job? = null
    private val _currentPodcast = MutableStateFlow<UiPodcast?>(null)
    val currentPodcast: Flow<UiPodcast?> = _currentPodcast.asSharedFlow()

    @OptIn(UnstableApi::class)
    fun playPodcast(podcast: UiPodcast) {
        val media = podcast.media ?: return
        stopPodcast()
        _currentPodcast.value = podcast
        val sessionToken =
            SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture =
            MediaController
                .Builder(context, sessionToken)
                .buildAsync()
        controllerFuture.addListener({
            val controller = controllerFuture.get()
            if (_currentPodcast.value !== podcast) {
                controller.release()
                return@addListener
            }
            currentController = controller
            controller.also {
                it.playWhenReady = true
                it.setMediaItem(
                    androidx.media3.common.MediaItem
                        .Builder()
                        .setMediaId("podcast_session")
                        .setUri(media.url)
                        .setMediaMetadata(
                            androidx.media3.common.MediaMetadata
                                .Builder()
                                .setTitle(podcast.title)
                                .setArtist(podcast.creator.name.innerText)
                                .setExtras(
                                    android.os.Bundle().apply {
                                        putString(
                                            "flare-media-proxy",
                                            kotlinx.serialization.json.Json
                                                .encodeToString(media.proxy),
                                        )
                                    },
                                ).build(),
                        ).build(),
                )
                it.prepare()
            }
            podcast.creator.avatar?.let { artwork ->
                artworkJob =
                    scope.launch {
                        try {
                            val request =
                                ImageRequest
                                    .Builder(context)
                                    .media(artwork, ProxyNetwork.snapshot(artwork.proxy))
                                    .size(512)
                                    .allowHardware(false)
                                    .build()
                            val result = context.imageLoader.execute(request) as? SuccessResult ?: return@launch
                            val bytes =
                                withContext(Dispatchers.Default) {
                                    java.io.ByteArrayOutputStream().use { output ->
                                        result.image.toBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
                                        output.toByteArray()
                                    }
                                }
                            if (currentController === controller) {
                                controller.currentMediaItem?.let { item ->
                                    controller.replaceMediaItem(
                                        controller.currentMediaItemIndex,
                                        item
                                            .buildUpon()
                                            .setMediaMetadata(
                                                item.mediaMetadata
                                                    .buildUpon()
                                                    .setArtworkData(bytes, androidx.media3.common.MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                                                    .build(),
                                            ).build(),
                                    )
                                }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            // Artwork failure must not interrupt audio or retry outside the selected proxy.
                        }
                    }
            }
        }, MoreExecutors.directExecutor())
    }

    fun stopPodcast() {
        artworkJob?.cancel()
        artworkJob = null
        currentController?.stop()
        currentController?.release()
        currentController = null
        _currentPodcast.value = null
    }

    override fun close() {
        stopPodcast()
        scope.cancel()
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
                    object : androidx.media3.exoplayer.source.MediaSource.Factory {
                        private var drmProvider: androidx.media3.exoplayer.drm.DrmSessionManagerProvider? = null
                        private var errorPolicy: androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy? = null

                        override fun setDrmSessionManagerProvider(
                            provider: androidx.media3.exoplayer.drm.DrmSessionManagerProvider,
                        ): androidx.media3.exoplayer.source.MediaSource.Factory =
                            apply {
                                drmProvider =
                                    provider
                            }

                        override fun setLoadErrorHandlingPolicy(
                            policy: androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy,
                        ): androidx.media3.exoplayer.source.MediaSource.Factory =
                            apply {
                                errorPolicy =
                                    policy
                            }

                        override fun getSupportedTypes(): IntArray = intArrayOf(androidx.media3.common.C.CONTENT_TYPE_HLS)

                        override fun createMediaSource(
                            item: androidx.media3.common.MediaItem,
                        ): androidx.media3.exoplayer.source.MediaSource {
                            val proxy =
                                kotlinx.serialization.json.Json.decodeFromString<ProxyRef>(
                                    requireNotNull(item.mediaMetadata.extras?.getString("flare-media-proxy")),
                                )
                            val media = UiMedia.Audio(requireNotNull(item.localConfiguration).uri.toString(), null, null, proxy = proxy)
                            return org.koin.java.KoinJavaComponent
                                .get<Media3VideoCacheManager>(Media3VideoCacheManager::class.java)
                                .mediaSourceFactory(media)
                                .apply {
                                    drmProvider?.let(::setDrmSessionManagerProvider)
                                    errorPolicy?.let(::setLoadErrorHandlingPolicy)
                                }.createMediaSource(item)
                        }
                    },
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
