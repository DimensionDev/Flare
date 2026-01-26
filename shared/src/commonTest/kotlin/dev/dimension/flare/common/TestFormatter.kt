package dev.dimension.flare.common

import dev.dimension.flare.ui.humanizer.PlatformFormatter
import kotlin.time.Instant

class TestFormatter : PlatformFormatter {
    override fun formatNumber(number: Long): String = number.toString()

    override fun formatRelativeInstant(instant: Instant): String = instant.toString()

    override fun formatFullInstant(instant: Instant): String = instant.toString()

    override fun formatAbsoluteInstant(instant: Instant): String = instant.toString()
}
