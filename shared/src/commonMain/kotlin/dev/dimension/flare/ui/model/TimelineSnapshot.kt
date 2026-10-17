package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.common.PagingState

/** Queries belong to the filtered, published items, including their original paging indices. */
@Immutable
public class TimelineSnapshot(
    data: PagingState<UiTimelineV2>,
) {
    private val items = data as? PagingState.Success

    private class Entry(
        val position: Int,
        val readable: Boolean,
        val hasNoticeKey: Boolean,
    )

    private class Index(
        val positions: Map<String, Entry>,
        val firstReadingKey: String?,
    )

    private val index by lazy {
        val positions = LinkedHashMap<String, Entry>()
        var firstReadingKey: String? = null
        items?.let { data ->
            repeat(data.itemCount) { index ->
                data.peek(index)?.let { item ->
                    if (item.stableItemKey !in
                        positions
                    ) {
                        positions[item.stableItemKey] = Entry(index, !item.isReadingGap, item.itemKey != null)
                    }
                    if (firstReadingKey == null && !item.isReadingGap) firstReadingKey = item.stableItemKey
                }
            }
        }
        Index(positions, firstReadingKey)
    }

    public val firstReadingKey: String? get() = index.firstReadingKey
    public val noticeKeys: List<String> by lazy {
        buildList { items?.let { data -> repeat(data.itemCount) { data.peek(it)?.itemKey?.let(::add) } } }
    }

    public fun noticeIndexOf(key: String): Int = index.positions[key]?.takeIf { it.hasNoticeKey }?.position ?: -1

    public fun readingIndexOf(key: String): Int = index.positions[key]?.takeIf { it.readable }?.position ?: -1

    public fun restorationIndex(
        position: TimelineReadingPosition,
        refreshing: Boolean,
    ): Int {
        if (refreshing) return -1
        val index = readingIndexOf(position.itemKey)
        return index.takeIf { position.canRestore(it, refreshing) } ?: -1
    }

    private val content by lazy {
        items
            ?.let { data ->
                List(data.itemCount) { index -> data.peek(index)?.let { it.stableItemKey to it.renderHash } }
            }.orEmpty()
    }

    public fun hasSameContent(other: TimelineSnapshot?): Boolean = this === other || (other != null && content == other.content)
}
