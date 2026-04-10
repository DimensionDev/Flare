package dev.dimension.flare.ui.humanizer

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.round
import kotlin.time.Instant

public fun Float.humanizePercentage(): String {
    val roundedNumber = round(this * 100 * 100).toDouble() / 100
    return "$roundedNumber%"
}

public object Formatter : KoinComponent {
    private val platformFormatter: PlatformFormatter by inject()

    public fun Long.humanize(): String = platformFormatter.formatNumber(number = this)

    public fun Instant.relative(): String = platformFormatter.formatRelativeInstant(this)

    public fun Instant.full(): String = platformFormatter.formatFullInstant(this)

    public fun Instant.absolute(): String = platformFormatter.formatAbsoluteInstant(this)
}

public interface PlatformFormatter {
    fun formatNumber(number: Long): String

    fun formatRelativeInstant(instant: Instant): String

    fun formatFullInstant(instant: Instant): String

    fun formatAbsoluteInstant(instant: Instant): String
}
