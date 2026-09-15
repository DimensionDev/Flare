@file:kotlin.OptIn(ExperimentalFoundationApi::class)

package dev.dimension.flare.ui.component

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CirclePlay
import dev.dimension.flare.compose.ui.Res
import dev.dimension.flare.compose.ui.media_play_video
import dev.dimension.flare.ui.component.status.LocalIsScrollingInProgress
import dev.dimension.flare.ui.theme.PlatformTheme
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.android.awaitFrame
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

private val audioAttributes by lazy {
    AudioAttributes
        .Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .build()
}

@OptIn(UnstableApi::class, ExperimentalFoundationApi::class)
@Composable
public fun VideoPlayer(
    uri: String,
    customHeaders: ImmutableMap<String, String>? = null,
    previewUri: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    showControls: Boolean = false,
    keepScreenOn: Boolean = false,
    aspectRatio: Float? = null,
    contentScale: ContentScale = ContentScale.Crop,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    autoPlay: Boolean = true,
    remainingTimeContent: @Composable (BoxScope.(Long) -> Unit)? = null,
    loadingPlaceholder: @Composable BoxScope.() -> Unit = {
        if (previewUri != null) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                NetworkImage(
                    model = previewUri,
                    customHeaders = customHeaders,
                    contentScale = contentScale,
                    contentDescription = contentDescription,
                    modifier =
                        Modifier
                            .let {
                                if (aspectRatio != null) {
                                    it.aspectRatio(
                                        aspectRatio,
                                        matchHeightConstraintsFirst = aspectRatio > 1f,
                                    )
                                } else {
                                    it
                                }
                            }.fillMaxSize(),
                )
            }
            LinearProgressIndicator(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
            )
        }
    },
    idlePlaceholder: @Composable BoxScope.() -> Unit = {
        if (previewUri != null) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                NetworkImage(
                    model = previewUri,
                    customHeaders = customHeaders,
                    contentScale = contentScale,
                    contentDescription = contentDescription,
                    modifier =
                        Modifier
                            .let {
                                if (aspectRatio != null) {
                                    it.aspectRatio(
                                        aspectRatio,
                                        matchHeightConstraintsFirst = aspectRatio > 1f,
                                    )
                                } else {
                                    it
                                }
                            }.fillMaxSize(),
                )
                Box(
                    modifier =
                        Modifier
                            .padding(16.dp)
                            .background(
                                Color.Black.copy(alpha = 0.5f),
                                shape = PlatformTheme.shapes.medium,
                            ).padding(horizontal = 8.dp, vertical = 4.dp)
                            .align(Alignment.BottomStart),
                    contentAlignment = Alignment.Center,
                ) {
                    FAIcon(
                        FontAwesomeIcons.Solid.CirclePlay,
                        contentDescription = stringResource(Res.string.media_play_video),
                        modifier =
                            Modifier
                                .size(16.dp),
                        tint = Color.White,
                    )
                }
            }
        }
    },
) {
    var resumed by remember { mutableStateOf(false) }
    val playback = rememberTimelinePlayback()
    val request =
        remember(uri, customHeaders) {
            VideoRequest(uri = uri, customHeaders = customHeaders?.toMap().orEmpty())
        }
    val binding = rememberSurfaceBinding(request, playback, muted)
    val player = binding.first
    var isLoaded by remember(binding.second, player) { mutableStateOf(false) }

    LifecycleResumeEffect(binding.second) {
        resumed = true
        onPauseOrDispose {
            resumed = false
            binding.second.setActive(false)
        }
    }
    Box(
        modifier = modifier.timelineVideoAutoplay(playback, binding.second, autoPlay && resumed, request.uri),
    ) {
        if ((!isLoaded && LocalIsScrollingInProgress.current) || player == null) {
            idlePlaceholder()
        } else {
            var remainingTime by remember { mutableLongStateOf(0L) }
            val playerState = rememberPresentationState(player)
            LaunchedEffect(player) {
                while (true) {
                    if (player.playbackState == Player.STATE_READY) isLoaded = true
                    if (remainingTimeContent != null) {
                        remainingTime = player.duration - player.currentPosition
                    }
                    awaitFrame()
                }
            }
            AnimatedContent(
                isLoaded,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
            ) { isLoaded ->
                if (isLoaded) {
                    val playerModifier =
                        Modifier
                            .clipToBounds()
                            .semantics {
                                contentDescription?.let { this.contentDescription = it }
                            }.resizeWithContentScale(
                                contentScale = contentScale,
                                sourceSizeDp = playerState.videoSizeDp,
                            ).let {
                                if (onClick != null) {
                                    it.combinedClickable(
                                        onClick = onClick,
                                        onLongClick = onLongClick,
                                    )
                                } else {
                                    it
                                }
                            }.let {
                                if (keepScreenOn) {
                                    it.keepScreenOn()
                                } else {
                                    it
                                }
                            }.let {
                                if (aspectRatio != null && playerState.videoSizeDp == null) {
                                    it.aspectRatio(aspectRatio)
                                } else {
                                    it
                                }
                            }
                    Box {
                        PlayerSurface(
                            player = player,
                            modifier = playerModifier,
                        )
                        remainingTimeContent?.invoke(this, remainingTime)
                    }
                } else {
                    Box {
                        loadingPlaceholder()
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberSurfaceBinding(
    request: VideoRequest,
    playback: TimelinePlaybackCoordinator,
    muted: Boolean,
): Pair<ExoPlayer?, SurfaceBindingManager.Binding> {
    val manager: SurfaceBindingManager = koinInject()
    var player by remember(request, manager, playback, muted) { mutableStateOf<ExoPlayer?>(null) }
    val binding =
        remember(request, manager, playback, muted) {
            manager.register(request, playback, muted) { player = it }
        }
    DisposableEffect(binding, playback) {
        playback.register(binding, binding::setActive)
        onDispose {
            playback.remove(binding)
            binding.dispose()
        }
    }
    return player to binding
}

internal data class VideoRequest(
    val uri: String,
    val customHeaders: Map<String, String> = emptyMap(),
)

@Stable
@Single
public class SurfaceBindingManager(
    @Provided private val context: Context,
    private val media3VideoCacheManager: Media3VideoCacheManager,
) {
    private val playerDelegate =
        lazy {
            ExoPlayer
                .Builder(context.applicationContext)
                .setMediaSourceFactory(media3VideoCacheManager.mediaSourceFactory())
                .build()
                .apply {
                    repeatMode = Player.REPEAT_MODE_ALL
                    volume = 0f
                }
        }
    public val player: ExoPlayer by playerDelegate
    private var activeBinding: Binding? by mutableStateOf(null)
    private var activeRequest: Pair<TimelinePlaybackCoordinator, VideoRequest>? by mutableStateOf(null)

    public fun playerFor(uri: String): ExoPlayer? = if (activeBinding != null && activeRequest?.second?.uri == uri) player else null

    public interface Binding {
        public fun setActive(active: Boolean)

        public fun dispose()
    }

    internal fun register(
        request: VideoRequest,
        playback: TimelinePlaybackCoordinator,
        muted: Boolean,
        callback: (ExoPlayer?) -> Unit,
    ): Binding {
        playback.onClose(this) { release(playback) }
        return object : Binding {
            override fun setActive(active: Boolean) {
                if (active) {
                    if (activeBinding === this) return
                    activeBinding?.setActive(false)
                    activeBinding = this
                    if (activeRequest != (playback to request)) {
                        val source =
                            media3VideoCacheManager
                                .mediaSourceFactory(request.customHeaders)
                                .createMediaSource(MediaItem.fromUri(request.uri))
                        player.setMediaSource(source)
                        player.seekTo((playback.position(request.uri) * 1000).toLong())
                        player.prepare()
                        activeRequest = playback to request
                    }
                    player.setPlaybackSpeed(1f)
                    player.volume = if (muted) 0f else 1f
                    player.setAudioAttributes(audioAttributes, !muted)
                    player.play()
                    callback(player)
                } else if (activeBinding === this) {
                    if (player.playbackState != Player.STATE_IDLE && player.playerError == null) {
                        playback.savePosition(request.uri, player.currentPosition / 1000.0)
                    }
                    player.pause()
                    activeBinding = null
                    callback(null)
                }
            }

            override fun dispose() = setActive(false)
        }
    }

    internal fun close() {
        if (playerDelegate.isInitialized()) player.release()
    }

    internal fun release(playback: TimelinePlaybackCoordinator) {
        if (activeRequest?.first !== playback) return
        activeBinding?.setActive(false)
        activeRequest = null
        if (playerDelegate.isInitialized()) {
            player.stop()
            player.clearMediaItems()
        }
    }
}
