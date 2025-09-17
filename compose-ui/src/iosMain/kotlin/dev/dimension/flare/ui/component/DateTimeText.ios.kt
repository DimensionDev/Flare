package dev.dimension.flare.ui.component

import androidx.compose.runtime.saveable.rememberSaveable
import dev.dimension.flare.ui.render.UiDateTime
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterLongStyle
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSRelativeDateTimeFormatter
import platform.Foundation.NSRelativeDateTimeFormatterStyleNumeric
import platform.Foundation.dateWithTimeIntervalSince1970

@androidx.compose.runtime.Composable
internal actual fun rememberFormattedDateTime(
    data: UiDateTime,
    fullTime: Boolean,
): String =
    rememberSaveable(data, UiDateTimeSaver) {
        if (fullTime) {
            PlatformDateFormatter.formatAsFullDateTime(data.value.toEpochMilliseconds())
        } else {
            when (data.diff) {
                UiDateTime.DiffType.DAYS ->
                    PlatformDateFormatter.getRelativeTimeSpanString(data.value.toEpochMilliseconds())
                UiDateTime.DiffType.HOURS ->
                    PlatformDateFormatter.getRelativeTimeSpanString(data.value.toEpochMilliseconds())
                UiDateTime.DiffType.MINUTES ->
                    PlatformDateFormatter.getRelativeTimeSpanString(data.value.toEpochMilliseconds())
                UiDateTime.DiffType.SECONDS ->
                    PlatformDateFormatter.getRelativeTimeSpanString(data.value.toEpochMilliseconds())
                UiDateTime.DiffType.YEAR_MONTH_DAY ->
                    PlatformDateFormatter.formatAsFullDate(data.value.toEpochMilliseconds())
                UiDateTime.DiffType.MONTH_DAY ->
                    PlatformDateFormatter.formatAsFullDateWithoutYear(data.value.toEpochMilliseconds())
            }
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
