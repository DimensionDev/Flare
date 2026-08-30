@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.flare.ui

import platform.CoreFoundation.CFRunLoopRunInMode
import platform.CoreFoundation.kCFRunLoopDefaultMode
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

internal fun awaitAppleUi(
    message: String,
    condition: () -> Boolean,
) {
    val startedAt = TimeSource.Monotonic.markNow()
    while (!condition() && startedAt.elapsedNow() < APPLE_UI_TIMEOUT) {
        CFRunLoopRunInMode(kCFRunLoopDefaultMode, RUN_LOOP_STEP_SECONDS, true)
    }
    check(condition()) { message }
}

private val APPLE_UI_TIMEOUT = 5.seconds
private const val RUN_LOOP_STEP_SECONDS: Double = 0.01
