package dev.dimension.flare.ui.component

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.math.abs
import kotlin.math.max

@Composable
internal fun rememberScrollbarAdapter(scrollState: LazyStaggeredGridState): ScrollbarAdapter =
    remember(scrollState) { LazyStaggeredGridScrollbarAdapter(scrollState) }

internal class LazyStaggeredGridScrollbarAdapter(
    private val scrollState: LazyStaggeredGridState,
) : ScrollbarAdapter {
    private val layoutInfo
        get() = scrollState.layoutInfo

    private val viewportSizeInt: Int
        get() =
            layoutInfo.viewportSize.run {
                if (layoutInfo.orientation == androidx.compose.foundation.gestures.Orientation.Vertical) {
                    height
                } else {
                    width
                }
            }

    private val contentPadding: Int
        get() = layoutInfo.beforeContentPadding + layoutInfo.afterContentPadding

    private val scrollbarState
        get() = checkNotNull(scrollState.scrollIndicatorState)

    override val scrollOffset: Double
        get() = scrollbarState.scrollOffset.toDouble()
    override val contentSize: Double
        get() = scrollbarState.contentSize.toDouble()
    override val viewportSize: Double
        get() = scrollbarState.viewportSize.toDouble()

    override suspend fun scrollTo(scrollOffset: Double) {
        val targetOffset = scrollOffset.coerceIn(0.0, maxScrollOffset)
        val distance = targetOffset - this.scrollOffset
        if (abs(distance) <= viewportSize) {
            scrollState.scrollBy(distance.toFloat())
            return
        }

        val totalItemsCount = layoutInfo.totalItemsCount
        if (totalItemsCount <= 0) {
            return
        }

        val averageItemExtent =
            max(
                1.0,
                (contentSize - contentPadding + layoutInfo.mainAxisItemSpacing) / totalItemsCount,
            )
        val targetIndex = (targetOffset / averageItemExtent).toInt().coerceIn(0, totalItemsCount - 1)
        val targetItemOffset =
            (targetOffset - targetIndex * averageItemExtent)
                .toInt()
                .coerceIn(0, viewportSizeInt)

        scrollState.scrollToItem(
            index = targetIndex,
            scrollOffset = targetItemOffset,
        )
    }

    private val maxScrollOffset: Double
        get() = (contentSize - viewportSize).coerceAtLeast(0.0)
}
