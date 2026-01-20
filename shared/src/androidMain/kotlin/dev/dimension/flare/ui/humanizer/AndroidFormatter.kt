package dev.dimension.flare.ui.humanizer

import android.content.Context
import android.icu.text.CompactDecimalFormat
import android.text.format.DateUtils
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Instant

internal class AndroidFormatter(
    private val context: Context,
) : PlatformFormatter {
    override fun formatNumber(number: Long): String {
        val cdf =
            CompactDecimalFormat.getInstance(
                Locale.getDefault(),
                CompactDecimalFormat.CompactStyle.SHORT,
            )
        cdf.maximumFractionDigits = 2
        cdf.minimumFractionDigits = 0
        cdf.roundingMode = RoundingMode.DOWN.ordinal
        cdf.isGroupingUsed = false
        return cdf.format(number)
    }

    override fun formatRelativeInstant(instant: Instant): String {
        val compareTo = Clock.System.now()
        val timeZone = TimeZone.currentSystemDefault()
        val time = instant.toLocalDateTime(timeZone)
        val diff = compareTo - instant
        return when {
            diff.inWholeDays >= 7 -> {
                DateUtils.formatDateTime(
                    context,
                    instant.toEpochMilliseconds(),
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH,
                )
            }
            diff.inWholeDays >= 1 -> {
                DateUtils
                    .getRelativeTimeSpanString(
                        instant.toEpochMilliseconds(),
                        System.currentTimeMillis(),
                        DateUtils.DAY_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE,
                    ).toString()
            }
            diff.inWholeHours >= 1 -> {
                DateUtils
                    .getRelativeTimeSpanString(
                        instant.toEpochMilliseconds(),
                        System.currentTimeMillis(),
                        DateUtils.HOUR_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE,
                    ).toString()
            }
            diff.inWholeMinutes < 1 -> {
                DateUtils
                    .getRelativeTimeSpanString(
                        instant.toEpochMilliseconds(),
                        System.currentTimeMillis(),
                        DateUtils.SECOND_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE,
                    ).toString()
            }
            compareTo.toLocalDateTime(timeZone).year != time.year -> {
                DateUtils.formatDateTime(
                    context,
                    instant.toEpochMilliseconds(),
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_NUMERIC_DATE,
                )
            }
            else -> {
                DateUtils
                    .getRelativeTimeSpanString(
                        instant.toEpochMilliseconds(),
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE,
                    ).toString()
            }
        }
    }

    override fun formatFullInstant(instant: Instant): String =
        DateUtils.formatDateTime(
            context,
            instant.toEpochMilliseconds(),
            DateUtils.FORMAT_SHOW_DATE or
                DateUtils.FORMAT_SHOW_TIME or
                DateUtils.FORMAT_ABBREV_MONTH or
                DateUtils.FORMAT_NUMERIC_DATE or
                DateUtils.FORMAT_SHOW_YEAR,
        )

    override fun formatAbsoluteInstant(instant: Instant): String {
        val now = Clock.System.now()
        val timeZone = TimeZone.currentSystemDefault()
        val datePattern = getAbsoluteDatePattern(instant, now, timeZone)

        val locale = Locale.getDefault()
        val pattern = if (datePattern.isEmpty()) ABSOLUTE_TIME_PATTERN else "$datePattern $ABSOLUTE_TIME_PATTERN"
        val sdf = SimpleDateFormat(pattern, locale)
        return sdf.format(Date(instant.toEpochMilliseconds()))
    }
}
