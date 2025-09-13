package dev.dimension.flare.ui.render

import kotlinx.datetime.toNSDate
import platform.Foundation.NSDate
import kotlin.time.Instant

internal actual fun Instant.toPlatform(): PlatformDateTime = toNSDate()

internal actual typealias PlatformDateTime = NSDate
