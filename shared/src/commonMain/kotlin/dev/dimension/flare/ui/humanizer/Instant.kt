package dev.dimension.flare.ui.humanizer

import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Instant

private fun Int.withLeadingZero(): String =
    if (this < 10) {
        "0$this"
    } else {
        this.toString()
    }

public fun Duration.humanize(): String =
    this.toComponents { days, hours, minutes, seconds, _ ->
        buildString {
            if (days > 0) {
                append("${days.toInt().withLeadingZero()}:")
            }
            if (hours > 0) {
                append("${hours.withLeadingZero()}:")
            }
            if (minutes > 0) {
                append("${minutes.withLeadingZero()}:")
            } else {
                append("0:")
            }
            append(seconds.withLeadingZero())
        }
    }

internal fun getAbsoluteDatePattern(
    instant: Instant,
    now: Instant,
    timeZone: TimeZone,
): String {
    val nowDate = now.toLocalDateTime(timeZone).date
    val instantDate = instant.toLocalDateTime(timeZone).date
    val daysDiff = instantDate.daysUntil(nowDate)

    return when {
        daysDiff == 0 -> ""
        daysDiff < 7 -> "EEE"
        nowDate.year == instantDate.year -> "d MMM"
        else -> "yyyy-MM-dd"
    }
}

internal const val ABSOLUTE_TIME_PATTERN: String = "h:mma"
