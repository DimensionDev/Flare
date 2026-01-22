package dev.dimension.flare.ui.humanizer

import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import org.ocpsoft.prettytime.PrettyTime
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Instant

private val prettyTime = PrettyTime(Date(0))

public fun updateTimeFormatterLocale(locale: Locale) {
    prettyTime.setLocale(locale)
}

internal class JVMFormatter : PlatformFormatter {
    override fun formatNumber(number: Long): String {
        val fmt =
            NumberFormat.getCompactNumberInstance(
                Locale.getDefault(),
                NumberFormat.Style.SHORT,
            )
        fmt.roundingMode = RoundingMode.DOWN
        fmt.maximumFractionDigits = 2
        fmt.minimumFractionDigits = 0
        fmt.isGroupingUsed = false
        return fmt.format(number)
    }

    override fun formatRelativeInstant(instant: Instant): String {
        val compareTo = Clock.System.now()
        val diff = compareTo - instant
        return when {
            diff.inWholeDays < 7 -> {
                prettyTime.format(Date(-diff.inWholeMilliseconds))
            }
            else -> {
                DateTimeFormatter
                    .ofLocalizedDate(FormatStyle.MEDIUM)
                    .withLocale(Locale.getDefault())
                    .format(
                        java.time.Instant
                            .ofEpochMilli(instant.toEpochMilliseconds())
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate(),
                    )
            }
        }
    }

    override fun formatFullInstant(instant: Instant): String =
        DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault())
            .format(
                java.time.Instant
                    .ofEpochMilli(instant.toEpochMilliseconds())
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime(),
            )

    override fun formatAbsoluteInstant(instant: Instant): String {
        val now = Clock.System.now()
        val timeZone = TimeZone.currentSystemDefault()
        val nowDate = now.toLocalDateTime(timeZone).date
        val instantDate = instant.toLocalDateTime(timeZone).date
        val daysDiff = instantDate.daysUntil(nowDate)
        val locale = Locale.getDefault()

        val zonedDateTime =
            java.time.Instant
                .ofEpochMilli(instant.toEpochMilliseconds())
                .atZone(ZoneId.systemDefault())

        return when {
            daysDiff == 0 -> {
                DateTimeFormatter
                    .ofLocalizedTime(FormatStyle.SHORT)
                    .withLocale(locale)
                    .format(zonedDateTime)
            }
            daysDiff < 7 -> {
                val day = DateTimeFormatter
                    .ofPattern("E")
                    .withLocale(locale)
                    .format(zonedDateTime)
                val time = DateTimeFormatter
                    .ofLocalizedTime(FormatStyle.SHORT)
                    .withLocale(locale)
                    .format(zonedDateTime)
                "$day $time"
            }
            else -> {
                DateTimeFormatter
                    .ofLocalizedDateTime(FormatStyle.SHORT)
                    .withLocale(locale)
                    .format(zonedDateTime)
            }
        }
    }
}
