package dev.dimension.flare.ui.render

import androidx.compose.runtime.Immutable
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime

@Immutable
data class UiDateTime(
    private val value: Instant,
) {
    val shortTime by lazy {
        val compareTo = Clock.System.now()
        val timeZone = TimeZone.currentSystemDefault()
        val time = value.toLocalDateTime(timeZone)
        val diff = compareTo - value
        when {
            // dd MMM yy
            compareTo.toLocalDateTime(timeZone).year != time.year -> {
                time.format(
                    LocalDateTime.Format {
                        date(LocalDate.Formats.ISO)
//                        char(' ')
//                        hour(); char(':'); minute()
                    },
                )
            }
            // dd MMM
            diff.inWholeDays >= 7 -> {
                time.format(
                    LocalDateTime.Format {
                        monthNumber()
                        char('-')
                        dayOfMonth()
//                        char(' ')
//                        hour(); char(':'); minute()
                    },
                )
            }
            // xx day(s)
            diff.inWholeDays >= 1 -> {
                diff.inWholeDays.toString() + " days ago"
            }
            // xx hr(s)
            diff.inWholeHours >= 1 -> {
                diff.inWholeHours.toString() + " hours ago"
            }
            // xx sec(s)
            diff.inWholeMinutes < 1 -> {
                diff.inWholeSeconds.toString() + " seconds ago"
            }
            // xx min(s)
            else -> {
                diff.inWholeMinutes.toString() + " minutes ago"
            }
        }
    }

    val fullTime by lazy {
        value.toLocalDateTime(TimeZone.currentSystemDefault()).format(
            LocalDateTime.Format {
                date(LocalDate.Formats.ISO)
                char(' ')
                hour()
                char(':')
                minute()
                char(':')
                second()
            },
        )
    }
}

internal fun Instant.toUi(): UiDateTime = UiDateTime(this)
