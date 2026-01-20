package dev.dimension.flare.ui.humanizer

import kotlinx.datetime.TimeZone
import org.ocpsoft.prettytime.PrettyTime
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
                DateTimeFormatter.ISO_DATE
                    .format(
                        java.time.Instant
                            .ofEpochMilli(instant.toEpochMilliseconds())
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate(),
                    )
            }
        }
    }

    override fun formatFullInstant(instant: Instant): String =
        DateTimeFormatter.ISO_DATE_TIME
            .format(
                java.time.Instant
                    .ofEpochMilli(instant.toEpochMilliseconds())
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime(),
            )

    override fun formatAbsoluteInstant(instant: Instant): String {
        val now = Clock.System.now()
        val timeZone = TimeZone.currentSystemDefault()
        val datePattern = getAbsoluteDatePattern(instant, now, timeZone)
        
        val pattern = if (datePattern.isEmpty()) ABSOLUTE_TIME_PATTERN else "$datePattern $ABSOLUTE_TIME_PATTERN"
        val instantZonedDateTime = java.time.Instant.ofEpochMilli(instant.toEpochMilliseconds()).atZone(ZoneId.systemDefault())
        val formatter = DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
        return formatter.format(instantZonedDateTime)
    }
}
