package dev.dimension.flare.ui.render

import dev.dimension.flare.ui.humanizer.Formatter.full
import dev.dimension.flare.ui.humanizer.Formatter.relative
import kotlin.time.Clock
import kotlin.time.Instant

public expect class PlatformDateTime

internal expect fun Instant.toPlatform(): PlatformDateTime

public data class UiDateTime internal constructor(
    val value: Instant,
) {
    val platformValue: PlatformDateTime by lazy {
        value.toPlatform()
    }
    val relative: String = value.relative()
    val full: String = value.full()

    val shouldShowFull: Boolean by lazy {
        val compareTo = Clock.System.now()
        val diff = compareTo - this.value
        diff.inWholeDays >= 7
    }
}

public fun Instant.toUi(): UiDateTime = UiDateTime(this)

internal operator fun UiDateTime.compareTo(other: UiDateTime): Int = value.compareTo(other.value)
