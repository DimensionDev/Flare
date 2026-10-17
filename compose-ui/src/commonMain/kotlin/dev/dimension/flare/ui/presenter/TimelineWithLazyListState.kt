package dev.dimension.flare.ui.presenter

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.data.model.tab.UiTimelineTabItem
import dev.dimension.flare.ui.model.TimelineSnapshot
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import moe.tlaster.precompose.molecule.producePresenter
import kotlin.math.roundToInt

@Immutable
public interface TimelineWithLazyListState : TimelineItemPresenter.State {
    public val showNewToots: Boolean
    public val lazyListState: LazyStaggeredGridState
    public val newPostsCount: Int

    public fun onNewTootsShown()
}

/**
 * UI-side composable that exposes the timeline paging state plus scroll-bound indicator state
 * (new-toots banner, scroll-to-top etc.) bound to the supplied [lazyStaggeredGridState].
 *
 * The paging/refresh portion runs inside a molecule presenter scoped to a `ViewModel`
 * (so it survives configuration changes), while the lazyListState-dependent effects run in
 * plain Composition. This avoids capturing a stale `LazyStaggeredGridState` across Activity
 * recreation — every fresh Composition rebinds its own [lazyStaggeredGridState] to the effects.
 */
@Composable
public fun rememberTimelineItemPresenterWithLazyListState(
    item: UiTimelineTabItem,
    isHomeTimeline: Boolean = false,
    leadingItemCount: Int = 0,
    lazyStaggeredGridState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
): TimelineWithLazyListState {
    val baseState by producePresenter("timeline_${item.id}_${item.contentSourceKey}_$isHomeTimeline") {
        remember(item, isHomeTimeline) { TimelineItemPresenter(item, isHomeTimeline) }.invoke()
    }
    return rememberTimelineWithLazyListState(baseState, lazyStaggeredGridState, leadingItemCount)
}

@Composable
internal fun rememberTimelineWithLazyListState(
    baseState: TimelineItemPresenter.State,
    lazyListState: LazyStaggeredGridState,
    leadingItemCount: Int = 0,
): TimelineWithLazyListState {
    var newPostCount by remember(baseState.readingState != null) { mutableIntStateOf(0) }
    var restoringPosition by remember(lazyListState) { mutableStateOf(false) }
    val reading by rememberUpdatedState(baseState.readingState)
    val paging by rememberUpdatedState(baseState.listState)
    val leadingItems by rememberUpdatedState(leadingItemCount)
    val density = if (baseState.readingState != null) LocalDensity.current.density else 1f
    val snapshot by rememberUpdatedState(
        baseState.snapshot ?: remember(baseState.listState) { TimelineSnapshot(baseState.listState) },
    )
    DisposableEffect(lazyListState) {
        onDispose { reading?.savePosition() }
    }
    LaunchedEffect(lazyListState) {
        lazyListState.interactionSource.interactions.collect {
            if (it is DragInteraction.Start) reading?.cancelRestoration()
        }
    }
    LaunchedEffect(lazyListState, baseState.readingState != null, density) {
        if (reading == null) return@LaunchedEffect
        var previousSnapshot: TimelineSnapshot? = null
        var previousLeadingItems = leadingItems
        var previousViewport: TimelineViewport? = null
        var pendingAnchor: TimelineViewport? = null
        snapshotFlow { Triple(snapshot, lazyListState.layoutInfo, lazyListState.isScrollInProgress) }
            .collect { (currentSnapshot, measuredLayout, scrolling) ->
                val previous = pendingAnchor ?: previousViewport
                if ((currentSnapshot !== previousSnapshot || leadingItems != previousLeadingItems) && previous != null) {
                    val index = previous.key?.let(currentSnapshot::readingIndexOf) ?: -1
                    if (index >= 0 && index + leadingItems != previous.index) pendingAnchor = previous
                }
                previousSnapshot = currentSnapshot
                previousLeadingItems = leadingItems
                if (reading?.position != null || restoringPosition) pendingAnchor = null
                pendingAnchor?.let { anchor ->
                    val index = anchor.key?.let(currentSnapshot::readingIndexOf) ?: -1
                    val item = measuredLayout.visibleItemsInfo.firstOrNull { it.key == anchor.key }
                    if (index < 0 || item == null) {
                        pendingAnchor = null
                    } else if (item.index == index + leadingItems) {
                        pendingAnchor = null
                        // Key preservation can change staggered-grid lanes and lose the pixel offset.
                        val delta = item.offset.y - (anchor.offset * density).roundToInt()
                        if (delta != 0) lazyListState.dispatchRawDelta(delta.toFloat())
                    }
                }
                val layout = lazyListState.layoutInfo
                val visible =
                    layout.visibleItemsInfo
                        .filter {
                            (it.key as? String)?.let { key -> currentSnapshot.readingIndexOf(key) >= 0 } == true &&
                                it.offset.y + it.size.height > layout.viewportStartOffset
                        }.minWithOrNull(compareBy({ it.offset.y }, { it.offset.x }))
                val key = visible?.key as? String
                if (visible != null && key != null && currentSnapshot.readingIndexOf(key) + leadingItems != visible.index) {
                    return@collect
                }
                val viewport =
                    TimelineViewport(
                        key,
                        (visible?.offset?.y ?: 0).toDouble() / density,
                        lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0,
                        scrolling && !restoringPosition,
                        visible?.index ?: -1,
                    )
                if (viewport != previousViewport) {
                    previousViewport = viewport
                    reading?.updateViewport(viewport.key, viewport.offset, viewport.atTop, viewport.interacting)
                }
            }
    }
    val requestedPosition = baseState.readingState?.position
    LaunchedEffect(lazyListState, requestedPosition?.requestId, baseState.readingState != null, leadingItemCount) {
        val position = requestedPosition ?: return@LaunchedEffect
        val (_, target) =
            snapshotFlow {
                val data = paging as? PagingState.Success
                val index = snapshot.restorationIndex(position, refreshing = data?.isRefreshing != false)
                // An overlapping old snapshot may already contain the requested new head.
                if (data != null && index >= 0) data to index else null
            }.first { it != null }!!
        restoringPosition = true
        try {
            lazyListState.scrollToItem(
                if (position.latest) 0 else target + leadingItemCount,
                if (position.latest) 0 else (-position.offset * density).roundToInt(),
            )
            reading?.positionRestored(position.requestId)
        } finally {
            restoringPosition = false
        }
    }
    val isAtTheTop by remember(lazyListState) {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 &&
                lazyListState.firstVisibleItemScrollOffset == 0
        }
    }
    if (baseState.readingState == null) {
        baseState.listState.onSuccess {
            LaunchedEffect(lazyListState) {
                var previousKeys = emptySet<String>()
                snapshotFlow {
                    snapshot.noticeKeys
                }.collect { keys ->
                    if (keys.isNotEmpty()) {
                        // Count the new prefix before the first previously loaded post.
                        // Scroll indices can already have moved by the time this snapshot arrives.
                        if (previousKeys.isNotEmpty() && !isAtTheTop) {
                            newPostCount += keys.takeWhile { it !in previousKeys }.size
                        }
                        previousKeys = keys.toSet()
                    }
                }
            }
            LaunchedEffect(lazyListState) {
                snapshotFlow {
                    val index = lazyListState.firstVisibleItemIndex
                    index to
                        lazyListState.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.index == index }
                            ?.key
                }.drop(1)
                    .collect { (index, key) ->
                        // Consume posts on viewport changes, not paging updates whose
                        // new indices may arrive before the grid preserves its position.
                        // A measured item's key also excludes any leading header cards.
                        if (newPostCount == 0) return@collect
                        val postIndex =
                            if (key == null) {
                                index
                            } else {
                                (key as? String)?.let(snapshot::noticeIndexOf) ?: -1
                            }
                        if (postIndex >= 0) {
                            newPostCount = minOf(newPostCount, postIndex)
                        }
                    }
            }
        }
    }
    LaunchedEffect(isAtTheTop) {
        if (isAtTheTop) {
            newPostCount = 0
        }
    }
    return object :
        TimelineWithLazyListState,
        TimelineItemPresenter.State by baseState {
        override val showNewToots = baseState.readingState?.hasNewContent ?: (newPostCount > 0)
        override val lazyListState = lazyListState
        override val newPostsCount = if (baseState.readingState != null) 0 else newPostCount

        override fun onNewTootsShown() {
            newPostCount = 0
        }
    }
}

private data class TimelineViewport(
    val key: String?,
    val offset: Double,
    val atTop: Boolean,
    val interacting: Boolean,
    val index: Int,
)
