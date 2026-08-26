@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.flare.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import platform.CoreFoundation.CFRunLoopRunInMode
import platform.CoreFoundation.kCFRunLoopDefaultMode
import platform.Foundation.NSThread
import kotlin.test.Test
import kotlin.test.assertTrue

public class AppleFrameClockTest {
    @Test
    public fun displayLinkProducesMonotonicFrames() {
        val frameClock = createAppleFrameClock()
        val firstFrame = frameClock.awaitFrame { frameTimeNanos -> frameTimeNanos }
        val secondFrame = frameClock.awaitFrame { frameTimeNanos -> frameTimeNanos }

        assertTrue(firstFrame > 0L)
        assertTrue(secondFrame > firstFrame)
    }

    @Test
    public fun displayLinkDeliversFrameBlockOnMainThread() {
        val frameClock = createAppleFrameClock()

        frameClock.awaitFrame {
            assertTrue(NSThread.isMainThread, "Frame callbacks must run on the AppKit main thread.")
        }
    }

    private fun <R> AppleFrameClock.awaitFrame(onFrame: (Long) -> R): R {
        check(NSThread.isMainThread) { "The test must drive the AppKit run loop from the main thread." }
        val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
        var result: Result<R>? = null
        scope.launch {
            result = runCatching { withFrameNanos(onFrame) }
        }
        val deadlineNanos = monotonicFrameTimeNanos() + FRAME_TIMEOUT_NANOS
        while (result == null && monotonicFrameTimeNanos() < deadlineNanos) {
            CFRunLoopRunInMode(kCFRunLoopDefaultMode, RUN_LOOP_STEP_SECONDS, true)
        }
        scope.cancel()
        return result?.getOrThrow()
            ?: error("Timed out waiting for an AppKit display-link frame.")
    }

    private companion object {
        const val FRAME_TIMEOUT_NANOS: Long = 5_000_000_000L
        const val RUN_LOOP_STEP_SECONDS: Double = 0.01
    }
}
