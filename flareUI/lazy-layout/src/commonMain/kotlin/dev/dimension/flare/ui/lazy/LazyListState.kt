package dev.dimension.flare.ui.lazy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.ui.FlareUiComposable
import kotlinx.coroutines.CompletableDeferred

@Immutable
public data class LazyListItemInfo(
    public val key: Any,
    public val index: Int,
    public val offset: Float,
    public val size: Float,
)

@Immutable
public data class LazyListLayoutInfo(
    public val totalItemsCount: Int = 0,
    public val viewportStartOffset: Float = 0f,
    public val viewportEndOffset: Float = 0f,
    public val visibleItems: List<LazyListItemInfo> = emptyList(),
)

/** Observable viewport state and programmatic scrolling controller for one attached lazy list. */
@Stable
public class LazyListState internal constructor() {
    /** The most recent viewport and visible-item snapshot reported by the platform renderer. */
    public var layoutInfo: LazyListLayoutInfo by mutableStateOf(LazyListLayoutInfo())
        internal set

    /** Whether the native list is currently being dragged, flung, or programmatically animated. */
    public var isScrollInProgress: Boolean by mutableStateOf(false)
        internal set

    private var attachment: LazyListStateAttachment? = null
    private var activeRequest: LazyListScrollRequest? = null

    /** Immediately positions [index], with positive [scrollOffset] scrolling farther forward. */
    public suspend fun scrollToItem(
        index: Int,
        scrollOffset: Float = 0f,
    ) {
        requestScroll(index, scrollOffset, animated = false)
    }

    /** Animates to [index], with positive [scrollOffset] scrolling farther forward. */
    public suspend fun animateScrollToItem(
        index: Int,
        scrollOffset: Float = 0f,
    ) {
        requestScroll(index, scrollOffset, animated = true)
    }

    internal fun attach(
        owner: Any,
        itemCount: Int,
        onScroll: (LazyListScrollRequest) -> Unit,
    ) {
        val current = attachment
        check(current == null || current.owner === owner) {
            "A LazyListState cannot control more than one lazy collection at the same time."
        }
        attachment = LazyListStateAttachment(owner, itemCount, onScroll)
        layoutInfo =
            layoutInfo.copy(
                totalItemsCount = itemCount,
                visibleItems = layoutInfo.visibleItems.filter { it.index < itemCount },
            )
        if (activeRequest?.index?.let { it >= itemCount } == true) {
            activeRequest?.cancel()
            activeRequest = null
        }
    }

    internal fun detach(owner: Any) {
        if (attachment?.owner !== owner) return
        attachment = null
        activeRequest?.cancel()
        activeRequest = null
        isScrollInProgress = false
    }

    internal fun updateLayoutInfo(
        owner: Any,
        value: LazyListLayoutInfo,
    ) {
        if (attachment?.owner === owner) {
            layoutInfo = value
        }
    }

    internal fun updateScrollInProgress(
        owner: Any,
        value: Boolean,
    ) {
        if (attachment?.owner === owner) {
            isScrollInProgress = value
        }
    }

    private suspend fun requestScroll(
        index: Int,
        scrollOffset: Float,
        animated: Boolean,
    ) {
        require(index >= 0) { "Lazy list scroll index must be non-negative." }
        require(scrollOffset.isFinite()) { "Lazy list scroll offset must be finite." }
        val target =
            checkNotNull(attachment) {
                "LazyListState is not attached to a lazy collection."
            }
        require(index < target.itemCount) {
            "Lazy list scroll index $index is outside 0 until ${target.itemCount}."
        }
        activeRequest?.cancel()
        val request = LazyListScrollRequest(index, scrollOffset, animated)
        activeRequest = request
        try {
            target.onScroll(request)
            request.awaitCompletion()
        } finally {
            if (activeRequest === request) {
                activeRequest = null
            }
        }
    }
}

/** Remembers a [LazyListState] scoped to the current Flare composition. */
@Composable
@FlareUiComposable
public fun rememberLazyListState(): LazyListState = remember { LazyListState() }

private class LazyListStateAttachment(
    val owner: Any,
    val itemCount: Int,
    val onScroll: (LazyListScrollRequest) -> Unit,
)

internal class LazyListScrollRequest(
    val index: Int,
    val scrollOffset: Float,
    val animated: Boolean,
) {
    private val completion = CompletableDeferred<Unit>()

    fun complete() {
        completion.complete(Unit)
    }

    fun cancel() {
        completion.cancel()
    }

    suspend fun awaitCompletion() {
        completion.await()
    }
}
