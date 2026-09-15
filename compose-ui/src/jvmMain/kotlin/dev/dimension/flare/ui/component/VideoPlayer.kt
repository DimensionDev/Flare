package dev.dimension.flare.ui.component

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CirclePlay
import dev.dimension.flare.compose.ui.Res
import dev.dimension.flare.compose.ui.media_play_video
import dev.dimension.flare.ui.component.status.LocalIsScrollingInProgress
import dev.dimension.flare.ui.theme.PlatformTheme
import io.github.composefluent.component.ProgressRing
import io.github.kdroidfilter.composemediaplayer.InitialPlayerState
import io.github.kdroidfilter.composemediaplayer.VideoPlayerState
import io.github.kdroidfilter.composemediaplayer.VideoPlayerSurface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.annotation.Single
import kotlin.math.abs
import kotlin.math.roundToLong

@OptIn(ExperimentalFoundationApi::class)
@Composable
public fun VideoPlayer(
    uri: String,
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
            ProgressRing(
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
    controls: @Composable BoxScope.(VideoPlayerState, (Float) -> Unit) -> Unit = { _, _ -> },
) {
    val playback = rememberTimelinePlayback()
    val binding = rememberSurfaceBinding(uri, playback, muted)
    val playerState = binding.first
    var isLoaded by remember(binding.second, playerState) { mutableStateOf(false) }

    LaunchedEffect(muted, playerState) {
        playerState?.volume = if (muted) 0f else 1f
    }

    LaunchedEffect(playerState?.hasMedia, playerState?.isLoading) {
        if (playerState?.hasMedia == true && !playerState.isLoading) isLoaded = true
    }

    Box(
        modifier = modifier.timelineVideoAutoplay(playback, binding.second, autoPlay, uri),
    ) {
        if ((!isLoaded && LocalIsScrollingInProgress.current) || playerState == null) {
            if (binding.second.isPreparing && !LocalIsScrollingInProgress.current) {
                loadingPlaceholder()
            } else {
                idlePlaceholder()
            }
        } else {
            AnimatedContent(
                isLoaded,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
            ) { loaded ->
                if (loaded) {
                    val playerModifier =
                        Modifier
                            .clipToBounds()
                            .semantics {
                                contentDescription?.let { this.contentDescription = it }
                            }.let {
                                if (onClick != null) {
                                    it.combinedClickable(
                                        onClick = onClick,
                                        onLongClick = onLongClick,
                                    )
                                } else {
                                    it
                                }
                            }.let {
                                if (aspectRatio != null) {
                                    it.aspectRatio(aspectRatio)
                                } else {
                                    it
                                }
                            }

                    Box {
                        VideoPlayerSurface(
                            playerState = playerState,
                            modifier = playerModifier,
                            contentScale = contentScale,
                        )
                        val remainingTime by remember {
                            derivedStateOf {
                                if (playerState.sliderPos > 0f) {
                                    (((playerState.currentTime / (playerState.sliderPos / 1000)) - playerState.currentTime) * 1000)
                                        .roundToLong()
                                } else {
                                    0L
                                }
                            }
                        }
                        remainingTimeContent?.invoke(this, remainingTime)
                    }
                } else {
                    Box {
                        loadingPlaceholder()
                    }
                }
            }
            if (showControls) controls(playerState, binding.second::seek)
        }
    }
}

@Composable
private fun rememberSurfaceBinding(
    uri: String,
    playback: TimelinePlaybackCoordinator,
    muted: Boolean,
): Pair<VideoPlayerState?, SurfaceBindingManager.Binding> {
    val manager: SurfaceBindingManager = koinInject()
    var player by remember(uri, manager, playback, muted) { mutableStateOf<VideoPlayerState?>(null) }
    val binding =
        remember(uri, manager, playback, muted) {
            manager.register(uri, playback, muted) { player = it }
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

@androidx.compose.runtime.Stable
@Single
internal class SurfaceBindingManager() {
    private var scope: CoroutineScope = MainScope()
    private var createPlayer: () -> VideoPlayerState = {
        io.github.kdroidfilter.composemediaplayer
            .createVideoPlayerState()
    }

    internal constructor(scope: CoroutineScope, createPlayer: () -> VideoPlayerState) : this() {
        this.scope = scope
        this.createPlayer = createPlayer
    }

    private val playerDelegate =
        lazy {
            createPlayer().apply {
                loop = true
                volume = 0f
            }
        }
    val player: VideoPlayerState by playerDelegate
    private var activeBinding: Binding? = null
    private var requestedSource: Pair<TimelinePlaybackCoordinator, String>? = null
    private var currentSource: Pair<TimelinePlaybackCoordinator, String>? = null
    private var prepareJob: Job? = null
    private var seekJob: Job? = null
    private var pendingSeek: Double? = null

    inner class Binding(
        private val uri: String,
        private val playback: TimelinePlaybackCoordinator,
        private val muted: Boolean,
        private val callback: (VideoPlayerState?) -> Unit,
    ) {
        var isPreparing by mutableStateOf(false)
            private set

        fun setActive(active: Boolean) {
            if (active) {
                if (activeBinding === this) return
                activeBinding?.setActive(false)
                if (requestedSource != (playback to uri)) clearPendingSeek()
                activeBinding = this
                requestedSource = playback to uri
                isPreparing = true
                prepareRequestedSource()
            } else if (activeBinding === this) {
                if (currentSource == (playback to uri) && prepareJob?.isActive != true) {
                    val seconds =
                        if (player.userDragging && player.duration > 0) {
                            player.sliderPos / 1000.0 * player.duration
                        } else {
                            pendingSeek ?: player.currentTime
                        }
                    playback.savePosition(uri, seconds)
                }
                // Keep a pending restoration alive while paused. Native loading is
                // asynchronous and cannot be cancelled by cancelling our observer.
                player.pause()
                activeBinding = null
                isPreparing = false
                callback(null)
            }
        }

        fun prepared() {
            isPreparing = false
            player.userDragging = false
            player.volume = if (muted) 0f else 1f
            callback(player)
            if (player.error == null) player.play()
        }

        fun seek(sliderPosition: Float) {
            if (activeBinding !== this || currentSource != (playback to uri) || player.duration <= 0) return
            val seconds = sliderPosition.coerceIn(0f, 1000f) / 1000.0 * player.duration
            if (!seconds.isFinite()) return
            playback.savePosition(uri, seconds)
            seekTo(seconds)
        }

        fun dispose() = setActive(false)
    }

    fun register(
        uri: String,
        playback: TimelinePlaybackCoordinator,
        muted: Boolean = true,
        callback: (VideoPlayerState?) -> Unit,
    ): Binding {
        playback.onClose(this) { release(playback) }
        return Binding(uri, playback, muted, callback)
    }

    private fun prepareRequestedSource() {
        if (prepareJob?.isActive == true) return
        if (currentSource != null && currentSource == requestedSource) {
            activeBinding?.prepared()
            return
        }
        prepareJob =
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                // Serialize native opens: cancelling a coroutine waiting for metadata
                // does not cancel the library's in-flight openUri operation.
                while (currentSource != requestedSource) {
                    clearPendingSeek()
                    currentSource = null
                    if (player.hasMedia) {
                        player.stop()
                        snapshotFlow { !player.hasMedia }.first { it }
                    }
                    val source = requestedSource ?: break
                    player.clearError()
                    player.openUri(source.second, InitialPlayerState.PAUSE)
                    // Linux keeps hasMedia true during openUri and clears isLoading
                    // on pause. The completed stop above establishes a fresh baseline.
                    snapshotFlow { (player.hasMedia && !player.isLoading) || player.error != null }.first { it }
                    if (player.error != null) {
                        if (requestedSource == source) break
                        continue
                    }
                    currentSource = source
                    if (requestedSource == source) {
                        val seconds = source.first.position(source.second)
                        if (seconds > 0 && player.duration > 0) {
                            seekTo(seconds)
                        }
                    }
                }
                activeBinding?.prepared()
            }
    }

    fun close() {
        activeBinding?.setActive(false)
        prepareJob?.cancel()
        clearPendingSeek()
        requestedSource = null
        currentSource = null
        if (playerDelegate.isInitialized()) player.dispose()
    }

    fun release(playback: TimelinePlaybackCoordinator) {
        if (requestedSource?.first !== playback) return
        activeBinding?.setActive(false)
        requestedSource = null
        if (playerDelegate.isInitialized()) prepareRequestedSource()
    }

    private fun seekTo(seconds: Double) {
        clearPendingSeek()
        val duration = player.duration
        val target = seconds.coerceIn(0.0, duration)
        pendingSeek = target
        player.seekTo((target / duration * 1000).toFloat())
        seekJob =
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                snapshotFlow { player.currentTime }.first { abs(it - target) < 0.5 || (target == duration && it < 0.5) }
                pendingSeek = null
            }
    }

    private fun clearPendingSeek() {
        seekJob?.cancel()
        seekJob = null
        pendingSeek = null
    }
}
