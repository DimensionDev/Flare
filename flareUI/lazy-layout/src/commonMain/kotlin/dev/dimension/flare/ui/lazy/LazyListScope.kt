@file:OptIn(dev.dimension.flare.ui.LowLevelFlareApi::class)
@file:Suppress("ktlint:standard:annotation")

package dev.dimension.flare.ui.lazy

import androidx.compose.runtime.Composable
import dev.dimension.flare.ui.FlareContent
import dev.dimension.flare.ui.FlareUiComposable

@DslMarker
public annotation class LazyListScopeMarker

public typealias LazyIndexedContent = @Composable @FlareUiComposable (index: Int) -> Unit

public typealias LazyItemContent<T> = @Composable @FlareUiComposable (item: T) -> Unit

public typealias LazyIndexedItemContent<T> = @Composable @FlareUiComposable (index: Int, item: T) -> Unit

/** Declarative item builder which records intervals without composing their contents. */
@LazyListScopeMarker
public interface LazyListScope {
    /** Adds one item. [key] identifies its state; [contentType] identifies reuse compatibility. */
    public fun item(
        key: Any,
        contentType: Any? = null,
        content: FlareContent,
    )

    /** Adds [count] deferred items without composing any item while the interval is declared. */
    public fun items(
        count: Int,
        key: (index: Int) -> Any,
        contentType: (index: Int) -> Any? = { null },
        itemContent: LazyIndexedContent,
    )
}

public fun <T> LazyListScope.items(
    items: List<T>,
    key: (T) -> Any,
    contentType: (T) -> Any? = { null },
    itemContent: LazyItemContent<T>,
) {
    items(
        count = items.size,
        key = { index -> key(items[index]) },
        contentType = { index -> contentType(items[index]) },
        itemContent = { index -> itemContent(items[index]) },
    )
}

public fun <T> LazyListScope.items(
    items: Array<T>,
    key: (T) -> Any,
    contentType: (T) -> Any? = { null },
    itemContent: LazyItemContent<T>,
) {
    items(
        count = items.size,
        key = { index -> key(items[index]) },
        contentType = { index -> contentType(items[index]) },
        itemContent = { index -> itemContent(items[index]) },
    )
}

public fun <T> LazyListScope.itemsIndexed(
    items: List<T>,
    key: (index: Int, item: T) -> Any,
    contentType: (index: Int, item: T) -> Any? = { _, _ -> null },
    itemContent: LazyIndexedItemContent<T>,
) {
    items(
        count = items.size,
        key = { index -> key(index, items[index]) },
        contentType = { index -> contentType(index, items[index]) },
        itemContent = { index -> itemContent(index, items[index]) },
    )
}

public fun <T> LazyListScope.itemsIndexed(
    items: Array<T>,
    key: (index: Int, item: T) -> Any,
    contentType: (index: Int, item: T) -> Any? = { _, _ -> null },
    itemContent: LazyIndexedItemContent<T>,
) {
    items(
        count = items.size,
        key = { index -> key(index, items[index]) },
        contentType = { index -> contentType(index, items[index]) },
        itemContent = { index -> itemContent(index, items[index]) },
    )
}

internal class IntervalLazyListScope : LazyListScope {
    private val intervals = mutableListOf<LazyItemInterval>()
    private var itemCount: Int = 0

    override fun item(
        key: Any,
        contentType: Any?,
        content: FlareContent,
    ) {
        addInterval(
            count = 1,
            key = { key },
            contentType = { contentType },
            itemContent = { content() },
        )
    }

    override fun items(
        count: Int,
        key: (index: Int) -> Any,
        contentType: (index: Int) -> Any?,
        itemContent: LazyIndexedContent,
    ) {
        require(count >= 0) { "Lazy list item count must be non-negative." }
        if (count == 0) return
        addInterval(count, key, contentType, itemContent)
    }

    fun build(): LazyItemProvider = IntervalLazyItemProvider(intervals.toList(), itemCount)

    private fun addInterval(
        count: Int,
        key: (index: Int) -> Any,
        contentType: (index: Int) -> Any?,
        itemContent: LazyIndexedContent,
    ) {
        require(count <= Int.MAX_VALUE - itemCount) {
            "Lazy list item count exceeds ${Int.MAX_VALUE}."
        }
        intervals +=
            LazyItemInterval(
                startIndex = itemCount,
                count = count,
                key = key,
                contentType = contentType,
                itemContent = itemContent,
            )
        itemCount += count
    }
}

private class LazyItemInterval(
    val startIndex: Int,
    val count: Int,
    val key: (Int) -> Any,
    val contentType: (Int) -> Any?,
    val itemContent: LazyIndexedContent,
) {
    val endIndex: Int
        get() = startIndex + count
}

private class IntervalLazyItemProvider(
    private val intervals: List<LazyItemInterval>,
    override val itemCount: Int,
) : LazyItemProvider {
    override fun key(index: Int): Any {
        val interval = intervalAt(index)
        return interval.key(index - interval.startIndex)
    }

    override fun contentType(index: Int): Any? {
        val interval = intervalAt(index)
        return interval.contentType(index - interval.startIndex)
    }

    @Composable
    @FlareUiComposable
    override fun Item(index: Int) {
        val interval = intervalAt(index)
        interval.itemContent(index - interval.startIndex)
    }

    private fun intervalAt(index: Int): LazyItemInterval {
        require(index in 0 until itemCount) {
            "Lazy list index $index is outside 0 until $itemCount."
        }
        val intervalIndex =
            intervals.binarySearch { interval ->
                when {
                    interval.endIndex <= index -> -1
                    interval.startIndex > index -> 1
                    else -> 0
                }
            }
        check(intervalIndex >= 0) {
            "Unable to resolve lazy list index $index."
        }
        return intervals[intervalIndex]
    }
}
