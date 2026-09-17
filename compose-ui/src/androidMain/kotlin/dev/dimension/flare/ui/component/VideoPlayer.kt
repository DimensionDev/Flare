@file:kotlin.OptIn(ExperimentalFoundationApi::class)

package dev.dimension.flare.ui.component

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
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
import dev.dimension.flare.ui.theme.PlatformTheme
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.util.Collections
import java.util.WeakHashMap

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
    showVideoSurface: Boolean = true,
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
    var started by remember { mutableStateOf(false) }
    var resumed by remember { mutableStateOf(false) }
    val playback = rememberTimelinePlayback()
    val request =
        remember(uri, customHeaders) {
            VideoRequest(uri = uri, customHeaders = customHeaders?.toMap().orEmpty())
        }
    val binding = rememberSurfaceBinding(request, playback, muted)
    val player = binding.first

    LifecycleResumeEffect(binding.second) {
        resumed = true
        onPauseOrDispose {
            resumed = false
            binding.second.surfaceReady(visible = false)
        }
    }
    LifecycleStartEffect(binding.second) {
        started = true
        onStopOrDispose {
            started = false
            binding.second.setActive(false)
        }
    }
    Box(
        modifier = modifier.timelineVideoAutoplay(playback, binding.second, autoPlay && started, request.uri),
    ) {
        if (player == null) {
            idlePlaceholder()
        } else {
            // Mount the surface while covered: STATE_READY only means the media
            // is prepared, not that this new surface has received its first frame.
            key(binding.second, player) {
                val presentation = rememberPresentationState(player)
                LaunchedEffect(presentation.coverSurface, resumed) {
                    if (!presentation.coverSurface) binding.second.surfaceReady(visible = resumed)
                }
                val playerModifier =
                    Modifier
                        .clipToBounds()
                        .semantics { contentDescription?.let { this.contentDescription = it } }
                        .resizeWithContentScale(contentScale = contentScale, sourceSizeDp = presentation.videoSizeDp)
                        .let { if (onClick != null) it.combinedClickable(onClick = onClick, onLongClick = onLongClick) else it }
                        .let { if (keepScreenOn) it.keepScreenOn() else it }
                        .let { if (aspectRatio != null && presentation.videoSizeDp == null) it.aspectRatio(aspectRatio) else it }
                val view = LocalView.current
                PlayerSurface(
                    player = player,
                    modifier =
                        playerModifier.offset {
                            // Keep the SurfaceView attached while a poster handles the overlay animation.
                            // Moving it outside the window avoids alpha limitations without rebuilding its surface.
                            IntOffset(if (showVideoSurface) 0 else view.rootView.width * 2, 0)
                        },
                )
                if (!showVideoSurface) {
                    NetworkImage(
                        model = previewUri,
                        customHeaders = customHeaders,
                        contentDescription = contentDescription,
                        contentScale = contentScale,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (presentation.coverSurface) {
                    Box(Modifier.fillMaxSize()) { loadingPlaceholder() }
                } else if (remainingTimeContent != null) {
                    VideoCountdown(player, remainingTimeContent)
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
    private var activeRequest: VideoRequest? by mutableStateOf(null)
    private var requestOwner: TimelinePlaybackCoordinator? = null
    private val scope = MainScope()
    private var retentionJob: Job? = null
    private var handoffJob: Job? = null
    private var transferring = false
    private var foreground = true
    private val suspendedPlaybacks = Collections.newSetFromMap(WeakHashMap<TimelinePlaybackCoordinator, Boolean>())
    private val startedActivities = Collections.newSetFromMap(WeakHashMap<Activity, Boolean>())

    public fun playerFor(uri: String): ExoPlayer? = if (activeBinding != null && activeRequest?.uri == uri) player else null

    public interface Binding {
        public fun setActive(active: Boolean)

        public fun dispose()

        public fun surfaceReady(visible: Boolean)
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
                    if (!foreground) {
                        suspendedPlaybacks.add(playback)
                        playback.setSuspended(true)
                        return
                    }
                    if (activeBinding === this) return
                    transferring = activeRequest == request
                    activeBinding?.setActive(false)
                    transferring = false
                    retentionJob?.cancel()
                    handoffJob?.cancel()
                    activeBinding = this
                    requestOwner = playback
                    if (activeRequest != request || player.playerError != null) {
                        val source =
                            media3VideoCacheManager
                                .mediaSourceFactory(request.customHeaders)
                                .createMediaSource(MediaItem.fromUri(request.uri))
                        player.setMediaSource(source)
                        player.seekTo((playback.position(request.uri) * 1000).toLong())
                        player.prepare()
                        activeRequest = request
                    }
                    player.setPlaybackSpeed(1f)
                    player.volume = 0f
                    player.setAudioAttributes(audioAttributes, false)
                    player.play()
                    callback(player)
                } else if (activeBinding === this) {
                    if (player.playbackState != Player.STATE_IDLE && player.playerError == null) {
                        playback.savePosition(request.uri, player.currentPosition / 1000.0)
                    }
                    player.volume = 0f
                    if (!transferring && playback.handoffUri != request.uri) {
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
                    callback(null)
                    retentionJob?.cancel()
                    retentionJob =
                        scope.launch {
                            delay(5000)
                            clearIdleBuffer()
                        }
                }
            }

            override fun surfaceReady(visible: Boolean) {
                if (activeBinding !== this) return
                player.volume = if (muted || !visible) 0f else 1f
                player.setAudioAttributes(audioAttributes, !muted && visible)
            }

            override fun dispose() = setActive(false)
        }
    }

    private val memoryCallbacks =
        object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onLowMemory() = clearIdleBuffer()

            override fun onTrimMemory(level: Int) {
                if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) setForeground(false) else clearIdleBuffer()
            }
        }
    private val activityCallbacks =
        object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStopped(activity: Activity) {
                startedActivities.remove(activity)
                if (startedActivities.isEmpty() && !activity.isChangingConfigurations) setForeground(false)
            }

            override fun onActivityCreated(
                activity: Activity,
                savedInstanceState: Bundle?,
            ) = Unit

            override fun onActivityStarted(activity: Activity) {
                startedActivities.add(activity)
            }

            override fun onActivityResumed(activity: Activity) {
                startedActivities.add(activity)
                setForeground(true)
            }

            override fun onActivityPaused(activity: Activity) = Unit

            override fun onActivitySaveInstanceState(
                activity: Activity,
                outState: Bundle,
            ) = Unit

            override fun onActivityDestroyed(activity: Activity) = Unit
        }

    init {
        context.applicationContext.registerComponentCallbacks(memoryCallbacks)
        (context.applicationContext as? Application)?.registerActivityLifecycleCallbacks(activityCallbacks)
    }

    private fun setForeground(value: Boolean) {
        if (foreground == value) return
        foreground = value
        if (value) {
            val playbacks = suspendedPlaybacks.toList()
            suspendedPlaybacks.clear()
            playbacks.forEach { it.setSuspended(false) }
        } else {
            requestOwner?.let {
                suspendedPlaybacks.add(it)
                it.setSuspended(true)
            }
            activeBinding?.setActive(false)
            clearIdleBuffer()
        }
    }

    internal fun close() {
        retentionJob?.cancel()
        handoffJob?.cancel()
        context.applicationContext.unregisterComponentCallbacks(memoryCallbacks)
        (context.applicationContext as? Application)?.unregisterActivityLifecycleCallbacks(activityCallbacks)
        if (playerDelegate.isInitialized()) player.release()
    }

    internal fun release(playback: TimelinePlaybackCoordinator) {
        if (requestOwner !== playback) return
        activeBinding?.setActive(false)
        handoffJob?.cancel()
        handoffJob = null
        if (activeBinding == null && playerDelegate.isInitialized()) player.pause()
    }

    private fun clearIdleBuffer() {
        if (activeBinding != null) return
        retentionJob?.cancel()
        retentionJob = null
        handoffJob?.cancel()
        handoffJob = null
        activeRequest = null
        requestOwner = null
        if (playerDelegate.isInitialized()) {
            player.stop()
            player.clearMediaItems()
        }
    }
}
