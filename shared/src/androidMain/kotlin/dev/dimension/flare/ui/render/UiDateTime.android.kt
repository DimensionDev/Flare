package dev.dimension.flare.ui.render

import android.text.format.DateUtils
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

internal actual fun Instant.shortTime(): LocalizedShortTime {
    val compareTo = Clock.System.now()
    val timeZone = TimeZone.currentSystemDefault()
    val time = this.toLocalDateTime(timeZone)
    val diff = compareTo - this
    return when {
        // dd MMM yy
        compareTo.toLocalDateTime(timeZone).year != time.year -> {
            LocalizedShortTime.YearMonthDay(time.toJavaLocalDateTime())
        }
        // dd MMM
        diff.inWholeDays >= 7 -> {
            LocalizedShortTime.MonthDay(time.toJavaLocalDateTime())
        }
        // xx day(s)
        diff.inWholeDays >= 1 -> {
            DateUtils
                .getRelativeTimeSpanString(
                    this.toEpochMilliseconds(),
                    System.currentTimeMillis(),
                    DateUtils.DAY_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE,
                ).toString()
                .let(LocalizedShortTime::String)
        }
        // xx hr(s)
        diff.inWholeHours >= 1 -> {
            DateUtils
                .getRelativeTimeSpanString(
                    this.toEpochMilliseconds(),
                    System.currentTimeMillis(),
                    DateUtils.HOUR_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE,
                ).toString()
                .let(LocalizedShortTime::String)
        }
        // xx sec(s)
        diff.inWholeMinutes < 1 -> {
            DateUtils
                .getRelativeTimeSpanString(
                    this.toEpochMilliseconds(),
                    System.currentTimeMillis(),
                    DateUtils.SECOND_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE,
                ).toString()
                .let(LocalizedShortTime::String)
        }
        // xx min(s)
        else -> {
            DateUtils
                .getRelativeTimeSpanString(
                    this.toEpochMilliseconds(),
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE,
                ).toString()
                .let(LocalizedShortTime::String)
        }
    }
}
