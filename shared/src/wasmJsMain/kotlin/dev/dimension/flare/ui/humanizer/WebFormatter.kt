package dev.dimension.flare.ui.humanizer

import org.koin.core.annotation.Single
import kotlin.time.Instant

@Single(binds = [PlatformFormatter::class])
internal class WebFormatter : PlatformFormatter {
    override fun formatNumber(number: Long): String = number.toString()

    override fun formatRelativeInstant(instant: Instant): String = instant.toString()

    override fun formatFullInstant(instant: Instant): String = instant.toString()

    override fun formatAbsoluteInstant(instant: Instant): String = instant.toString()
}
