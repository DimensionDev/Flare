package dev.dimension.flare.ui.render

import kotlinx.datetime.Instant
import kotlinx.datetime.toNSDate
import platform.Foundation.NSDate

actual typealias UiDateTime = NSDate

actual fun Instant.toUi(): UiDateTime = toNSDate()
