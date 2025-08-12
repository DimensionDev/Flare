package dev.dimension.flare.ui.render

import java.time.LocalDateTime
import kotlin.time.Instant

internal expect fun Instant.shortTime(): LocalizedShortTime

public actual data class UiDateTime internal constructor(
    val value: Instant,
) {
    val shortTime: LocalizedShortTime
        get() = value.shortTime()
}

internal actual fun Instant.toUi(): UiDateTime = UiDateTime(this)

internal actual operator fun UiDateTime.compareTo(other: UiDateTime): Int = value.compareTo(other.value)

public sealed interface LocalizedShortTime {
    public data class String(
        val value: kotlin.String,
    ) : LocalizedShortTime

    public data class YearMonthDay(
        val localDateTime: LocalDateTime,
    ) : LocalizedShortTime

    public data class MonthDay(
        val localDateTime: LocalDateTime,
    ) : LocalizedShortTime
}
