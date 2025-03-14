package dev.dimension.flare.ui.component.platform

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import dev.dimension.flare.ui.component.platform.VideoPlayerState.Companion.Saver
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import java.awt.Component
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal actual fun PlatformVideoPlayer(
    uri: String,
    previewUri: String?,
    contentDescription: String?,
    modifier: Modifier,
    muted: Boolean,
    showControls: Boolean,
    keepScreenOn: Boolean,
    aspectRatio: Float?,
    contentScale: ContentScale,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    autoPlay: Boolean,
    remainingTimeContent: @Composable (BoxScope.(Long) -> Unit)?,
    loadingPlaceholder: @Composable BoxScope.() -> Unit,
) {
    VideoPlayer(
        url = uri,
        state =
            rememberVideoPlayerState(
                seek = 0.0,
                speed = 1f,
                volume = if (muted) 0f else 1f,
                isResumed = autoPlay,
                isFullscreen = false,
            ),
        modifier = modifier,
        onFinish = null,
    )
}

@Composable
public fun VideoPlayer(
    url: String,
    state: VideoPlayerState,
    modifier: Modifier = Modifier,
    onFinish: (() -> Unit)? = null,
): Unit =
    VideoPlayerImpl(
        url = url,
        isResumed = state.isResumed,
        volume = state.volume,
        speed = state.speed,
        seek = state.seek,
        isFullscreen = state.isFullscreen,
        progressState = state._progress,
        modifier = modifier,
        onFinish = onFinish,
    )

@Composable
public fun rememberVideoPlayerState(
    seek: Double = 0.0,
    speed: Float = 1f,
    volume: Float = 1f,
    isResumed: Boolean = true,
    isFullscreen: Boolean = false,
): VideoPlayerState =
    rememberSaveable(saver = VideoPlayerState.Saver()) {
        VideoPlayerState(
            seek,
            speed,
            volume,
            isResumed,
            isFullscreen,
            Progress(0.0, 0),
        )
    }

public class VideoPlayerState internal constructor(
    seek: Double = 0.0,
    speed: Float = 1f,
    volume: Float = 1f,
    isResumed: Boolean = true,
    isFullscreen: Boolean = false,
    progress: Progress,
) {
    public var seek: Double by mutableStateOf(seek)
    public var speed: Float by mutableStateOf(speed)
    public var volume: Float by mutableStateOf(volume)
    public var isResumed: Boolean by mutableStateOf(isResumed)
    public var isFullscreen: Boolean by mutableStateOf(isFullscreen)
    internal val _progress = mutableStateOf(progress)
    public val progress: State<Progress> = _progress

    public fun toggleResume() {
        isResumed = !isResumed
    }

    public fun toggleFullscreen() {
        isFullscreen = !isFullscreen
    }

    public fun stopPlayback() {
        isResumed = false
    }

    internal companion object {
        /**
         * The default [Saver] implementation for [VideoPlayerState].
         */
        fun Saver() =
            listSaver<VideoPlayerState, Any>(
                save = {
                    listOf(
                        it.seek,
                        it.speed,
                        it.volume,
                        it.isResumed,
                        it.isFullscreen,
                        it.progress.value,
                    )
                },
                restore = {
                    VideoPlayerState(
                        seek = it[0] as Double,
                        speed = it[1] as Float,
                        volume = it[2] as Float,
                        isResumed = it[3] as Boolean,
                        isFullscreen = it[3] as Boolean,
                        progress = it[4] as Progress,
                    )
                },
            )
    }
}

public data class Progress(
    val fraction: Double,
    // TODO: Use kotlin.time.Duration when Kotlin version is updated.
    //  See https://github.com/Kotlin/api-guidelines/issues/6
    val timeMillis: Long,
)

@Composable
internal fun VideoPlayerImpl(
    url: String,
    isResumed: Boolean,
    volume: Float,
    speed: Float,
    seek: Double,
    isFullscreen: Boolean,
    progressState: MutableState<Progress>,
    modifier: Modifier,
    onFinish: (() -> Unit)?,
) {
    val mediaPlayerComponent = remember { initializeMediaPlayerComponent() }
    val mediaPlayer = remember { mediaPlayerComponent.mediaPlayer() }
    mediaPlayer.emitProgressTo(progressState)
    mediaPlayer.setupVideoFinishHandler(onFinish)

    val factory = remember { { mediaPlayerComponent } }
    // OR the following code and using SwingPanel(factory = { factory }, ...)
    // val factory by rememberUpdatedState(mediaPlayerComponent)

    LaunchedEffect(url) { mediaPlayer.media().play/*OR .start*/(url) }
    LaunchedEffect(seek) { mediaPlayer.controls().setPosition(seek) }
    LaunchedEffect(speed) { mediaPlayer.controls().setRate(speed) }
    LaunchedEffect(volume) { mediaPlayer.audio().setVolume(volume.toPercentage()) }
    LaunchedEffect(isResumed) { mediaPlayer.controls().setPause(!isResumed) }
    LaunchedEffect(isFullscreen) {
        if (mediaPlayer is EmbeddedMediaPlayer) {
            /*
             * To be able to access window in the commented code below,
             * extend the player composable function from WindowScope.
             * See https://github.com/JetBrains/compose-jb/issues/176#issuecomment-812514936
             * and its subsequent comments.
             *
             * We could also just fullscreen the whole window:
             * `window.placement = WindowPlacement.Fullscreen`
             * See https://github.com/JetBrains/compose-multiplatform/issues/1489
             */
            // mediaPlayer.fullScreen().strategy(ExclusiveModeFullScreenStrategy(window))
            mediaPlayer.fullScreen().toggle()
        }
    }
    DisposableEffect(Unit) { onDispose(mediaPlayer::release) }
    SwingPanel(
        factory = factory,
        background = Color.Transparent,
        modifier = modifier,
    )
}

private fun Float.toPercentage(): Int = (this * 100).roundToInt()

/**
 * See https://github.com/caprica/vlcj/issues/887#issuecomment-503288294
 * for why we're using CallbackMediaPlayerComponent for macOS.
 */
private fun initializeMediaPlayerComponent(): Component {
    NativeDiscovery().discover()
    return if (isMacOS()) {
        CallbackMediaPlayerComponent()
    } else {
        EmbeddedMediaPlayerComponent()
    }
}

/**
 * We play the video again on finish (so the player is kind of idempotent),
 * unless the [onFinish] callback stops the playback.
 * Using `mediaPlayer.controls().repeat = true` did not work as expected.
 */
@Composable
private fun MediaPlayer.setupVideoFinishHandler(onFinish: (() -> Unit)?) {
    DisposableEffect(onFinish) {
        val listener =
            object : MediaPlayerEventAdapter() {
                override fun finished(mediaPlayer: MediaPlayer) {
                    onFinish?.invoke()
                    mediaPlayer.submit { mediaPlayer.controls().play() }
                }
            }
        events().addMediaPlayerEventListener(listener)
        onDispose { events().removeMediaPlayerEventListener(listener) }
    }
}

/**
 * Checks for and emits video progress every 50 milliseconds.
 * Note that it seems vlcj updates the progress only every 250 milliseconds or so.
 *
 * Instead of using `Unit` as the `key1` for [LaunchedEffect],
 * we could use `media().info()?.mrl()` if it's needed to re-launch
 * the effect (for whatever reason) when the url (aka video) changes.
 */
@Composable
private fun MediaPlayer.emitProgressTo(state: MutableState<Progress>) {
    LaunchedEffect(key1 = Unit) {
        while (isActive) {
            val fraction = status().position()
            val time = status().time()
            state.value = Progress(fraction, time)
            delay(50)
        }
    }
}

/**
 * Returns [MediaPlayer] from player components.
 * The method names are the same, but they don't share the same parent/interface.
 * That's why we need this method.
 */
private fun Component.mediaPlayer() =
    when (this) {
        is CallbackMediaPlayerComponent -> mediaPlayer()
        is EmbeddedMediaPlayerComponent -> mediaPlayer()
        else -> error("mediaPlayer() can only be called on vlcj player components")
    }

private fun isMacOS(): Boolean {
    val os =
        System
            .getProperty("os.name", "generic")
            .lowercase(Locale.ENGLISH)
    return "mac" in os || "darwin" in os
}
