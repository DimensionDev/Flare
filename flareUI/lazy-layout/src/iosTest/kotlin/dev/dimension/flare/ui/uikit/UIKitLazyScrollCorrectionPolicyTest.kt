package dev.dimension.flare.ui.uikit

import kotlin.test.Test
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
}
