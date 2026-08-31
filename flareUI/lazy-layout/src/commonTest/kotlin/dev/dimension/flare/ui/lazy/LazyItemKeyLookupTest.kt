@file:OptIn(dev.dimension.flare.ui.LowLevelFlareApi::class)

package dev.dimension.flare.ui.lazy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LazyItemKeyLookupTest {
    @Test
    fun countDeltaFindsALargePrependWithoutScanningTheProvider() {
        var keyLookups = 0
        val provider =
            provider(count = 10_100) { index ->
                keyLookups += 1
                index - 100
            }

        val index =
            provider.findIndexByKey(
                key = 9_000,
                expectedIndex = 9_000,
                previousItemCount = 10_000,
            )

        assertEquals(9_100, index)
        assertTrue(keyLookups <= 2, "Large prepend resolved $keyLookups keys.")
    }

    @Test
    fun arbitraryFarReorderKeepsTheStableKeyThroughTheCorrectnessFallback() {
        val keys = (1 until 200).toList() + 0
        val provider = provider(keys.size, keys::get)

        assertEquals(
            199,
            provider.findIndexByKey(
                key = 0,
                expectedIndex = 0,
                previousItemCount = 200,
            ),
        )
    }

    @Test
    fun localSearchDoesNotOverflowNearTheMaximumItemCount() {
        val expectedIndex = Int.MAX_VALUE - 2
        val targetIndex = expectedIndex - 4
        val anchor = "anchor"
        val provider =
            provider(Int.MAX_VALUE) { index ->
                if (index == targetIndex) anchor else index
            }

        assertEquals(
            targetIndex,
            provider.findIndexByKey(
                key = anchor,
                expectedIndex = expectedIndex,
                previousItemCount = Int.MAX_VALUE,
            ),
        )
    }

    private fun provider(
        count: Int,
        key: (Int) -> Any,
    ): LazyItemProvider =
        IntervalLazyListScope()
            .apply { items(count = count, key = key) {} }
            .build()
}
