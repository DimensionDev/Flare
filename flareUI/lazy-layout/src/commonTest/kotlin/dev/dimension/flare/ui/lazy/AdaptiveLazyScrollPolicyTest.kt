package dev.dimension.flare.ui.lazy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdaptiveLazyScrollPolicyTest {
    @Test
    fun deferredCorrectionPreservesThePhysicalViewportDeltaOnBothApplePlatforms() {
        assertEquals(146.0, restoredLazyViewportOffset(130.0, 100.0, 116.0, true))
        assertEquals(130.0, restoredLazyViewportOffset(130.0, 100.0, 116.0, false))
    }

    @Test
    fun settlingStopsInsideTheHalfPointTolerance() {
        assertFalse(needsAdaptiveLazyScrollCorrection(current = 100.0, target = 100.5))
        assertTrue(needsAdaptiveLazyScrollCorrection(current = 100.0, target = 100.51))
    }
}
