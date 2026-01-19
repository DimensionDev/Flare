package dev.dimension.flare.ui.humanizer

import org.ocpsoft.prettytime.PrettyTime
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
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
        val zoneId = ZoneId.systemDefault()
        val nowDate = java.time.Instant.ofEpochMilli(now.toEpochMilliseconds()).atZone(zoneId).toLocalDate()
        val instantZonedDateTime = java.time.Instant.ofEpochMilli(instant.toEpochMilliseconds()).atZone(zoneId)
        val instantDate = instantZonedDateTime.toLocalDate()
        val daysDiff = ChronoUnit.DAYS.between(instantDate, nowDate)

        val datePattern = when {
            daysDiff < 7 -> "EEE"
            nowDate.year == instantDate.year -> "d MMM"
            else -> "yyyy-MM-dd"
        }
        val timePattern = "h:mma"

        val formatter = DateTimeFormatter.ofPattern("$datePattern $timePattern", Locale.getDefault())
        return formatter.format(instantZonedDateTime)
    }
}
