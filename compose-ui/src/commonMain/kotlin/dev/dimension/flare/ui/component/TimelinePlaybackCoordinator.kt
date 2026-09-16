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
    val isCarousel: Boolean = true,
)

internal class TimelinePlaybackCoordinator(
    val scope: CoroutineScope,
    private val arbiter: VideoPlaybackArbiter = VideoPlaybackArbiter.shared,
    private val memory: MediaPlaybackMemory = MediaPlaybackMemory.shared,
) {
    private val policy = TimelineAutoplayPolicy()
    private val candidates = linkedMapOf<Any, TimelineAutoplayPolicy.Candidate>()
    private val players = mutableMapOf<Any, (Boolean) -> Unit>()
    private val scrollingSources = mutableSetOf<Any>()
    private var selectionJob: Job? = null
    private var playingId: Any? = null
    private var closed = false
    private var suspended = false
    private var isPresentation = false
    private var immediateReturn = false
    var handoffUri: String? = null
        private set
    val mediaSelections = TimelineMediaSelections()
    private var viewerSelection: Pair<List<String>, String>? = null
    private val cleanup = mutableMapOf<Any, () -> Unit>()
    var viewport: Rect = Rect.Zero

    init {
        arbiter.register(
            this,
            ::stopCurrentPlayer,
            ::scheduleSelection,
            mediaSelections::returned,
            resume = {
                immediateReturn = true
                scheduleSelection()
            },
            willHandoff = { handoffUri = it },
        )
    }

    fun position(key: String): Double = memory.position(key)

    fun savePosition(
        key: String,
        seconds: Double,
    ) {
        memory.save(key, seconds)
    }

    fun selectMedia(
        groupId: Any,
        uri: String,
        userInitiated: Boolean,
    ) {
        if (userInitiated) {
            immediateReturn = false
            arbiter.interacted(this)
        }
        policy.returnedToMedia(groupId, uri)
        scheduleSelection()
    }

    fun selectViewerMedia(
        urls: List<String>,
        selectedUri: String?,
    ) {
        val selection = selectedUri?.takeIf { it in urls }?.let { urls.toList() to it }
        if (viewerSelection == selection) return
        viewerSelection = selection
        if (isPresentation) scheduleSelection()
    }

    fun present() {
        if (isPresentation) return
        isPresentation = true
        immediateReturn = true
        arbiter.present(this, viewerSelection?.second)
        scheduleSelection()
    }

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
            immediateReturn = false
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
        arbiter.remove(this, viewerSelection?.first.orEmpty(), viewerSelection?.second)
        stopCurrentPlayer()
        players.clear()
        candidates.clear()
        cleanup.values.toList().forEach { it() }
        cleanup.clear()
    }

    fun setSuspended(value: Boolean) {
        if (suspended == value) return
        suspended = value
        if (value) {
            selectionJob?.cancel()
            stopCurrentPlayer()
            arbiter.release(this)
        } else {
            scheduleSelection()
        }
    }

    private fun scheduleSelection() {
        selectionJob?.cancel()
        if (closed || suspended || scrollingSources.isNotEmpty()) return
        if (immediateReturn) {
            reconcile(allowStart = true)
            if (playingId != null) immediateReturn = false
            return
        }
        selectionJob =
            scope.launch {
                delay(200)
                reconcile(allowStart = true)
            }
    }

    private fun reconcile(allowStart: Boolean) {
        if (closed || suspended) return
        // Scrolling only invalidates the current item; full selection runs at idle.
        if (!allowStart || scrollingSources.isNotEmpty()) {
            if (playingId != null && candidates[playingId]?.visible != true) {
                stopCurrentPlayer()
                arbiter.release(this)
            }
            return
        }
        val selectedUri = viewerSelection?.second.takeIf { isPresentation }
        val eligible = if (selectedUri == null) candidates.values else candidates.values.filter { it.mediaUri == selectedUri }
        val next = policy.select(eligible, scrolling = false)
        if (next == null) arbiter.settledWithoutVideo(this)
        if (next == playingId) return
        if (next != null && !arbiter.acquire(this)) {
            stopCurrentPlayer()
            return
        }
        playingId?.let { players[it]?.invoke(false) }
        playingId = next
        next?.let {
            immediateReturn = false
            players[it]?.invoke(true)
        }
        if (next == null) arbiter.release(this)
    }

    private fun stopCurrentPlayer() {
        val old = playingId
        playingId = null
        old?.let { players[it]?.invoke(false) }
        handoffUri = null
        policy.select(emptyList(), scrolling = true)
    }
}

/** Keeps background timelines paused while any page of the media viewer is open. */
@Composable
public fun MediaViewerPlayback(content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val playback = remember(scope) { TimelinePlaybackCoordinator(scope) }
    CompositionLocalProvider(LocalTimelinePlayback provides playback, content = content)
    // Selection from the child is committed before preempting the timeline, so
    // the outgoing binding knows whether the same media is being handed over.
    SideEffect { playback.present() }
    DisposableEffect(playback) {
        onDispose { playback.close() }
    }
}

/** Records the last selected page for the timeline that opened this viewer. */
@Composable
public fun MediaViewerSelection(
    mediaUrls: List<String>,
    selectedUri: String?,
) {
    val playback = LocalTimelinePlayback.current
    SideEffect { playback?.selectViewerMedia(mediaUrls, selectedUri) }
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
    mediaUri: String,
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
                canStart = enabled && (item?.isCarousel != true || visible.width >= geometry.bounds.width * 0.6f),
                distance = abs(geometry.bounds.center.y - playback.viewport.center.y),
                mediaUri = mediaUri,
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
