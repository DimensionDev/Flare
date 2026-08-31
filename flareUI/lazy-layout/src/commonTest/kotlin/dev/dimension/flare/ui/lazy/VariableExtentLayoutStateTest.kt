package dev.dimension.flare.ui.lazy

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

public class VariableExtentLayoutStateTest {
    @Test
    public fun measuredExtentOnlyMovesFollowingItems() {
        val state = VariableExtentLayoutState(defaultEstimatedExtent = 48.0)
        state.reset(itemCount = 4, spacing = 6.0, environment = "portrait")

        assertEquals(0.0, state.itemStart(0))
        assertEquals(54.0, state.itemStart(1))
        assertEquals(108.0, state.itemStart(2))
        assertEquals(210.0, state.contentExtent)

        val change =
            state.record(
                index = 1,
                key = "second",
                layoutVersion = Unit,
                contentType = "text",
                extent = 80.0,
            )

        assertEquals(32.0, change?.delta)
        assertEquals(0.0, state.itemStart(0))
        assertEquals(54.0, state.itemStart(1))
        assertEquals(140.0, state.itemStart(2))
        assertEquals(242.0, state.contentExtent)
        assertEquals(1..1, state.visibleRange(viewportStart = 55.0, viewportEnd = 139.0))
    }

    @Test
    public fun exactMeasurementsFollowStableKeysAcrossResetAndReorder() {
        val state = VariableExtentLayoutState(defaultEstimatedExtent = 48.0)
        state.reset(itemCount = 3, spacing = 0.0, environment = "portrait")
        state.record(
            index = 1,
            key = "stable-b",
            layoutVersion = Unit,
            contentType = "text",
            extent = 92.0,
        )

        state.reset(itemCount = 4, spacing = 0.0, environment = "portrait")
        val restored =
            state.resolve(
                index = 3,
                key = "stable-b",
                layoutVersion = Unit,
                contentType = "text",
            )

        assertEquals(44.0, restored?.delta)
        assertEquals(92.0, state.itemExtent(3))
    }

    @Test
    public fun contentTypeMedianPredictsUnseenItemsWithoutBecomingFixedSizing() {
        val state = VariableExtentLayoutState(defaultEstimatedExtent = 48.0)
        state.reset(itemCount = 4, spacing = 0.0, environment = "portrait")
        state.record(0, "image-1", Unit, "image", 120.0)
        state.record(1, "image-2", Unit, "image", 180.0)
        state.record(2, "image-3", Unit, "image", 160.0)
        repeat(20) {
            state.record(0, "image-1", Unit, "image", 120.0)
        }

        state.reset(itemCount = 5, spacing = 0.0, environment = "portrait")
        state.resolve(4, "image-4", Unit, "image")

        assertEquals(160.0, state.itemExtent(4))
        state.record(4, "image-4", Unit, "image", 210.0)
        assertEquals(210.0, state.itemExtent(4))
    }

    @Test
    public fun layoutVersionAndEnvironmentPreventStaleExactMeasurements() {
        val state = VariableExtentLayoutState(defaultEstimatedExtent = 48.0)
        state.reset(itemCount = 1, spacing = 0.0, environment = "width-320")
        state.record(0, "post", layoutVersion = 1, contentType = "expanded", extent = 140.0)

        state.reset(itemCount = 1, spacing = 0.0, environment = "width-320")
        state.resolve(0, "post", layoutVersion = 2, contentType = "collapsed")
        assertEquals(48.0, state.itemExtent(0))

        state.record(0, "post", layoutVersion = 2, contentType = "collapsed", extent = 72.0)
        state.reset(itemCount = 1, spacing = 0.0, environment = "width-480")
        state.resolve(0, "post", layoutVersion = 2, contentType = "collapsed")
        assertEquals(48.0, state.itemExtent(0))
    }

    @Test
    public fun repeatedSubToleranceChangesAreComparedWithAppliedGeometry() {
        val state =
            VariableExtentLayoutState(
                defaultEstimatedExtent = 48.0,
                measurementTolerance = 0.5,
            )
        state.reset(itemCount = 2, spacing = 0.0, environment = "portrait")

        state.record(0, "dynamic", Unit, "post", 48.4)
        assertEquals(48.0, state.itemExtent(0))
        assertEquals(48.0, state.itemStart(1))

        state.record(0, "dynamic", Unit, "post", 48.8)
        assertEquals(48.8, state.itemExtent(0))
        assertEquals(48.8, state.itemStart(1))
    }

    @Test
    public fun visibleRangeMatchesLinearOracleForSparseHeterogeneousExtents() {
        val random = Random(0xF1A2E)

        repeat(40) { scenario ->
            val itemCount = random.nextInt(from = 1, until = 200)
            val spacing = random.nextDouble(from = 0.0, until = 24.0)
            val extents = MutableList(itemCount) { 48.0 }
            val measuredIndices =
                extents.indices
                    .shuffled(random)
                    .take(max(1, itemCount / 4))
            measuredIndices.forEach { index ->
                extents[index] = random.nextDouble(from = 1.0, until = 240.0)
            }
            val starts = extents.itemStarts(spacing)
            val state =
                VariableExtentLayoutState(
                    defaultEstimatedExtent = 48.0,
                    measurementTolerance = 0.0,
                )
            state.reset(itemCount, spacing, environment = "scenario-$scenario")
            measuredIndices.shuffled(random).forEach { index ->
                state.record(
                    index = index,
                    key = "item-$index",
                    layoutVersion = Unit,
                    contentType = index % 5,
                    extent = extents[index],
                )
            }

            repeat(50) {
                val viewportStart =
                    random.nextDouble(from = -100.0, until = state.contentExtent + 100.0)
                val viewportEnd =
                    random.nextDouble(from = -100.0, until = state.contentExtent + 100.0)
                val overscan = random.nextDouble(from = 0.0, until = 100.0)
                val queryStart = max(0.0, min(viewportStart, viewportEnd) - overscan)
                val queryEnd = max(viewportStart, viewportEnd) + overscan

                assertEquals(
                    starts.indexAtOrBefore(queryStart)..starts.indexAtOrBefore(queryEnd),
                    state.visibleRange(viewportStart, viewportEnd, overscan),
                    "scenario=$scenario, viewport=$viewportStart..$viewportEnd, overscan=$overscan",
                )
            }
        }
    }

    @Test
    public fun visibleRangePreservesSpacingBoundariesAndEmptyState() {
        val empty = VariableExtentLayoutState(defaultEstimatedExtent = 48.0)
        empty.reset(itemCount = 0, spacing = 7.0, environment = Unit)
        assertEquals(IntRange.EMPTY, empty.visibleRange(0.0, 1_000.0))

        val state =
            VariableExtentLayoutState(
                defaultEstimatedExtent = 48.0,
                measurementTolerance = 0.0,
            )
        state.reset(itemCount = 4, spacing = 7.0, environment = Unit)
        listOf(10.0, 20.0, 5.0, 100.0).forEachIndexed { index, extent ->
            state.record(index, "item-$index", Unit, null, extent)
        }

        assertEquals(0..0, state.visibleRange(10.0, 16.999))
        assertEquals(0..1, state.visibleRange(10.0, 17.0))
        assertEquals(1..2, state.visibleRange(50.0, 43.999))
        assertEquals(0..3, state.visibleRange(56.0, 56.0, overscan = 1_000.0))
    }

    @Test
    public fun sparseGeometrySupportsMaximumItemCountWithoutIndexOverflow() {
        val itemCount = Int.MAX_VALUE
        val lastIndex = itemCount - 1
        val state =
            VariableExtentLayoutState(
                defaultEstimatedExtent = 48.0,
                measurementTolerance = 0.0,
            )
        state.reset(itemCount, spacing = 1.0, environment = Unit)
        state.record(0, "first", Unit, null, 96.0)
        state.record(lastIndex, "last", Unit, null, 72.0)

        val lastItemStart = lastIndex * 49.0 + 48.0
        assertEquals(lastIndex..lastIndex, state.visibleRange(lastItemStart, Double.MAX_VALUE))
        assertEquals(lastItemStart, state.itemStart(lastIndex))
    }

    @Test
    public fun visibleRangeUsesOneFenwickDescentPerEndpoint() {
        val itemCount = 1_000_000
        val state =
            VariableExtentLayoutState(
                defaultEstimatedExtent = 48.0,
                measurementTolerance = 0.0,
            )
        state.reset(itemCount, spacing = 3.0, environment = Unit)
        listOf(0, 7, 1_024, 65_535, 500_000, itemCount - 1).forEach { index ->
            state.record(index, "item-$index", Unit, null, 24.0 + index % 97)
        }

        val (range, nodeVisits) =
            state.visibleRangeWithSearchNodeVisitsForTesting(
                viewportStart = state.contentExtent * 0.45,
                viewportEnd = state.contentExtent * 0.55,
                overscan = 500.0,
            )

        assertEquals(
            state.visibleRange(state.contentExtent * 0.45, state.contentExtent * 0.55, 500.0),
            range,
        )
        assertTrue(nodeVisits <= 40, "Expected O(log N) node visits, but observed $nodeVisits")
    }
}

private fun List<Double>.itemStarts(spacing: Double): List<Double> {
    var start = 0.0
    return map { extent ->
        start.also { start += extent + spacing }
    }
}

private fun List<Double>.indexAtOrBefore(offset: Double): Int {
    var result = 0
    forEachIndexed { index, start ->
        if (start <= offset) {
            result = index
        } else {
            return result
        }
    }
    return result
}
