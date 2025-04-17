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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import io.github.kdroidfilter.composemediaplayer.VideoPlayerState
import io.github.kdroidfilter.composemediaplayer.VideoPlayerSurface
import kotlinx.coroutines.delay
import kotlin.concurrent.timer
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalFoundationApi::class)
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
    errorContent: @Composable BoxScope.() -> Unit,
    loadingPlaceholder: @Composable BoxScope.() -> Unit,
) {
    val playerPool: VideoPlayerPool = org.koin.compose.koinInject()
    val player =
        remember(uri) {
            playerPool
                .get(uri)
                .apply {
                    if (autoPlay) {
                        play()
                    }
                    volume = if (muted) 0f else 1f
                }
        }
    DisposableEffect(uri) {
        onDispose {
            playerPool.release(uri)
        }
    }
    var remainingTime by remember { mutableLongStateOf(0L) }
    val density = LocalDensity.current
    val size =
        remember(player.metadata) {
            val height = player.metadata.height
            val width = player.metadata.width
            if (height != null && width != null) {
                with(density) {
                    Size(
                        width = width.toFloat(),
                        height = height.toFloat(),
                    )
                }
            } else {
                null
            }
        }
    LaunchedEffect(player) {
        while (true) {
            val duration = player.metadata.duration
            if (remainingTimeContent != null && duration != null) {
                val position = player.sliderPos / 1000f
                remainingTime = (duration * 1000 * (1f - position)).toLong()
            }
            delay(500)
        }
    }

    Box(modifier) {
        VideoPlayerSurface(
            playerState = player,
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
        if (player.error != null) {
            errorContent.invoke(this)
        } else if (player.isLoading) {
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

public class VideoPlayerPool {
    private val positionPool = mutableMapOf<String, Float>()
    private val lockCount = linkedMapOf<String, Long>()
    private val pool =
        lruCache<String, VideoPlayerState>(
            maxSize = 10,
            create = { uri ->
                VideoPlayerState()
                    .apply {
                        loop = true
                        openUri(uri)
                    }
            },
            onEntryRemoved = { evicted, key, oldValue, newValue ->
                if (evicted) {
                    positionPool.put(key, oldValue.sliderPos)
                    oldValue.dispose()
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
                    pool.remove(uri)?.dispose()
                }
            }
        }

    public fun peek(uri: String): VideoPlayerState? = pool.get(uri)

    public fun get(uri: String): VideoPlayerState {
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
