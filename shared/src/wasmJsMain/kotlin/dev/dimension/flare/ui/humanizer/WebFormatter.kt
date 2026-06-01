package dev.dimension.flare.ui.humanizer

import org.koin.core.annotation.Single
import kotlin.time.Instant

public interface WebFormatterBridge {
    public fun formatNumber(number: Double): String

    public fun formatRelativeInstant(epochMillis: Double): String

    public fun formatFullInstant(epochMillis: Double): String

    public fun formatAbsoluteInstant(epochMillis: Double): String
}

public fun installWebFormatterBridge(formatter: WebFormatterBridge) {
    WebFormatterBridgeHolder.formatter = formatter
}

@Single(binds = [PlatformFormatter::class])
internal class WebFormatter : PlatformFormatter {
    override fun formatNumber(number: Long): String = WebFormatterBridgeHolder.formatter.formatNumber(number.toDouble())

    override fun formatRelativeInstant(instant: Instant): String =
        WebFormatterBridgeHolder.formatter.formatRelativeInstant(instant.toEpochMilliseconds().toDouble())

    override fun formatFullInstant(instant: Instant): String =
        WebFormatterBridgeHolder.formatter.formatFullInstant(instant.toEpochMilliseconds().toDouble())

    override fun formatAbsoluteInstant(instant: Instant): String =
        WebFormatterBridgeHolder.formatter.formatAbsoluteInstant(instant.toEpochMilliseconds().toDouble())
}

private object WebFormatterBridgeHolder {
    var formatter: WebFormatterBridge = FallbackWebFormatterBridge
}

private object FallbackWebFormatterBridge : WebFormatterBridge {
    override fun formatNumber(number: Double): String = number.toLong().toString()

    override fun formatRelativeInstant(epochMillis: Double): String = Instant.fromEpochMilliseconds(epochMillis.toLong()).toString()

    override fun formatFullInstant(epochMillis: Double): String = Instant.fromEpochMilliseconds(epochMillis.toLong()).toString()

    override fun formatAbsoluteInstant(epochMillis: Double): String = Instant.fromEpochMilliseconds(epochMillis.toLong()).toString()
}
