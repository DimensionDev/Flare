package dev.dimension.flare.ui.render

import kotlinx.datetime.toNSDate
import platform.Foundation.NSDate
import kotlin.time.Instant

public actual typealias UiDateTime = NSDate

internal actual fun Instant.toUi(): UiDateTime = toNSDate()
