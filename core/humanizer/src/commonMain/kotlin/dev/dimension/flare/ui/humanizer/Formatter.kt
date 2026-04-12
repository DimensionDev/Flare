package dev.dimension.flare.ui.humanizer

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Instant

public object Formatter : KoinComponent {
    private val platformFormatter: PlatformFormatter by inject()

    public fun Long.humanize(): String = platformFormatter.formatNumber(number = this)

    public fun Instant.relative(): String = platformFormatter.formatRelativeInstant(this)

    public fun Instant.full(): String = platformFormatter.formatFullInstant(this)

    public fun Instant.absolute(): String = platformFormatter.formatAbsoluteInstant(this)
}
