package dev.dimension.flare.ui.component

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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import dev.dimension.flare.compose.ui.media_video_error
import dev.dimension.flare.data.network.MediaPlaybackSource
import dev.dimension.flare.data.network.MediaProxy
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.component.status.LocalIsScrollingInProgress
import dev.dimension.flare.ui.model.UiMedia
import io.github.composefluent.component.ProgressRing
import io.github.kdroidfilter.composemediaplayer.InitialPlayerState
import io.github.kdroidfilter.composemediaplayer.VideoPlayerError
import io.github.kdroidfilter.composemediaplayer.VideoPlayerState
import io.github.kdroidfilter.composemediaplayer.VideoPlayerSurface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.annotation.Single
import java.awt.AWTEvent
import java.awt.EventQueue
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.WindowEvent
import java.lang.management.ManagementFactory
import java.lang.management.MemoryNotificationInfo
import java.lang.management.MemoryPoolMXBean
import java.util.Collections
import java.util.WeakHashMap
import javax.management.NotificationEmitter
import javax.management.NotificationListener
import kotlin.math.abs
import kotlin.math.roundToLong

@OptIn(ExperimentalFoundationApi::class)
@Composable
public fun VideoPlayer(
    media: UiMedia,
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
    errorContent: @Composable BoxScope.() -> Unit = { PlaybackError() },
    loadingPlaceholder: @Composable BoxScope.() -> Unit = {
        if (media.preview != null) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                NetworkImage(
                    model = media.preview,
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
        if (media.preview != null) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                NetworkImage(
                    model = media.preview,
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
                MediaVideoBadge(modifier = Modifier.align(Alignment.BottomStart)) {
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
    val route = rememberMediaProxySnapshot(media)
    val source by key(media, route) {
        produceState<Result<MediaPlaybackSource>?>(null) {
            if (media.isRemote && route == null) return@produceState
            val resolved =
                try {
                    Result.success(MediaProxy.playbackSource(media))
                } catch (
                    error: kotlinx.coroutines.CancellationException,
                ) {
                    throw error
                } catch (error: Exception) {
                    error.printStackTrace()
                    Result.failure(error)
                }
            value = resolved
            awaitDispose { resolved.getOrNull()?.let(MediaProxy::release) }
        }
    }
    val resolved = source?.getOrNull()
    if (resolved == null) {
        Box(modifier.let { if (onClick != null) it.combinedClickable(onClick = onClick, onLongClick = onLongClick) else it }) {
            if (source?.isFailure == true) {
                errorContent()
            } else {
                loadingPlaceholder()
            }
        }
        return
    }
    val uri = media.url
    val playback = rememberTimelinePlayback()
    val binding = rememberSurfaceBinding(resolved, playback, muted)
    val playerState = binding.first
    // Keep the cover underneath the surface. The desktop renderer draws nothing
    // until it has an actual frame; metadata readiness is not frame readiness.
    Box(
        modifier = modifier.timelineVideoAutoplay(playback, binding.second, autoPlay && binding.second.isForeground, uri),
    ) {
        if (playerState == null) {
            if (binding.second.isPreparing && !LocalIsScrollingInProgress.current) loadingPlaceholder() else idlePlaceholder()
        } else {
            if (media.preview != null) {
                NetworkImage(
                    model = media.preview,
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            val playerModifier =
                Modifier
                    .clipToBounds()
                    .semantics { contentDescription?.let { this.contentDescription = it } }
                    .let {
                        if (onClick != null) it.combinedClickable(onClick = onClick, onLongClick = onLongClick) else it
                    }.let { if (aspectRatio != null) it.aspectRatio(aspectRatio) else it }
            VideoPlayerSurface(
                playerState = playerState,
                modifier = playerModifier,
                contentScale = contentScale,
            )
            LaunchedEffect(binding.second, playerState) { binding.second.surfaceAttached() }
            val remainingTime by remember(playerState) {
                derivedStateOf {
                    if (playerState.sliderPos > 0f) {
                        (((playerState.currentTime / (playerState.sliderPos / 1000)) - playerState.currentTime) * 1000).roundToLong()
                    } else {
                        0L
                    }
                }
            }
            remainingTimeContent?.invoke(this, remainingTime)
            if (showControls) controls(playerState, binding.second::seek)
            val playbackError = playerState.error
            LaunchedEffect(playerState, playbackError, resolved.key) {
                if (playbackError != null) {
                    System.err.println(playbackFailureDiagnostic(playbackError, MediaProxy.failureMessage(resolved.url)))
                }
            }
            if (playbackError != null) {
                Box(playerModifier.fillMaxSize()) {
                    errorContent()
                }
            }
        }
    }
}

internal fun playbackFailureDiagnostic(
    error: VideoPlayerError,
    relayFailure: String?,
): String {
    val nativeMessage =
        when (error) {
            is VideoPlayerError.CodecError -> error.message
            is VideoPlayerError.NetworkError -> error.message
            is VideoPlayerError.SourceError -> error.message
            is VideoPlayerError.UnknownError -> error.message
        }
    val diagnostic =
        (relayFailure ?: nativeMessage)
            .lineSequence()
            .first()
            .replace(Regex("(?i)https?://\\S+"), "[redacted URL]")
            .replace(Regex("(?i)(authorization|cookie):.*"), "[redacted credentials]")
            .take(512)
    return "Video playback failed (${error::class.simpleName}): $diagnostic"
}

@Composable
private fun BoxScope.PlaybackError() {
    PlatformText(
        text = stringResource(Res.string.media_video_error),
        color = Color.White,
        modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.75f)).padding(12.dp),
    )
}

@Composable
private fun rememberSurfaceBinding(
    source: MediaPlaybackSource,
    playback: TimelinePlaybackCoordinator,
    muted: Boolean,
): Pair<VideoPlayerState?, SurfaceBindingManager.Binding> {
    val manager: SurfaceBindingManager = koinInject()
    DisposableEffect(manager) {
        manager.observeAppFocus()
        onDispose { }
    }
    var player by remember(source, manager, playback, muted) { mutableStateOf<VideoPlayerState?>(null) }
    val binding =
        remember(source, manager, playback, muted) {
            manager.register(source, playback, muted) { player = it }
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

    private fun newPlayer() =
        lazy {
            createPlayer().apply {
                loop = true
                volume = 0f
            }
        }

    private var playerDelegate = newPlayer()
    val player: VideoPlayerState get() = playerDelegate.value
    private var activeBinding: Binding? = null
    private var requestedBinding: Binding? = null
        set(value) {
            if (field === value) return
            value?.source?.let(MediaProxy::retain)
            field?.source?.let(MediaProxy::release)
            field = value
        }

    // A pending native open can still own the previous source while the next binding waits.
    private var nativeSource: MediaPlaybackSource? = null
    private var unsubscribeRoute: (() -> Unit)? = null
    private var currentSource: MediaPlaybackSource? = null
    private var retentionJob: Job? = null
    private var handoffJob: Job? = null
    private var transferring = false
    private var prepareJob: Job? = null
    private var seekJob: Job? = null
    private var pendingSeek: Double? = null
    private var foreground by mutableStateOf(true)
    private var focusListener: AWTEventListener? = null
    private val suspendedPlaybacks = Collections.newSetFromMap(WeakHashMap<TimelinePlaybackCoordinator, Boolean>())
    private var memoryListener: NotificationListener? = null
    private var memoryPool: MemoryPoolMXBean? = null
    private var previousMemoryThreshold = 0L
    private var memoryThreshold = 0L

    internal fun setForeground(value: Boolean) {
        if (foreground == value) return
        foreground = value
        if (value) {
            // Weak keys can disappear after toList() reads size == 1.
            val playbacks = suspendedPlaybacks.toMutableList()
            suspendedPlaybacks.clear()
            playbacks.forEach { it.setSuspended(false) }
        } else {
            requestedBinding?.playback?.let {
                suspendedPlaybacks.add(it)
                it.setSuspended(true)
            }
            activeBinding?.setActive(false)
            clearIdleBuffer()
        }
    }

    fun observeAppFocus() {
        if (focusListener != null) return
        val listener =
            AWTEventListener { event ->
                if (event is WindowEvent) {
                    when (event.id) {
                        WindowEvent.WINDOW_GAINED_FOCUS -> {
                            setForeground(true)
                        }

                        WindowEvent.WINDOW_LOST_FOCUS -> {
                            if (event.oppositeWindow == null) {
                                // Media details are another app window. Let AWT finish
                                // the focus transfer before treating it as backgrounding.
                                EventQueue.invokeLater {
                                    if (focusListener != null && Window.getWindows().none { it.isFocused }) {
                                        setForeground(false)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.WINDOW_FOCUS_EVENT_MASK)
        focusListener = listener
        setForeground(Window.getWindows().any { it.isFocused })
        // JVMs expose heap pressure through collection thresholds. Native mobile
        // pressure notifications are handled by their platform implementations.
        val pool =
            ManagementFactory.getMemoryPoolMXBeans().firstOrNull {
                it.isCollectionUsageThresholdSupported && it.usage.max > 0
            }
        val emitter = ManagementFactory.getMemoryMXBean() as? NotificationEmitter
        if (pool != null && emitter != null) {
            previousMemoryThreshold = pool.collectionUsageThreshold
            memoryThreshold = previousMemoryThreshold.takeIf { it > 0 } ?: (pool.usage.max * 0.9).toLong()
            pool.collectionUsageThreshold = memoryThreshold
            memoryPool = pool
            val pressureListener =
                NotificationListener { notification, _ ->
                    if (notification.type == MemoryNotificationInfo.MEMORY_COLLECTION_THRESHOLD_EXCEEDED) {
                        scope.launch { clearIdleBuffer() }
                    }
                }
            emitter.addNotificationListener(pressureListener, null, null)
            memoryListener = pressureListener
        }
    }

    inner class Binding(
        val source: MediaPlaybackSource,
        val playback: TimelinePlaybackCoordinator,
        private val muted: Boolean,
        private val callback: (VideoPlayerState?) -> Unit,
    ) {
        val uri: String get() = source.originalUrl
        val isForeground: Boolean get() = foreground
        var isPreparing by mutableStateOf(false)
            private set

        fun setActive(active: Boolean) {
            if (active) {
                if (!foreground) {
                    suspendedPlaybacks.add(playback)
                    playback.setSuspended(true)
                    return
                }
                if (activeBinding === this) return
                transferring = requestedBinding?.source == source
                activeBinding?.setActive(false)
                transferring = false
                retentionJob?.cancel()
                handoffJob?.cancel()
                if (requestedBinding?.source != source) clearPendingSeek()
                activeBinding = this
                requestedBinding = this
                isPreparing = true
                prepareRequestedSource()
            } else if (activeBinding === this) {
                if (currentSource == source && prepareJob?.isActive != true) {
                    val seconds =
                        if (player.userDragging && player.duration > 0) {
                            player.sliderPos / 1000.0 * player.duration
                        } else {
                            pendingSeek ?: player.currentTime
                        }
                    playback.savePosition(uri, seconds)
                    if (player.userDragging && player.duration > 0) seekTo(seconds)
                }
                // Keep a pending restoration alive while paused. Native loading is
                // asynchronous and cannot be cancelled by cancelling our observer.
                player.volume = 0f
                if (!transferring && playback.handoffUri != uri) {
                    player.pause()
                } else {
                    handoffJob?.cancel()
                    handoffJob =
                        scope.launch {
                            delay(500)
                            if (activeBinding == null) player.pause()
                        }
                }
                activeBinding = null
                isPreparing = false
                callback(null)
                retentionJob?.cancel()
                retentionJob =
                    scope.launch {
                        delay(5000)
                        clearIdleBuffer()
                    }
            }
        }

        fun prepared() {
            if (!isPreparing || pendingSeek != null) return
            isPreparing = false
            player.userDragging = false
            player.volume = 0f
            callback(player)
            if (player.error == null && !player.isPlaying) player.play()
        }

        fun seek(sliderPosition: Float) {
            if (activeBinding !== this || currentSource != source || player.duration <= 0) return
            val seconds = sliderPosition.coerceIn(0f, 1000f) / 1000.0 * player.duration
            if (!seconds.isFinite()) return
            playback.savePosition(uri, seconds)
            seekTo(seconds)
        }

        fun surfaceAttached() {
            if (activeBinding === this) player.volume = if (muted) 0f else 1f
        }

        fun dispose() = setActive(false)
    }

    fun register(
        source: MediaPlaybackSource,
        playback: TimelinePlaybackCoordinator,
        muted: Boolean = true,
        callback: (VideoPlayerState?) -> Unit,
    ): Binding {
        playback.onClose(this) { release(playback) }
        return Binding(source, playback, muted, callback)
    }

    private fun prepareRequestedSource() {
        if (prepareJob?.isActive == true) return
        if (currentSource != null && currentSource == requestedBinding?.source && player.error == null) {
            activeBinding?.prepared()
            return
        }
        if (player.error != null) currentSource = null
        prepareJob =
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                // Serialize native opens: cancelling a coroutine waiting for metadata
                // does not cancel the library's in-flight openUri operation.
                while (currentSource != requestedBinding?.source ||
                    (requestedBinding == null && (nativeSource != null || player.hasMedia))
                ) {
                    clearPendingSeek()
                    currentSource = null
                    if (player.hasMedia) {
                        player.stop()
                        snapshotFlow { !player.hasMedia }.first { it }
                    }
                    val source = requestedBinding?.source
                    setNativeSource(source)
                    if (source == null) break
                    player.clearError()
                    player.openUri(source.url, InitialPlayerState.PAUSE)
                    // Linux keeps hasMedia true during openUri and clears isLoading
                    // on pause. The completed stop above establishes a fresh baseline.
                    snapshotFlow { (player.hasMedia && !player.isLoading) || player.error != null }.first { it }
                    if (player.error != null) {
                        if (requestedBinding?.source == source) break
                        continue
                    }
                    currentSource = source
                    val binding = requestedBinding
                    if (binding?.source == source) {
                        val seconds = binding.playback.position(binding.uri)
                        if (seconds > 0 && player.duration > 0) {
                            seekTo(seconds)
                        }
                    }
                }
                activeBinding?.prepared()
            }
    }

    private fun setNativeSource(source: MediaPlaybackSource?) {
        if (nativeSource == source) return
        unsubscribeRoute?.invoke()
        source?.let(MediaProxy::retain)
        nativeSource?.let(MediaProxy::release)
        nativeSource = source
        val snapshot = source?.snapshot
        unsubscribeRoute =
            snapshot?.onInvalidated {
                scope.launch {
                    if (nativeSource?.snapshot !== snapshot) return@launch
                    if (requestedBinding?.source?.snapshot === snapshot) {
                        activeBinding?.setActive(false)
                        requestedBinding = null
                    }
                    retentionJob?.cancel()
                    handoffJob?.cancel()
                    disposeNativePlayer()
                    if (requestedBinding != null) prepareRequestedSource()
                }
            }
    }

    private fun disposeNativePlayer() {
        prepareJob?.cancel()
        prepareJob = null
        clearPendingSeek()
        currentSource = null
        // Native loading outlives openUri's caller; disposal cancels its own worker.
        if (playerDelegate.isInitialized()) player.dispose()
        playerDelegate = newPlayer()
        setNativeSource(null)
    }

    fun close() {
        focusListener?.let { Toolkit.getDefaultToolkit().removeAWTEventListener(it) }
        focusListener = null
        memoryListener?.let { (ManagementFactory.getMemoryMXBean() as? NotificationEmitter)?.removeNotificationListener(it) }
        memoryListener = null
        memoryPool?.let { if (it.collectionUsageThreshold == memoryThreshold) it.collectionUsageThreshold = previousMemoryThreshold }
        memoryPool = null
        activeBinding?.setActive(false)
        retentionJob?.cancel()
        handoffJob?.cancel()
        requestedBinding = null
        disposeNativePlayer()
    }

    fun release(playback: TimelinePlaybackCoordinator) {
        if (requestedBinding?.playback !== playback) return
        if (activeBinding?.playback === playback) activeBinding?.setActive(false)
        handoffJob?.cancel()
        handoffJob = null
        if (activeBinding == null && playerDelegate.isInitialized()) player.pause()
    }

    internal fun clearIdleBuffer() {
        if (activeBinding != null) return
        retentionJob?.cancel()
        retentionJob = null
        handoffJob?.cancel()
        handoffJob = null
        requestedBinding = null
        if (prepareJob?.isActive == true) {
            disposeNativePlayer()
        } else if (playerDelegate.isInitialized()) {
            prepareRequestedSource()
        }
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
                activeBinding?.prepared()
            }
    }

    private fun clearPendingSeek() {
        seekJob?.cancel()
        seekJob = null
        pendingSeek = null
    }
}
