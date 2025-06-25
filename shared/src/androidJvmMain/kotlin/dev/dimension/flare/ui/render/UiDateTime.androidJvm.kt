package dev.dimension.flare.ui.render

import kotlin.time.Instant
import java.time.LocalDateTime

internal expect fun Instant.shortTime(): LocalizedShortTime

public actual data class UiDateTime internal constructor(
    val value: Instant,
) {
    val shortTime: LocalizedShortTime
        get() = value.shortTime()
}

internal actual fun Instant.toUi(): UiDateTime = UiDateTime(this)

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
