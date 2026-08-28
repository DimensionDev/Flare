package dev.dimension.flare.ui.lazy

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdaptiveLazyScrollPolicyTest {
    @Test
    fun settlingStopsInsideTheHalfPointTolerance() {
        assertFalse(needsAdaptiveLazyScrollCorrection(current = 100.0, target = 100.5))
        assertTrue(needsAdaptiveLazyScrollCorrection(current = 100.0, target = 100.51))
    }
}
