package dev.dimension.flare.ui.render

import kotlinx.datetime.Instant
import kotlinx.datetime.toNSDate
import platform.Foundation.NSDate

public actual typealias UiDateTime = NSDate

internal actual fun Instant.toUi(): UiDateTime = toNSDate()
