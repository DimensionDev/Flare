package dev.dimension.flare.ui.presenter

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
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.data.model.tab.UiTimelineTabItem
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
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
    return rememberTimelineWithLazyListState(baseState, lazyStaggeredGridState, leadingItemCount, LocalDensity.current.density)
}

@Composable
internal fun rememberTimelineWithLazyListState(
    baseState: TimelineItemPresenter.State,
    lazyListState: LazyStaggeredGridState,
    leadingItemCount: Int = 0,
    density: Float = 1f,
): TimelineWithLazyListState {
    val latestReading by rememberUpdatedState(baseState.reading)
    var restoring by remember { mutableStateOf(false) }
    var scrollingByUser by remember { mutableStateOf(false) }
    var gapAnchor by remember { mutableStateOf<GapScrollAnchor?>(null) }
    var newPostCount by remember { mutableIntStateOf(0) }
    val isAtTheTop by remember(lazyListState) {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 &&
                lazyListState.firstVisibleItemScrollOffset == 0
        }
    }
    LaunchedEffect(lazyListState, baseState.reading != null) {
        // A tab's presenter can outlive its native list. Its new paging window may
        // start elsewhere, so a saved numeric scroll index is no longer sufficient.
        latestReading?.restoreCurrentPosition()
    }
    baseState.listState.onSuccess {
        val currentPagingState by rememberUpdatedState(this)
        LaunchedEffect(this, gapAnchor) {
            val anchor = gapAnchor ?: return@LaunchedEffect
            if (lazyListState.isScrollInProgress) {
                gapAnchor = null
                return@LaunchedEffect
            }
            val gap = (0 until itemCount).mapNotNull { peek(it) as? UiTimelineV2.Gap }.firstOrNull { it.stableItemKey == anchor.gapKey }
            if (anchor.itemKey == null && anchor.insertedKeys == null) return@LaunchedEffect
            val index =
                (0 until itemCount).firstOrNull {
                    val item = peek(it)
                    if (anchor.itemKey != null) {
                        item?.stableItemKey == anchor.itemKey
                    } else {
                        item != null && !item.isPaginationGap && item.stableItemKey in anchor.insertedKeys.orEmpty()
                    }
                } ?: return@LaunchedEffect
            restoring = true
            try {
                lazyListState.scrollToItem(index + anchor.headerCount + if (anchor.afterItem) 1 else 0, anchor.scrollOffset)
            } finally {
                restoring = false
            }
            val loading =
                gap?.loading == true
            if (anchor.itemKey == null || gap == null || (!loading && gap.renderHash != anchor.renderHash)) gapAnchor = null
        }
        val target = baseState.reading?.restorePosition
        // A replacement page can contain the target without changing itemCount.
        LaunchedEffect(target, this, lazyListState) {
            if (target == null || lazyListState.isScrollInProgress) return@LaunchedEffect
            val index =
                (0 until itemCount).firstOrNull { peek(it)?.stableItemKey == target.itemKey }
                    ?: return@LaunchedEffect
            restoring = true
            try {
                lazyListState.scrollToItem(index + leadingItemCount)
                val height =
                    lazyListState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.key == target.itemKey }
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
                    val keys =
                        (0 until currentPagingState.itemCount)
                            .mapNotNull {
                                currentPagingState.peek(it)?.takeUnless { item -> item.isPaginationGap }?.stableItemKey
                            }.toSet()
                    lazyListState.layoutInfo.visibleItemsInfo
                        .filter { it.key in keys }
                        .minWithOrNull(compareBy({ it.offset.y }, { it.offset.x }))
                        ?.let { visible ->
                            latestReading?.positionChanged(
                                visible.key as String,
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
                        (info.index - leadingItemCount)
                            .takeIf { it in 0 until currentPagingState.itemCount }
                            ?.let {
                                currentPagingState.peek(
                                    it,
                                )
                            }?.let {
                                !it.isPaginationGap && it.stableItemKey == info.key
                            } == true
                    }.minWithOrNull(compareBy({ it.offset.y }, { it.offset.x }))
                    ?.let { it.key to it.offset.y }
            }.collect { position ->
                if (!restoring && lazyListState.isScrollInProgress && position != null) {
                    latestReading?.viewportChanged(position.first as String, position.second.toDouble() / density)
                }
            }
        }
        DisposableEffect(lazyListState) {
            onDispose {
                if (scrollingByUser && !restoring && latestReading?.restorePosition == null) {
                    lazyListState.layoutInfo.visibleItemsInfo
                        .firstOrNull { info ->
                            val index = info.index - leadingItemCount
                            index in 0 until currentPagingState.itemCount && currentPagingState.peek(index)?.isPaginationGap == false
                        }?.let { latestReading?.positionChanged(it.key as String, it.offset.y.toDouble() / density) }
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
            baseState.listState.onSuccess {
                val gapIndex = (0 until itemCount).firstOrNull { peek(it)?.stableItemKey == message.stableItemKey } ?: return@onSuccess
                val previous = (gapIndex - 1 downTo 0).firstOrNull { peek(it)?.isPaginationGap == false }
                val key = previous?.let { peek(it)?.stableItemKey }
                if (key != null) {
                    val visible = lazyListState.layoutInfo.visibleItemsInfo
                    val above = visible.firstOrNull { it.key == key }
                    val gap = visible.firstOrNull { it.key == message.stableItemKey }
                    val headerCount = leadingItemCount
                    val top = above?.offset?.y ?: gap?.offset?.y ?: 0
                    gapAnchor =
                        GapScrollAnchor(message.stableItemKey, message.renderHash, key, -top, above == null, headerCount)
                    if (above != null) latestReading?.positionChanged(key, top.toDouble() / density)
                } else {
                    val top =
                        lazyListState.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.key == message.stableItemKey }
                            ?.offset
                            ?.y ?: 0
                    gapAnchor =
                        GapScrollAnchor(
                            message.stableItemKey,
                            message.renderHash,
                            null,
                            -top,
                            false,
                            leadingItemCount,
                        )
                }
            }
            val requestAnchor = gapAnchor
            message.onActivate?.invoke { keys ->
                if (gapAnchor === requestAnchor && requestAnchor?.itemKey == null) {
                    gapAnchor = requestAnchor?.takeIf { keys.isNotEmpty() }?.copy(insertedKeys = keys.toSet())
                }
            }
        }
    }
}

private data class GapScrollAnchor(
    val gapKey: String,
    val renderHash: Int,
    val itemKey: String?,
    val scrollOffset: Int,
    val afterItem: Boolean,
    val headerCount: Int,
    val insertedKeys: Set<String>? = null,
)
