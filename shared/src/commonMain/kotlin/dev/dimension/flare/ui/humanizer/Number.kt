package dev.dimension.flare.ui.humanizer

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.round
import kotlin.time.Instant

internal fun Float.humanizePercentage(): String {
    val roundedNumber = round(this * 100 * 100).toDouble() / 100
    return "$roundedNumber%"
}

internal object Formatter : KoinComponent {
    val platformFormatter: PlatformFormatter by inject()

    internal fun Long.humanize(): String = platformFormatter.formatNumber(number = this)

    internal fun Instant.relative(): String = platformFormatter.formatRelativeInstant(this)

    internal fun Instant.full(): String = platformFormatter.formatFullInstant(this)

    internal fun Instant.absolute(): String = platformFormatter.formatAbsoluteInstant(this)
}

internal interface PlatformFormatter {
    fun formatNumber(number: Long): String

    fun formatRelativeInstant(instant: Instant): String

    fun formatFullInstant(instant: Instant): String

    fun formatAbsoluteInstant(instant: Instant): String
}
