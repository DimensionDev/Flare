/*
 * Copyright (C) 2023 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package dev.dimension.flare.ui

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.cinterop.ObjCAction
import platform.Foundation.NSRunLoop
import platform.Foundation.NSRunLoopCommonModes
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSThread
import platform.QuartzCore.CADisplayLink
import platform.darwin.NSObject

internal actual fun createAppleFrameClock(): AppleFrameClock = IOSDisplayLinkFrameClock

/** On-demand CADisplayLink clock adapted from Cash App Molecule's iOS clock. */
private object IOSDisplayLinkFrameClock : AppleFrameClock {
    private val target = DisplayLinkTarget(this)
    private val displayLink =
        CADisplayLink.displayLinkWithTarget(
            target = target,
            selector = NSSelectorFromString(DisplayLinkTarget::tickClock.name),
        )
    private val broadcastFrameClock =
        BroadcastFrameClock {
            displayLink.addToRunLoop(NSRunLoop.mainRunLoop, NSRunLoopCommonModes)
        }

    init {
        check(NSThread.isMainThread) {
            "The iOS frame clock must be created on the main thread."
        }
    }

    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R = broadcastFrameClock.withFrameNanos(onFrame)

    private fun tickClock() {
        // Remove the completed request before resuming frame awaiters. Resumed work can request
        // another frame synchronously; removing afterwards would detach that newly scheduled tick.
        displayLink.removeFromRunLoop(NSRunLoop.mainRunLoop, NSRunLoopCommonModes)
        broadcastFrameClock.sendFrame(monotonicFrameTimeNanos())
    }

    /** Objective-C selector bridge for the Kotlin [MonotonicFrameClock] object. */
    private class DisplayLinkTarget(
        private val frameClock: IOSDisplayLinkFrameClock,
    ) : NSObject() {
        @ObjCAction
        fun tickClock() {
            frameClock.tickClock()
        }
    }
}
