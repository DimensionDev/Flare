package dev.dimension.flare.ui.lazy

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
