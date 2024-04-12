package dev.dimension.flare.ui.humanizer

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private fun Int.withLeadingZero(): String {
    return if (this < 10) {
        "0$this"
    } else {
        this.toString()
    }
}

fun Instant.humanize(
    compareTo: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val time = toLocalDateTime(timeZone)
    val diff = compareTo - this
    return when {
        // dd MMM yy
        compareTo.toLocalDateTime(timeZone).year != time.year -> {
            "${time.dayOfMonth.withLeadingZero()} ${time.month.abbr()} ${time.year.yearAbbr()}"
        }
        // dd MMM
        diff.inWholeDays >= 7 -> {
            "${time.dayOfMonth.withLeadingZero()} ${time.month.abbr()}"
        }
        // xx day(s)
        diff.inWholeDays >= 1 -> {
            "${diff.inWholeDays} day${if (diff.inWholeDays > 1) "s" else ""}"
        }
        // xx hr(s)
        diff.inWholeHours >= 1 -> {
            "${diff.inWholeHours} hr${if (diff.inWholeHours > 1) "s" else ""}"
        }
        // now
        diff.inWholeMinutes < 1 -> {
            "Now"
        }
        // xx min(s)
        else -> {
            "${(diff.inWholeMinutes).coerceAtLeast(0)} min${if (diff.inWholeMinutes > 1) "s" else ""}"
        }
    }
}

fun Instant.fullHumanize(timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
    val time = toLocalDateTime(timeZone)
    return "${time.dayOfMonth.withLeadingZero()} ${time.month.abbr()} ${time.year}," +
        " ${time.hour.withLeadingZero()}:${time.minute.withLeadingZero()}"
}

fun Long.toLocalDateTime(timeZone: TimeZone = TimeZone.currentSystemDefault()): LocalDateTime {
    return Instant.fromEpochMilliseconds(this).toLocalDateTime(timeZone)
}

private fun Month.abbr() = name.substring(0, 3).lowercase().replaceFirstChar { if (it.isLowerCase()) it.uppercaseChar() else it }

private fun Int.yearAbbr() = toString().substring(2, 4)
