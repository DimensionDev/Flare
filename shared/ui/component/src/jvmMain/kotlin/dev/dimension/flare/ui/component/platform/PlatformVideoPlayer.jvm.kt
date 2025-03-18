package dev.dimension.flare.ui.component.platform

import androidx.collection.lruCache
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.openani.mediamp.InternalMediampApi
import org.openani.mediamp.PlaybackState
import org.openani.mediamp.features.AudioLevelController
import org.openani.mediamp.playUri
import org.openani.mediamp.vlc.VlcMediampPlayer
import org.openani.mediamp.vlc.compose.VlcMediampPlayerSurface
import kotlin.concurrent.timer
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalFoundationApi::class, InternalMediampApi::class)
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
    val playerPool: VideoPlayerPool = org.koin.compose.koinInject()
    val player =
        remember(uri) {
            playerPool
                .get(uri)
        }
    val state by player.playbackState.collectAsState()
    LaunchedEffect(state) {
        if (state in listOf(PlaybackState.READY, PlaybackState.PAUSED) && autoPlay) {
            player.features[AudioLevelController.Key]?.setMute(muted)
            player.resume()
        }
    }
    LaunchedEffect(muted) {
        player.features[AudioLevelController.Key]?.setMute(muted)
    }
    DisposableEffect(Unit) {
        onDispose {
            playerPool.release(uri)
        }
    }
    val properties by player.mediaProperties.collectAsState()
    val position by player.currentPositionMillis.collectAsState()
    val density = LocalDensity.current
    val size =
        remember(state) {
            val dimension = player.player.video().videoDimension()
            if (dimension != null) {
                with(density) {
                    Size(
                        width = dimension.width.toFloat(),
                        height = dimension.height.toFloat(),
                    )
                }
            } else {
                null
            }
        }
    val remainingTime by remember {
        derivedStateOf {
            properties?.durationMillis?.let { duration ->
                if (duration > 0) {
                    duration - position
                } else {
                    0
                }
            } ?: 0
        }
    }

    Box(modifier) {
        VlcMediampPlayerSurface(
            mediampPlayer = player,
            modifier =
                Modifier
                    .clipToBounds()
                    .resizeWithContentScale(
                        contentScale = contentScale,
                        sourceSizeDp = size,
                    ).let {
                        if (aspectRatio != null) {
                            it.aspectRatio(
                                aspectRatio,
                            )
                        } else {
                            it
                        }
                    }.let {
                        if (onClick != null) {
                            it.combinedClickable(
                                onClick = onClick,
                                onLongClick = onLongClick,
                            )
                        } else {
                            it
                        }
                    }.matchParentSize(),
        )
        if (state != PlaybackState.PLAYING || position <= 0) {
            loadingPlaceholder()
        } else {
            remainingTimeContent?.invoke(this, remainingTime)
        }
    }
}

@Composable
private fun Modifier.resizeWithContentScale(
    contentScale: ContentScale,
    sourceSizeDp: Size?,
    density: Density = LocalDensity.current,
): Modifier =
    then(
        Modifier
            .fillMaxSize()
            .wrapContentSize()
            .then(
                sourceSizeDp?.let { srcSizeDp ->
                    Modifier.layout { measurable, constraints ->
                        val srcSizePx =
                            with(density) { Size(Dp(srcSizeDp.width).toPx(), Dp(srcSizeDp.height).toPx()) }
                        val dstSizePx = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
                        val scaleFactor = contentScale.computeScaleFactor(srcSizePx, dstSizePx)
                        val placeable =
                            measurable.measure(
                                constraints.copy(
                                    maxWidth = (srcSizePx.width * scaleFactor.scaleX).roundToInt(),
                                    maxHeight = (srcSizePx.height * scaleFactor.scaleY).roundToInt(),
                                    minWidth = (srcSizePx.width * scaleFactor.scaleX).roundToInt(),
                                    minHeight = (srcSizePx.height * scaleFactor.scaleY).roundToInt(),
                                ),
                            )
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }
                } ?: Modifier,
            ),
    )

public class VideoPlayerPool(
    private val scope: CoroutineScope,
    private val context: CoroutineContext = EmptyCoroutineContext,
) {
    private val positionPool = mutableMapOf<String, Long>()
    private val lockCount = linkedMapOf<String, Long>()
    private val pool =
        lruCache<String, VlcMediampPlayer>(
            maxSize = 10,
            create = { uri ->
                VlcMediampPlayer(context)
                    .apply {
                        player.controls().repeat = true
                        scope.launch {
                            playUri(uri)
                        }
                    }
            },
            onEntryRemoved = { evicted, key, oldValue, newValue ->
                if (evicted) {
                    positionPool.put(key, oldValue.getCurrentPositionMillis())
                    oldValue.close()
                } else if (newValue != null) {
                    val position = positionPool.get(key)
                    if (position != null) {
                        newValue.seekTo(position)
                        positionPool.remove(key)
                    }
                }
            },
        )

    private val clearTimer =
        timer(period = 1.minutes.inWholeMilliseconds) {
            pool.snapshot().forEach { (uri, _) ->
                if (lockCount.getOrElse(uri) { 0 } == 0L) {
                    pool.remove(uri)?.close()
                }
            }
        }

    public fun peek(uri: String): VlcMediampPlayer? = pool.get(uri)

    public fun get(uri: String): VlcMediampPlayer {
        lock(uri)
        return pool.get(uri)!!
    }

    public fun lock(uri: String) {
        lockCount.put(uri, lockCount.getOrElse(uri) { 0 } + 1)
    }

    public fun release(uri: String): Boolean {
        lockCount.put(uri, lockCount.getOrElse(uri) { 0 } - 1)
        val count = lockCount.getOrElse(uri) { 0 }
        if (count == 0L) {
            pool.get(uri)?.pause()
        }

        return count == 0L
    }
}
