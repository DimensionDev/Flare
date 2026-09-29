package dev.dimension.flare.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.Uri
import coil3.compose.LocalPlatformContext
import coil3.compose.asPainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CirclePlay
import dev.dimension.flare.compose.ui.Res
import dev.dimension.flare.compose.ui.status_loadmore_error_retry
import dev.dimension.flare.compose.ui.ugoira_pause
import dev.dimension.flare.compose.ui.ugoira_play
import dev.dimension.flare.media.UgoiraAnimation
import dev.dimension.flare.ui.component.platform.PlatformButton
import dev.dimension.flare.ui.component.platform.PlatformCircularProgressIndicator
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.media.UgoiraPresenter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.time.TimeSource

/** Uses the same visibility arbitration as videos; keeps only two decoded frames. */
@Composable
public fun UgoiraPlayer(
    media: UiMedia.Ugoira,
    modifier: Modifier = Modifier,
    autoplay: Boolean = true,
    controls: Boolean = false,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val presenter = remember(media) { UgoiraPresenter(media) }.invoke()
    val scope = rememberCoroutineScope()
    val playback = rememberTimelinePlayback()
    val memory = MediaPlaybackMemory.shared
    val id = remember(media.url) { Any() }
    val foreground = platformMediaActive()
    var selected by remember(media.url) { mutableStateOf(false) }
    var paused by remember(media.url) { mutableStateOf(memory.paused(media.url)) }
    var animation by remember(media.url) { mutableStateOf<UgoiraAnimation?>(null) }
    var progress by remember(media.url) { mutableFloatStateOf(0f) }
    var failure by remember(media.url) { mutableStateOf(false) }
    var retry by remember(media.url) { mutableIntStateOf(0) }
    var painter by remember(media.url) { mutableStateOf<Painter?>(null) }
    val context = LocalPlatformContext.current
    val active = selected && foreground && autoplay

    DisposableEffect(playback, id) {
        playback.register(id) { selected = it }
        onDispose { playback.remove(id) }
    }
    LaunchedEffect(active, retry, media) {
        if (!active) return@LaunchedEffect
        paused = memory.paused(media.url)
        failure = false
        try {
            val loaded = presenter.load { progress = it }
            animation = loaded
            try {
                kotlinx.coroutines.awaitCancellation()
            } finally {
                animation = null
                presenter.release(loaded)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            failure = true
        }
    }
    LaunchedEffect(animation, active, paused) {
        val sequence = animation ?: return@LaunchedEffect
        if (!active) return@LaunchedEffect
        val frames = linkedMapOf<Int, Painter>()

        suspend fun decode(index: Int): Painter =
            frames
                .getOrPutSuspending(index) {
                    val result =
                        SingletonImageLoader.get(context).execute(
                            ImageRequest
                                .Builder(context)
                                .data(Uri(path = sequence.frames[index].file))
                                .size(4096, 4096)
                                .memoryCachePolicy(CachePolicy.DISABLED)
                                .diskCachePolicy(CachePolicy.DISABLED)
                                .build(),
                        )
                    check(result is SuccessResult) { "Unable to decode Ugoira frame" }
                    result.image.asPainter(context)
                }.also { while (frames.size > 2) frames.remove(frames.keys.first()) }
        var position = (memory.position(media.url) * 1000).toLong() % sequence.durationMillis
        val generation = memory.generation(media.url)
        val startPosition = position
        val clock = TimeSource.Monotonic.markNow()
        try {
            do {
                val index = sequence.frameIndex(position)
                painter = decode(index)
                if (paused) break
                decode((index + 1) % sequence.frames.size)
                val elapsed = clock.elapsedNow().inWholeMilliseconds
                val end =
                    position - position % sequence.durationMillis + sequence.frameStartMillis(index) + sequence.frames[index].delayMillis
                delay((end - startPosition - elapsed).coerceIn(1, 60_000))
                position = startPosition + clock.elapsedNow().inWholeMilliseconds
                if (generation == memory.generation(media.url)) memory.save(media.url, (position % sequence.durationMillis) / 1000.0)
            } while (true)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            failure = true
        } finally {
            if (!paused && generation == memory.generation(media.url)) {
                memory.save(
                    media.url,
                    ((startPosition + clock.elapsedNow().inWholeMilliseconds) % sequence.durationMillis) / 1000.0,
                )
            }
        }
    }
    Box(modifier.timelineVideoAutoplay(playback, id, autoplay && foreground, media.url)) {
        val frame = painter
        if (frame == null) {
            NetworkImage(
                media.previewUrl,
                media.accessibleDescription(),
                Modifier.matchParentSize(),
                contentScale = contentScale,
                customHeaders = media.customHeaders,
            )
        } else {
            Image(frame, media.accessibleDescription(), Modifier.matchParentSize(), contentScale = contentScale)
        }
        if (active && animation == null && !failure) {
            Column(
                Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = .6f)).padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PlatformCircularProgressIndicator()
                PlatformText("${(progress * 100).toInt()}%", color = Color.White)
            }
        }
        if (controls && active && (animation != null || failure)) {
            PlatformButton(onClick = {
                if (failure) {
                    scope.launch {
                        animation?.let { presenter.invalidate(it) }
                        retry++
                        painter = null
                    }
                } else {
                    paused = !paused
                    memory.savePaused(media.url, paused)
                }
            }, modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                PlatformText(
                    stringResource(
                        if (failure) {
                            Res.string.status_loadmore_error_retry
                        } else if (paused) {
                            Res.string.ugoira_play
                        } else {
                            Res.string.ugoira_pause
                        },
                    ),
                )
            }
        } else if (!active || failure) {
            FAIcon(
                FontAwesomeIcons.Solid.CirclePlay,
                contentDescription = stringResource(Res.string.ugoira_play),
                tint = Color.White,
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = .6f))
                        .padding(4.dp),
            )
        }
    }
}

private suspend fun <K, V> MutableMap<K, V>.getOrPutSuspending(
    key: K,
    value: suspend () -> V,
): V = get(key) ?: value().also { put(key, it) }

/** New gallery visits restart; returning from its viewer uses the saved page state. */
@Composable
public fun GalleryMedia(
    media: UiMedia,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    if (media is UiMedia.Ugoira) {
        rememberSaveable(media.url) {
            MediaPlaybackMemory.shared.reset(media.url)
            true
        }
        UgoiraPlayer(media, modifier, controls = true, contentScale = contentScale)
    } else {
        NetworkImage(media.url, media.accessibleDescription(), modifier, contentScale = contentScale, customHeaders = media.customHeaders)
    }
}

@Composable
internal expect fun platformMediaActive(): Boolean
