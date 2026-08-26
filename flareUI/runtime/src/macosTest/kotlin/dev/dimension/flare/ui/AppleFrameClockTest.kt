package dev.dimension.flare.ui

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

public class AppleFrameClockTest {
    @Test
    public fun displayLinkProducesMonotonicFrames(): Unit =
        runBlocking {
            val frameClock = createAppleFrameClock()
            val firstFrame =
                withTimeout(5.seconds) {
                    frameClock.withFrameNanos { frameTimeNanos -> frameTimeNanos }
                }
            val secondFrame =
                withTimeout(5.seconds) {
                    frameClock.withFrameNanos { frameTimeNanos -> frameTimeNanos }
                }

            assertTrue(firstFrame > 0L)
            assertTrue(secondFrame > firstFrame)
        }
}
