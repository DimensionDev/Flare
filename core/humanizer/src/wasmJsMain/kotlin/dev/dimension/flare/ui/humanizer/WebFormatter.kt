package dev.dimension.flare.ui.humanizer

import kotlin.time.Instant

public data object WebFormatter : PlatformFormatter {
    override fun formatNumber(number: Long): String = number.toString()

    override fun formatRelativeInstant(instant: Instant): String = instant.toString()

    override fun formatFullInstant(instant: Instant): String = instant.toString()

    override fun formatAbsoluteInstant(instant: Instant): String = instant.toString()
}
