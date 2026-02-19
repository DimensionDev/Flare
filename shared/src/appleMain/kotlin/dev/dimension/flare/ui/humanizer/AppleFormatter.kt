package dev.dimension.flare.ui.humanizer

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterLongStyle
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSRelativeDateTimeFormatter
import platform.Foundation.NSRelativeDateTimeFormatterStyleNumeric
import platform.Foundation.dateWithTimeIntervalSince1970

public interface SwiftFormatter {
    public fun formatNumber(number: Long): String
}

internal class AppleFormatter(
    private val formatter: SwiftFormatter,
) : PlatformFormatter {
    override fun formatNumber(number: Long): String = formatter.formatNumber(number)

    override fun formatRelativeInstant(instant: Instant): String {
        val compareTo = Clock.System.now()
        val diff = compareTo - instant
        return when {
            diff.inWholeDays < 7 -> {
                PlatformDateFormatter.getRelativeTimeSpanString(instant.toEpochMilliseconds())
            }
            else -> {
                PlatformDateFormatter.formatAsFullDate(instant.toEpochMilliseconds())
            }
        }
    }

    override fun formatFullInstant(instant: Instant): String = PlatformDateFormatter.formatAsFullDateTime(instant.toEpochMilliseconds())

    override fun formatAbsoluteInstant(instant: Instant): String {
        val now = Clock.System.now()
        val timeZone = TimeZone.currentSystemDefault()
        val nowDate = now.toLocalDateTime(timeZone).date
        val instantDate = instant.toLocalDateTime(timeZone).date
        val daysDiff = instantDate.daysUntil(nowDate)

        val date = NSDate.dateWithTimeIntervalSince1970(instant.toEpochMilliseconds() / 1000.0)
        val formatter = NSDateFormatter()
        when {
            daysDiff == 0 -> {
                formatter.dateStyle = NSDateFormatterNoStyle
                formatter.timeStyle = NSDateFormatterShortStyle
            }
            daysDiff < 7 -> {
                formatter.dateStyle = NSDateFormatterShortStyle
                formatter.timeStyle = NSDateFormatterShortStyle
            }
            else -> {
                formatter.dateStyle = NSDateFormatterShortStyle
                formatter.timeStyle = NSDateFormatterShortStyle
            }
        }
        return formatter.stringFromDate(date)
    }
}

private object PlatformDateFormatter {
    fun formatAsFullDateTime(epochMillis: Long): String {
        val date = NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0)
        val formatter =
            NSDateFormatter().apply {
                dateStyle = NSDateFormatterLongStyle
                timeStyle = NSDateFormatterMediumStyle
            }
        return formatter.stringFromDate(date)
    }

    fun formatAsFullDate(epochMillis: Long): String {
        val date = NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0)
        val formatter =
            NSDateFormatter().apply {
                dateStyle = NSDateFormatterLongStyle
                timeStyle = NSDateFormatterNoStyle
            }
        return formatter.stringFromDate(date)
    }

    fun formatAsFullDateWithoutYear(epochMillis: Long): String {
        val date = NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0)
        val formatter =
            NSDateFormatter().apply {
                dateStyle = NSDateFormatterMediumStyle
                timeStyle = NSDateFormatterNoStyle
            }
        return formatter.stringFromDate(date)
    }

    fun getRelativeTimeSpanString(epochMillis: Long): String {
        val date = NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0)
        val formatter =
            NSRelativeDateTimeFormatter().apply {
                dateTimeStyle = NSRelativeDateTimeFormatterStyleNumeric
            }
        return formatter.localizedStringForDate(date, relativeToDate = NSDate())
    }
}
