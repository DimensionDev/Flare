package dev.dimension.flare.ui.appkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

public class AppKitLazyScrollCorrectionPolicyTest {
    @Test
    public fun liveScrollDefersAnchorCorrections() {
        assertFalse(shouldDeferAppKitLazyOffsetCorrection(isLiveScrolling = false))
        assertTrue(shouldDeferAppKitLazyOffsetCorrection(isLiveScrolling = true))
    }

    @Test
    public fun deferredCorrectionPreservesThePhysicalViewportDelta() {
        assertEquals(
            expected = 146.0,
            actual =
                restoredAppKitLazyViewportOffset(
                    anchorTargetAtCapture = 130.0,
                    capturedViewportOffset = 100.0,
                    currentViewportOffset = 116.0,
                    preserveViewportDelta = true,
                ),
        )
        assertEquals(
            expected = 130.0,
            actual =
                restoredAppKitLazyViewportOffset(
                    anchorTargetAtCapture = 130.0,
                    capturedViewportOffset = 100.0,
                    currentViewportOffset = 116.0,
                    preserveViewportDelta = false,
                ),
        )
    }
}
