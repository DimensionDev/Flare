@file:OptIn(dev.dimension.flare.ui.LowLevelFlareApi::class)

package dev.dimension.flare.ui.lazy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

public class LazyListChangeSetTest {
    @Test
    public fun resolvesManyIntervalsAndRejectsTotalCountOverflow() {
        val scope = IntervalLazyListScope()
        repeat(1_000) { interval ->
            scope.item(key = "item-$interval") {}
        }

        val provider = scope.build()
        assertEquals("item-0", provider.key(0))
        assertEquals("item-511", provider.key(511))
        assertEquals("item-999", provider.key(999))

        assertFailsWith<IllegalArgumentException> {
            IntervalLazyListScope().apply {
                items(count = Int.MAX_VALUE, key = { it }) {}
                item(key = "overflow") {}
            }
        }
    }

    @Test
    public fun reportsInsertRemoveMoveAndContentTypeChangeInNativeIndexSpaces() {
        val previous = provider("a" to "row", "b" to "row", "c" to "row")
        val current = provider("b" to "row", "a" to "featured", "d" to "row")

        val changes = calculateLazyListChangeSet(previous, current)

        assertEquals(listOf(2), changes.removedIndices)
        assertEquals(listOf(2), changes.insertedIndices)
        assertEquals(
            listOf(LazyListMove(fromIndex = 1, toIndex = 0)),
            changes.moves,
        )
        assertEquals(listOf(1), changes.reloadedIndices)
    }

    @Test
    public fun duplicateKeysInAnUpdatedSnapshotFailDeterministically() {
        val previous = provider("a" to null)
        val current = provider("duplicate" to null, "duplicate" to null)

        assertFailsWith<IllegalStateException> {
            calculateLazyListChangeSet(previous, current)
        }
    }

    @Test
    public fun prependDiffComparesKeysInLinearTime() {
        val comparisons = ComparisonCounter()
        val previousEntries = (0 until 2_000).map { CountingKey(it, comparisons) to null }
        val currentEntries =
            listOf(CountingKey(-1, comparisons) to null) +
                (0 until 2_000).map { CountingKey(it, comparisons) to null }

        val changes = calculateLazyListChangeSet(provider(previousEntries), provider(currentEntries))

        assertEquals(listOf(0), changes.insertedIndices)
        assertEquals(emptyList(), changes.moves)
        assertTrue(comparisons.value < 50_000, "Prepend diff used ${comparisons.value} equality checks.")
    }

    private fun provider(vararg entries: Pair<String, String?>): LazyItemProvider = provider(entries.toList())

    private fun provider(entries: List<Pair<Any, String?>>): LazyItemProvider =
        IntervalLazyListScope()
            .apply {
                items(
                    count = entries.size,
                    key = { index -> entries[index].first },
                    contentType = { index -> entries[index].second },
                ) {}
            }.build()

    private class ComparisonCounter {
        var value: Int = 0
    }

    private class CountingKey(
        private val value: Int,
        private val comparisons: ComparisonCounter,
    ) {
        override fun equals(other: Any?): Boolean {
            comparisons.value += 1
            return other is CountingKey && value == other.value
        }

        override fun hashCode(): Int = value
    }
}
