@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package dev.dimension.flare.ui

import androidx.compose.runtime.BroadcastFrameClock
import kotlinx.cinterop.ObjCAction
import platform.Foundation.NSRunLoop
import platform.Foundation.NSRunLoopCommonModes
import platform.Foundation.NSThread
import platform.QuartzCore.CADisplayLink
import platform.darwin.NSObject
import platform.darwin.sel_registerName

internal actual fun createAppleFrameClock(): AppleFrameClock = IOSDisplayLinkFrameClock()

/**
 * VSync-backed iOS frame clock which wakes only while Compose has frame awaiters.
 */
private class IOSDisplayLinkFrameClock : AppleFrameClock {
    private var displayLink: CADisplayLink? = null
    private val broadcastFrameClock =
        BroadcastFrameClock {
            displayLink?.paused = false
        }
    private val target =
        object : NSObject() {
            @Suppress("unused")
            @ObjCAction
            fun displayLinkDidFire(link: CADisplayLink) {
                link.paused = true
                broadcastFrameClock.sendFrame(
                    (link.targetTimestamp * NANOS_PER_SECOND).toLong(),
                )
            }
        }

    init {
        check(NSThread.isMainThread) {
            "The iOS frame clock must be created on the main thread."
        }
        displayLink =
            CADisplayLink
                .displayLinkWithTarget(
                    target = target,
                    selector = sel_registerName("displayLinkDidFire:"),
                ).also { link ->
                    link.paused = true
                    link.addToRunLoop(NSRunLoop.mainRunLoop, NSRunLoopCommonModes)
                }
    }

    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R = broadcastFrameClock.withFrameNanos(onFrame)

    override fun dispose() {
        displayLink?.invalidate()
        displayLink = null
        broadcastFrameClock.cancel()
    }
}

private const val NANOS_PER_SECOND: Double = 1_000_000_000.0
