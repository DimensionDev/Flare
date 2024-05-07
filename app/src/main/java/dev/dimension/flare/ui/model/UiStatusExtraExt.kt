package dev.dimension.flare.ui.model

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import dev.dimension.flare.R
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import java.time.format.DateTimeFormatter

val UiStatus.localizedShortTime: String
    @Composable
    get() = extra.createdAt.localizedShortTime

val UiStatus.localizedFullTime: String
    @Composable
    get() = extra.createdAt.localizedFullTime

val Instant.localizedShortTime: String
    @Composable
    get() {
        val formatYearMonthDay = stringResource(id = R.string.date_format_year_month_day)
        val formatMonthDay = stringResource(id = R.string.date_format_month_day)
        return remember(this) {
            val compareTo = Clock.System.now()
            val timeZone = TimeZone.currentSystemDefault()
            val time = toLocalDateTime(timeZone)
            val diff = compareTo - this
            when {
                // dd MMM yy
                compareTo.toLocalDateTime(timeZone).year != time.year -> {
                    DateTimeFormatter.ofPattern(formatYearMonthDay).format(time.toJavaLocalDateTime())
                }
                // dd MMM
                diff.inWholeDays >= 7 -> {
                    DateTimeFormatter.ofPattern(formatMonthDay).format(time.toJavaLocalDateTime())
                }
                // xx day(s)
                diff.inWholeDays >= 1 -> {
                    DateUtils.getRelativeTimeSpanString(
                        toEpochMilliseconds(),
                        System.currentTimeMillis(),
                        DateUtils.DAY_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE,
                    ).toString()
                }
                // xx hr(s)
                diff.inWholeHours >= 1 -> {
                    DateUtils.getRelativeTimeSpanString(
                        toEpochMilliseconds(),
                        System.currentTimeMillis(),
                        DateUtils.HOUR_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE,
                    ).toString()
                }
                // xx sec(s)
                diff.inWholeMinutes < 1 -> {
                    DateUtils.getRelativeTimeSpanString(
                        toEpochMilliseconds(),
                        System.currentTimeMillis(),
                        DateUtils.SECOND_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE,
                    ).toString()
                }
                // xx min(s)
                else -> {
                    DateUtils.getRelativeTimeSpanString(
                        toEpochMilliseconds(),
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE,
                    ).toString()
                }
            }
        }
    }

val Instant.localizedFullTime: String
    @Composable
    get() {
        val format = stringResource(id = R.string.date_format_full)
        return remember(this) {
            val timeZone = TimeZone.currentSystemDefault()
            val time = toLocalDateTime(timeZone)
            DateTimeFormatter.ofPattern(format).format(time.toJavaLocalDateTime())
        }
    }
