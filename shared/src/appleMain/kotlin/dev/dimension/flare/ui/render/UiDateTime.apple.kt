package dev.dimension.flare.ui.render

import kotlin.time.Instant
import kotlin.time.toNSDate
import platform.Foundation.NSDate

public actual typealias UiDateTime = NSDate

internal actual fun Instant.toUi(): UiDateTime = toNSDate()
