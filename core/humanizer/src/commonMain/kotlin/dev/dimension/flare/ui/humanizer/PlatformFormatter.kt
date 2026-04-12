package dev.dimension.flare.ui.humanizer

import kotlin.time.Instant

public interface PlatformFormatter {
    public fun formatNumber(number: Long): String

    public fun formatRelativeInstant(instant: Instant): String

    public fun formatFullInstant(instant: Instant): String

    public fun formatAbsoluteInstant(instant: Instant): String
}
