package dev.dimension.flare.ui.presenter

import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withContext
import moe.tlaster.precompose.molecule.producePresenter
import kotlin.math.roundToInt

@Immutable
public interface TimelineWithLazyListState : TimelineItemPresenter.State {
    public val showNewToots: Boolean
    public val lazyListState: LazyStaggeredGridState
    public val newPostsCount: Int

    public fun onNewTootsShown()

    public fun loadGap(message: UiTimelineV2.Gap) {
        message.onActivate?.invoke {}
    }
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
    lazyStaggeredGridState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    leadingItemCount: Int = 0,
): TimelineWithLazyListState {
    val baseState by producePresenter("timeline_${item.id}_${item.contentSourceKey}_$isHomeTimeline") {
        remember(item.id, item.contentSourceKey, isHomeTimeline) { TimelineItemPresenter(item, isHomeTimeline) }.invoke()
    }
    return key(item.id, item.contentSourceKey, isHomeTimeline) {
        rememberTimelineWithLazyListState(baseState, lazyStaggeredGridState, leadingItemCount, LocalDensity.current.density)
    }
}

@Composable
internal fun rememberTimelineWithLazyListState(
    baseState: TimelineItemPresenter.State,
    lazyListState: LazyStaggeredGridState,
    leadingItemCount: Int = 0,
    density: Float = 1f,
): TimelineWithLazyListState {
    val latestReading by rememberUpdatedState(baseState.reading)
    val currentLeadingItemCount by rememberUpdatedState(leadingItemCount)
    var restoring by remember { mutableStateOf(false) }
    var scrollingByUser by remember { mutableStateOf(false) }
    var gapAnchor by remember { mutableStateOf<GapScrollAnchor?>(null) }
    var activatingGaps by remember { mutableStateOf(emptySet<Pair<String, Long>>()) }
    var newPostCount by remember { mutableIntStateOf(0) }
    val isAtTheTop by remember(lazyListState) {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 &&
                lazyListState.firstVisibleItemScrollOffset == 0
        }
    }
    LaunchedEffect(lazyListState, baseState.reading != null) {
        latestReading?.restoreCurrentPosition()
    }
    if (baseState.listState is PagingState.Empty) SideEffect { gapAnchor = null }
    baseState.listState.onSuccess {
        val currentPagingState by rememberUpdatedState(this)
        SideEffect {
            val anchor = gapAnchor
            if (anchor != null && !lazyListState.isScrollInProgress && latestReading?.restorePosition == null) {
                val index = (0 until itemCount).firstOrNull { peek(it)?.stableItemKey == anchor.itemKey }
                val visible = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == anchor.itemKey }
                if (index != null && (visible?.index != index + leadingItemCount || visible.offset.y != -anchor.scrollOffset)) {
                    lazyListState.requestScrollToItem(index + leadingItemCount, anchor.scrollOffset)
                }
            }
        }
        LaunchedEffect(this, gapAnchor, leadingItemCount) {
            val anchor = gapAnchor ?: return@LaunchedEffect
            val index = (0 until itemCount).firstOrNull { peek(it)?.stableItemKey == anchor.itemKey }
            if (index == null) {
                val keys = (0 until itemCount).mapNotNull { peek(it)?.stableItemKey }.toSet()
                val oldIndex = anchor.itemOrder.indexOf(anchor.itemKey)
                // A clipped paging window is not evidence that the reading item was deleted.
                if (anchor.itemOrder.take(oldIndex).any { it in keys } &&
                    anchor.itemOrder.drop(oldIndex + 1).any { it in keys }
                ) {
                    gapAnchor = null
                }
                return@LaunchedEffect
            }
            // Queue a remeasure during layout notification, before the displaced item is drawn.
            withContext(Dispatchers.Main.immediate) {
                snapshotFlow {
                    val layout = lazyListState.layoutInfo
                    layout.totalItemsCount to layout.visibleItemsInfo.map { Triple(it.key, it.offset, it.size) }
                }.collect {
                    if (lazyListState.isScrollInProgress || latestReading?.restorePosition != null) return@collect
                    val visible = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == anchor.itemKey }
                    if (visible?.index == index + leadingItemCount && visible.offset.y == -anchor.scrollOffset) return@collect
                    restoring = true
                    try {
                        lazyListState.requestScrollToItem(index + leadingItemCount, anchor.scrollOffset)
                    } finally {
                        restoring = false
                    }
                }
            }
        }
        val target = baseState.reading?.restorePosition
        // A replacement page can contain the target without changing itemCount.
        LaunchedEffect(target, this, lazyListState, leadingItemCount) {
            if (target == null || lazyListState.isScrollInProgress) return@LaunchedEffect
            gapAnchor = null
            val index = target.index.takeIf { it in 0 until itemCount && peek(it) != null } ?: return@LaunchedEffect
            restoring = true
            try {
                lazyListState.scrollToItem(index + leadingItemCount)
                val height =
                    lazyListState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.index == index + leadingItemCount }
                        ?.size
                        ?.height
                val offset = (-target.offset * density).roundToInt().coerceAtMost(height?.minus(1)?.coerceAtLeast(0) ?: Int.MAX_VALUE)
                if (offset != 0) lazyListState.scrollToItem(index + leadingItemCount, offset)
                latestReading?.restorationFinished()
            } finally {
                restoring = false
            }
        }
        LaunchedEffect(lazyListState) {
            var userScrolled = false
            snapshotFlow { lazyListState.isScrollInProgress }.distinctUntilChanged().collect { scrolling ->
                if (restoring) return@collect
                if (scrolling) {
                    gapAnchor = null
                    userScrolled = true
                    scrollingByUser = true
                    latestReading?.restorationFinished()
                } else if (userScrolled) {
                    lazyListState.layoutInfo.visibleItemsInfo
                        .filter { info ->
                            val index = info.index - currentLeadingItemCount
                            info.offset.y + info.size.height > 0 && index in 0 until currentPagingState.itemCount &&
                                currentPagingState.peek(index)?.let { !it.isPaginationGap && it.stableItemKey == info.key } == true
                        }.minWithOrNull(compareBy({ it.offset.y }, { it.offset.x }))
                        ?.let { visible ->
                            latestReading?.positionChanged(
                                visible.index - currentLeadingItemCount,
                                visible.offset.y.toDouble() / density,
                            )
                        }
                    userScrolled = false
                    scrollingByUser = false
                }
            }
        }
        LaunchedEffect(lazyListState) {
            snapshotFlow {
                lazyListState.layoutInfo.visibleItemsInfo
                    .filter { info ->
                        info.offset.y + info.size.height > 0 && (info.index - currentLeadingItemCount)
                            .takeIf { it in 0 until currentPagingState.itemCount }
                            ?.let {
                                currentPagingState.peek(
                                    it,
                                )
                            }?.let {
                                !it.isPaginationGap && it.stableItemKey == info.key
                            } == true
                    }.minWithOrNull(compareBy({ it.offset.y }, { it.offset.x }))
                    ?.let { (it.index - currentLeadingItemCount) to it.offset.y }
            }.collect { position ->
                if (!restoring && position != null) {
                    latestReading?.viewportChanged(position.first, position.second.toDouble() / density)
                }
            }
        }
        DisposableEffect(lazyListState) {
            onDispose {
                if (scrollingByUser && !restoring && latestReading?.restorePosition == null) {
                    lazyListState.layoutInfo.visibleItemsInfo
                        .firstOrNull { info ->
                            val index = info.index - currentLeadingItemCount
                            info.offset.y + info.size.height > 0 && index in 0 until currentPagingState.itemCount &&
                                currentPagingState.peek(index)?.let { !it.isPaginationGap && it.stableItemKey == info.key } == true
                        }?.let { latestReading?.positionChanged(it.index - currentLeadingItemCount, it.offset.y.toDouble() / density) }
                }
                latestReading?.savePosition()
            }
        }
        LaunchedEffect(lazyListState) {
            var previousKeys = emptySet<String>()
            snapshotFlow {
                val pagingState = currentPagingState
                (0 until pagingState.itemCount).mapNotNull { pagingState.peek(it)?.itemKey }
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
                    val pagingState = currentPagingState
                    val postIndex =
                        if (key == null) {
                            index
                        } else {
                            (0 until pagingState.itemCount).indexOfFirst { pagingState.peek(it)?.itemKey == key }
                        }
                    if (postIndex >= 0) {
                        newPostCount = minOf(newPostCount, postIndex)
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
        override val showNewToots = baseState.reading?.hasNewContent ?: (newPostCount > 0)
        override val lazyListState = lazyListState
        override val newPostsCount = newPostCount

        override fun onNewTootsShown() {
            newPostCount = 0
        }

        override fun loadGap(message: UiTimelineV2.Gap) {
            val activate = message.onActivate ?: return
            val request = message.stableItemKey to message.revision
            if (message.loading || request in activatingGaps) return
            activatingGaps += request
            gapAnchor = null
            baseState.listState.onSuccess {
                val gapIndex = (0 until itemCount).firstOrNull { peek(it)?.stableItemKey == message.stableItemKey } ?: return@onSuccess
                val next = (gapIndex + 1 until itemCount).firstOrNull { peek(it)?.isPaginationGap == false }
                val visible = lazyListState.layoutInfo.visibleItemsInfo
                val below = next?.let { index -> visible.firstOrNull { it.key == peek(index)?.stableItemKey } }
                val anchor =
                    below ?: visible
                        .filter { info ->
                            val index = info.index - leadingItemCount
                            info.offset.y + info.size.height > 0 && index in 0 until itemCount &&
                                peek(index)?.let { !it.isPaginationGap && it.stableItemKey == info.key } == true
                        }.minWithOrNull(compareBy({ it.offset.y }, { it.offset.x }))
                if (anchor != null) {
                    gapAnchor =
                        GapScrollAnchor(
                            checkNotNull(peek(anchor.index - leadingItemCount)).stableItemKey,
                            -anchor.offset.y,
                            (0 until itemCount).mapNotNull { peek(it)?.takeUnless { it.isPaginationGap }?.stableItemKey },
                        )
                }
            }
            activate { activatingGaps -= request }
        }
    }
}

private data class GapScrollAnchor(
    val itemKey: String,
    val scrollOffset: Int,
    val itemOrder: List<String>,
)
