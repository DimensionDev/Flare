@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.flare.ui.lazy

import androidx.compose.runtime.snapshots.Snapshot
import platform.CoreFoundation.CFRunLoopRunInMode
import platform.CoreFoundation.kCFRunLoopDefaultMode
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

internal fun awaitAppleUi(
    message: String,
    condition: () -> Boolean,
) {
    val startedAt = TimeSource.Monotonic.markNow()
    while (!condition() && startedAt.elapsedNow() < 5.seconds) {
        Snapshot.sendApplyNotifications()
        CFRunLoopRunInMode(kCFRunLoopDefaultMode, 0.01, true)
    }
    check(condition()) { message }
}
