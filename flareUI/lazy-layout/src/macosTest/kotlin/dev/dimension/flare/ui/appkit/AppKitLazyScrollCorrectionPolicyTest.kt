package dev.dimension.flare.ui.appkit

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

public class AppKitLazyScrollCorrectionPolicyTest {
    @Test
    public fun liveScrollDefersAnchorCorrections() {
        assertFalse(shouldDeferAppKitLazyOffsetCorrection(isLiveScrolling = false))
        assertTrue(shouldDeferAppKitLazyOffsetCorrection(isLiveScrolling = true))
    }
}
