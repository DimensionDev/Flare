package dev.dimension.flare.ui.uikit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

public class UIKitLazyScrollCorrectionPolicyTest {
    @Test
    public fun physicalScrollDefersContentOffsetCorrections() {
        assertFalse(
            shouldDeferUIKitLazyOffsetCorrection(
                isTracking = false,
                isDragging = false,
                isDecelerating = false,
            ),
        )
        assertTrue(
            shouldDeferUIKitLazyOffsetCorrection(
                isTracking = true,
                isDragging = false,
                isDecelerating = false,
            ),
        )
        assertTrue(
            shouldDeferUIKitLazyOffsetCorrection(
                isTracking = false,
                isDragging = true,
                isDecelerating = false,
            ),
        )
        assertTrue(
            shouldDeferUIKitLazyOffsetCorrection(
                isTracking = false,
                isDragging = false,
                isDecelerating = true,
            ),
        )
    }

    @Test
    public fun deferredCorrectionPreservesThePhysicalViewportDelta() {
        assertEquals(
            expected = 146.0,
            actual =
                restoredUIKitLazyViewportOffset(
                    anchorTargetAtCapture = 130.0,
                    capturedViewportOffset = 100.0,
                    currentViewportOffset = 116.0,
                    preserveViewportDelta = true,
                ),
        )
        assertEquals(
            expected = 130.0,
            actual =
                restoredUIKitLazyViewportOffset(
                    anchorTargetAtCapture = 130.0,
                    capturedViewportOffset = 100.0,
                    currentViewportOffset = 116.0,
                    preserveViewportDelta = false,
                ),
        )
    }
}
