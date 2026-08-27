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

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@file:Suppress("DEPRECATION")

package dev.dimension.flare.ui

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.value
import platform.CoreGraphics.CGMainDisplayID
import platform.CoreVideo.CVDisplayLinkCreateWithActiveCGDisplays
import platform.CoreVideo.CVDisplayLinkCreateWithCGDisplay
import platform.CoreVideo.CVDisplayLinkRef
import platform.CoreVideo.CVDisplayLinkRefVar
import platform.CoreVideo.CVDisplayLinkSetOutputCallback
import platform.CoreVideo.CVDisplayLinkStart
import platform.CoreVideo.CVDisplayLinkStop
import platform.CoreVideo.CVOptionFlags
import platform.CoreVideo.CVOptionFlagsVar
import platform.CoreVideo.CVTimeStamp
import platform.CoreVideo.kCVReturnSuccess
import platform.Foundation.NSThread
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

internal actual fun createAppleFrameClock(): AppleFrameClock = MacOSDisplayLinkFrameClock

/**
 * Process-scoped, on-demand Core Video display-link clock for AppKit.
 *
 * Adapted from Cash App Molecule's macOS [MonotonicFrameClock]. The native callback and stable
 * reference intentionally live for the process lifetime; the link itself sleeps without awaiters.
 */
private object MacOSDisplayLinkFrameClock : AppleFrameClock {
    private val displayLink: CVDisplayLinkRef = createDisplayLink()
    private val broadcastFrameClock =
        BroadcastFrameClock {
            checkDisplayLink(CVDisplayLinkStart(displayLink))
        }
    private val clockReference: StableRef<BroadcastFrameClock> =
        StableRef.create(broadcastFrameClock)

    init {
        check(NSThread.isMainThread) {
            "The macOS frame clock must be created on the main thread."
        }
        checkDisplayLink(
            CVDisplayLinkSetOutputCallback(
                displayLink = displayLink,
                callback = staticCFunction(::displayLinkCallback),
                userInfo = clockReference.asCPointer(),
            ),
        )
    }

    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R = broadcastFrameClock.withFrameNanos(onFrame)
}

private fun createDisplayLink(): CVDisplayLinkRef =
    memScoped {
        val displayLink = alloc<CVDisplayLinkRefVar>()
        val activeDisplaysResult = CVDisplayLinkCreateWithActiveCGDisplays(displayLink.ptr)
        if (activeDisplaysResult != kCVReturnSuccess) {
            checkDisplayLink(CVDisplayLinkCreateWithCGDisplay(CGMainDisplayID(), displayLink.ptr))
        }
        requireNotNull(displayLink.value) {
            "CVDisplayLinkCreateWithActiveCGDisplays returned no display link."
        }
    }

private fun checkDisplayLink(result: Int) {
    check(result == kCVReturnSuccess) {
        "Could not operate the macOS CVDisplayLink. Error code $result."
    }
}

@Suppress("UNUSED_PARAMETER")
private fun displayLinkCallback(
    displayLink: CVDisplayLinkRef?,
    currentTime: CPointer<CVTimeStamp>?,
    outputTime: CPointer<CVTimeStamp>?,
    inputFlags: CVOptionFlags,
    outputFlags: CPointer<CVOptionFlagsVar>?,
    userInfo: COpaquePointer?,
): Int {
    val clock =
        userInfo
            ?.asStableRef<BroadcastFrameClock>()
            ?.get()
    val frameTimeNanos = monotonicFrameTimeNanos()

    // Sleep after one delivered frame. BroadcastFrameClock restarts the link for new awaiters.
    val stopResult = CVDisplayLinkStop(displayLink)
    if (clock != null) {
        dispatch_async(dispatch_get_main_queue()) {
            clock.sendFrame(frameTimeNanos)
        }
    }
    return stopResult
}
