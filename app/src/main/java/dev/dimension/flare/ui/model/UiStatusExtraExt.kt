package dev.dimension.flare.ui.model

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import dev.dimension.flare.R
import dev.dimension.flare.ui.render.LocalizedShortTime
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import java.time.format.DateTimeFormatter

val LocalizedShortTime.localizedShortTime: String
    @Composable
    get() =
        when (val type = this) {
            is LocalizedShortTime.MonthDay ->
                DateTimeFormatter
                    .ofPattern(stringResource(id = R.string.date_format_month_day))
                    .format(type.localDateTime)
            is LocalizedShortTime.String -> type.value
            is LocalizedShortTime.YearMonthDay ->
                DateTimeFormatter
                    .ofPattern(stringResource(id = R.string.date_format_year_month_day))
                    .format(type.localDateTime)
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
