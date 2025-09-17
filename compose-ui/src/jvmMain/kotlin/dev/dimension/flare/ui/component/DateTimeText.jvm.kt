package dev.dimension.flare.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import dev.dimension.flare.ui.render.UiDateTime
import org.ocpsoft.prettytime.PrettyTime
import java.time.format.DateTimeFormatter
import java.util.Date

private val prettyTime = PrettyTime(Date(0))

@Composable
internal actual fun rememberFormattedDateTime(
    data: UiDateTime,
    fullTime: Boolean,
): String =
    rememberSaveable(data, UiDateTimeSaver) {
        if (fullTime) {
            DateTimeFormatter.ISO_DATE_TIME
                .format(
                    java.time.Instant
                        .ofEpochMilli(data.value.toEpochMilliseconds())
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDateTime(),
                )
        } else {
            val diff = data.value.toEpochMilliseconds() - System.currentTimeMillis()
            when (data.diff) {
                UiDateTime.DiffType.DAYS,
                UiDateTime.DiffType.HOURS,
                UiDateTime.DiffType.MINUTES,
                UiDateTime.DiffType.SECONDS,
                ->
                    prettyTime.format(Date(diff))
                UiDateTime.DiffType.YEAR_MONTH_DAY ->
                    DateTimeFormatter.ISO_DATE
                        .format(
                            java.time.Instant
                                .ofEpochMilli(data.value.toEpochMilliseconds())
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDate(),
                        )
                UiDateTime.DiffType.MONTH_DAY ->
                    DateTimeFormatter.ISO_DATE
                        .format(
                            java.time.Instant
                                .ofEpochMilli(data.value.toEpochMilliseconds())
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDate(),
                        )
            }
        }
    }
