package dev.dimension.flare.ui.render

import kotlinx.datetime.toNSDate
import platform.Foundation.NSDate
import kotlin.time.Instant

public actual fun Instant.toPlatform(): PlatformDateTime = toNSDate()

public actual typealias PlatformDateTime = NSDate
