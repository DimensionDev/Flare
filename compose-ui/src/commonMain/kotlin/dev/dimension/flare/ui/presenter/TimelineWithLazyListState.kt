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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.data.model.tab.UiTimelineTabItem
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
    var newPostCount by remember { mutableIntStateOf(0) }
    val reading by rememberUpdatedState(baseState.readingState)
    val paging by rememberUpdatedState(baseState.listState)
    val density = if (baseState.readingState != null) LocalDensity.current.density else 1f
    val loadedKeys by rememberUpdatedState(
        remember(baseState.listState) {
            (baseState.listState as? PagingState.Success)
                ?.let { data ->
                    (0 until data.itemCount).mapNotNull { data.peek(it)?.stableItemKey }.toSet()
                }.orEmpty()
        },
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
        snapshotFlow {
            val layout = lazyListState.layoutInfo
            val visible =
                layout.visibleItemsInfo
                    .filter {
                        it.key in loadedKeys && it.offset.y + it.size.height > layout.viewportStartOffset
                    }.minWithOrNull(compareBy({ it.offset.y }, { it.offset.x }))
            TimelineViewport(
                visible?.key as? String,
                (visible?.offset?.y ?: 0).toDouble() / density,
                lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0,
                lazyListState.isScrollInProgress,
            )
        }.collect { viewport ->
            reading?.updateViewport(viewport.key, viewport.offset, viewport.atTop, viewport.interacting)
        }
    }
    val requestedPosition = baseState.readingState?.position
    LaunchedEffect(lazyListState, requestedPosition?.requestId, baseState.readingState != null) {
        val position = requestedPosition ?: return@LaunchedEffect
        val (_, target) =
            snapshotFlow {
                val data = paging as? PagingState.Success
                val index = data?.let { (0 until it.itemCount).firstOrNull { index -> it.peek(index)?.stableItemKey == position.itemKey } }
                if (data != null && index != null && !data.isRefreshing) data to index else null
            }.first { it != null }!!
        lazyListState.scrollToItem(
            if (position.latest) 0 else target + leadingItemCount,
            if (position.latest) 0 else (-position.offset * density).roundToInt(),
        )
        reading?.positionRestored(position.requestId)
    }
    val isAtTheTop by remember(lazyListState) {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 &&
                lazyListState.firstVisibleItemScrollOffset == 0
        }
    }
    baseState.listState.onSuccess {
        val currentPagingState by rememberUpdatedState(this)
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
        override val showNewToots = baseState.readingState?.hasNewContent ?: (newPostCount > 0)
        override val lazyListState = lazyListState
        override val newPostsCount = newPostCount

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
)
