@file:OptIn(dev.dimension.compose.nativekit.LowLevelNativeKitApi::class)

package dev.dimension.compose.nativekit.lazy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LazyGeometryUpdateComplexityTest {
    @Test
    fun oneSizeUpdateAfterLongTraversalHasLocalLookupWork() {
        val geometry = VariableExtentLayoutState()
        val count = 100_000
        geometry.reset(count, 0.0, Unit)
        repeat(50_000) { index -> geometry.record(index, index, false, Unit, 60.0) }
        var lookups = 0
        val provider =
            IntervalLazyListScope()
                .apply {
                    items(
                        count = count,
                        key = {
                            lookups++
                            it
                        },
                        contentType = {
                            lookups++
                            Unit
                        },
                        layoutVersion = {
                            lookups++
                            it == 49_999
                        },
                    ) { }
                }.build()
        geometry.update(provider, 0.0, Unit)
        val previous = geometry.itemExtent(49_999)
        geometry.record(49_999, 49_999, true, Unit, 72.0)
        assertEquals(60.0, previous)
        assertEquals(50_000 * 60.0 + 12.0, geometry.itemStart(50_000))
        assertTrue(lookups < 1_000, "One changed visible size caused $lookups provider lookups after visiting 50,000 items.")
    }

    @Test
    fun prependAndRemovalPreserveLongMeasuredPrefixesWithoutScanningHistory() {
        val geometry = VariableExtentLayoutState()
        geometry.reset(100_000, 0.0, Unit)
        repeat(50_000) { index -> geometry.record(index, index, Unit, null, 60.0) }
        val previousStart = geometry.itemStart(40_000)
        val previousTotal = geometry.contentExtent
        var lookups = 0
        val prepended =
            IntervalLazyListScope()
                .apply {
                    items(100_001, key = {
                        lookups++
                        it - 1
                    }) {}
                }.build()
        geometry.update(prepended, 0.0, Unit, LazyIndexSplice(0, 0, 1))
        // These keys are older than the exact-size LRU, so their sparse geometry must survive.
        assertEquals(60.0, geometry.itemExtent(1))
        assertEquals(previousStart + 48.0, geometry.itemStart(40_001))
        assertEquals(previousTotal + 48.0, geometry.contentExtent)

        val removed =
            IntervalLazyListScope()
                .apply {
                    items(99_001, key = {
                        lookups++
                        if (it == 0) -1 else it + 999
                    }) {}
                }.build()
        geometry.update(removed, 0.0, Unit, LazyIndexSplice(1, 1_000, 0))
        assertEquals(previousStart + 48.0 - 60_000.0, geometry.itemStart(39_001))
        assertEquals(previousTotal + 48.0 - 60_000.0, geometry.contentExtent)
        assertTrue(lookups < 1_000, "Positional edits resolved $lookups keys from browsing history.")
    }
}
