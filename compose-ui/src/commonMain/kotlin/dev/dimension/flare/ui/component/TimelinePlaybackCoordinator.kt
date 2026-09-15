package dev.dimension.flare.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

internal val LocalTimelinePlayback = compositionLocalOf<TimelinePlaybackCoordinator?> { null }
internal val LocalTimelineCarouselItem = compositionLocalOf<TimelineCarouselItem?> { null }

internal data class TimelineCarouselItem(
    val groupId: Any,
    val selected: Boolean,
)

internal class TimelinePlaybackCoordinator(
    val scope: CoroutineScope,
    private val arbiter: VideoPlaybackArbiter = VideoPlaybackArbiter.shared,
) {
    private val policy = TimelineAutoplayPolicy()
    private val candidates = linkedMapOf<Any, TimelineAutoplayPolicy.Candidate>()
    private val players = mutableMapOf<Any, (Boolean) -> Unit>()
    private val scrollingSources = mutableSetOf<Any>()
    private var selectionJob: Job? = null
    private var playingId: Any? = null
    private var closed = false
    private val positions = mutableMapOf<Any, Double>()
    private val cleanup = mutableMapOf<Any, () -> Unit>()
    var viewport: Rect = Rect.Zero

    init {
        arbiter.register(this, ::stopCurrentPlayer, ::scheduleSelection)
    }

    fun position(key: Any): Double = positions[key] ?: 0.0

    fun savePosition(
        key: Any,
        seconds: Double,
    ) {
        if (seconds.isFinite() && seconds >= 0) positions[key] = seconds
    }

    fun present() = arbiter.present(this)

    fun onClose(
        key: Any,
        action: () -> Unit,
    ) {
        cleanup[key] = action
    }

    fun register(
        id: Any,
        playback: (Boolean) -> Unit,
    ) {
        players[id] = playback
    }

    fun update(candidate: TimelineAutoplayPolicy.Candidate) {
        if (candidates[candidate.id] == candidate) return
        candidates[candidate.id] = candidate
        reconcile(allowStart = false)
        scheduleSelection()
    }

    fun remove(id: Any) {
        candidates.remove(id)
        reconcile(allowStart = false)
        players.remove(id)
        scheduleSelection()
    }

    fun setScrolling(
        source: Any,
        scrolling: Boolean,
        vertical: Boolean,
    ) {
        if (scrolling) {
            if (scrollingSources.add(source)) {
                arbiter.interacted(this)
                if (vertical) policy.verticalScrollBegan() else policy.interactWithCarousel(source)
            }
            selectionJob?.cancel()
        } else {
            scrollingSources.remove(source)
            scheduleSelection()
        }
    }

    fun close() {
        closed = true
        selectionJob?.cancel()
        arbiter.remove(this)
        stopCurrentPlayer()
        players.clear()
        candidates.clear()
        cleanup.values.toList().forEach { it() }
        cleanup.clear()
        positions.clear()
    }

    private fun scheduleSelection() {
        selectionJob?.cancel()
        if (closed || scrollingSources.isNotEmpty()) return
        selectionJob =
            scope.launch {
                delay(200)
                reconcile(allowStart = true)
            }
    }

    private fun reconcile(allowStart: Boolean) {
        if (closed) return
        // Scrolling only invalidates the current item; full selection runs at idle.
        if (!allowStart || scrollingSources.isNotEmpty()) {
            if (playingId != null && candidates[playingId]?.visible != true) {
                stopCurrentPlayer()
                arbiter.release(this)
            }
            return
        }
        val next = policy.select(candidates.values, scrolling = false)
        if (next == null) arbiter.settledWithoutVideo(this)
        if (next == playingId) return
        if (next != null && !arbiter.acquire(this)) {
            stopCurrentPlayer()
            return
        }
        playingId?.let { players[it]?.invoke(false) }
        playingId = next
        next?.let { players[it]?.invoke(true) }
        if (next == null) arbiter.release(this)
    }

    private fun stopCurrentPlayer() {
        val old = playingId
        playingId = null
        old?.let { players[it]?.invoke(false) }
        policy.select(emptyList(), scrolling = true)
    }
}

/** Keeps background timelines paused while any page of the media viewer is open. */
@Composable
public fun MediaViewerPlayback(content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val playback = remember(scope) { TimelinePlaybackCoordinator(scope) }
    DisposableEffect(playback) {
        playback.present()
        onDispose { playback.close() }
    }
    CompositionLocalProvider(LocalTimelinePlayback provides playback, content = content)
}

@Composable
internal fun rememberTimelinePlayback(): TimelinePlaybackCoordinator {
    val inherited = LocalTimelinePlayback.current
    val scope = rememberCoroutineScope()
    val playback = inherited ?: remember(scope) { TimelinePlaybackCoordinator(scope) }
    DisposableEffect(playback, inherited) {
        onDispose { if (inherited == null) playback.close() }
    }
    return playback
}

private class VideoGeometry {
    var bounds: Rect = Rect.Zero
    var visible: Rect = Rect.Zero
}

@Composable
internal fun Modifier.timelineVideoAutoplay(
    playback: TimelinePlaybackCoordinator,
    id: Any,
    enabled: Boolean,
): Modifier {
    val item = LocalTimelineCarouselItem.current
    val geometry = remember(id) { VideoGeometry() }

    fun update() {
        val visible = if (playback.viewport.isEmpty) geometry.visible else geometry.visible.intersect(playback.viewport)
        playback.update(
            TimelineAutoplayPolicy.Candidate(
                id = id,
                groupId = item?.groupId,
                visible = enabled && !visible.isEmpty,
                selected = item?.selected ?: true,
                canStart = enabled && (item == null || visible.width >= geometry.bounds.width * 0.6f),
                distance = abs(geometry.bounds.center.y - playback.viewport.center.y),
            ),
        )
    }
    SideEffect { update() }
    return onGloballyPositioned {
        geometry.bounds = Rect(it.positionInWindow(), it.size.toSize())
        geometry.visible = it.boundsInWindow()
        update()
    }
}
