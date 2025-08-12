package dev.dimension.flare.ui.render

import kotlinx.datetime.toKotlinInstant
import kotlinx.datetime.toNSDate
import platform.Foundation.NSDate
import kotlin.time.Instant

public actual typealias UiDateTime = NSDate

internal actual fun Instant.toUi(): UiDateTime = toNSDate()

internal actual operator fun UiDateTime.compareTo(other: UiDateTime): Int {
    val instant = this.toKotlinInstant()
    val otherInstant = other.toKotlinInstant()
    return instant.compareTo(otherInstant)
}
