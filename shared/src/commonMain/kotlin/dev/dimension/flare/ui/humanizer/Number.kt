package dev.dimension.flare.ui.humanizer

import dev.dimension.flare.di.koinInject
import kotlin.math.round
import kotlin.native.HiddenFromObjC
import kotlin.time.Instant

internal fun Float.humanizePercentage(): String {
    val roundedNumber = round(this * 100 * 100).toDouble() / 100
    return "$roundedNumber%"
}

internal object Formatter  {
    val platformFormatter: PlatformFormatter by koinInject()

    internal fun Long.humanize(): String = platformFormatter.formatNumber(number = this)

    internal fun Instant.relative(): String = platformFormatter.formatRelativeInstant(this)

    internal fun Instant.full(): String = platformFormatter.formatFullInstant(this)

    internal fun Instant.absolute(): String = platformFormatter.formatAbsoluteInstant(this)
}

@HiddenFromObjC
public interface PlatformFormatter {
    public fun formatNumber(number: Long): String

    public fun formatRelativeInstant(instant: Instant): String

    public fun formatFullInstant(instant: Instant): String

    public fun formatAbsoluteInstant(instant: Instant): String
}
