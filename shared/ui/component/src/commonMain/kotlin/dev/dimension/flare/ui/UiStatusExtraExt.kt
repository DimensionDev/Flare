package dev.dimension.flare.ui.model

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.dimension.flare.ui.component.Res
import dev.dimension.flare.ui.component.date_format_full
import dev.dimension.flare.ui.component.date_format_month_day
import dev.dimension.flare.ui.component.date_format_year_month_day
import dev.dimension.flare.ui.render.LocalizedShortTime
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import java.time.format.DateTimeFormatter

public val LocalizedShortTime.localizedShortTime: String
    @Composable
    get() =
        when (val type = this) {
            is LocalizedShortTime.MonthDay ->
                runCatching {
                    DateTimeFormatter
                        .ofPattern(stringResource(resource = Res.string.date_format_month_day))
                        .format(type.localDateTime)
                }.getOrElse {
                    DateTimeFormatter
                        .ofPattern("dd MMM")
                        .format(type.localDateTime)
                }
            is LocalizedShortTime.String -> type.value
            is LocalizedShortTime.YearMonthDay ->
                runCatching {
                    DateTimeFormatter
                        .ofPattern(stringResource(resource = Res.string.date_format_year_month_day))
                        .format(type.localDateTime)
                }.getOrElse {
                    DateTimeFormatter
                        .ofPattern("dd MMM yy")
                        .format(type.localDateTime)
                }
        }

public val Instant.localizedFullTime: String
    @Composable
    get() {
        val format = stringResource(resource = Res.string.date_format_full)
        return remember(this) {
            val timeZone = TimeZone.currentSystemDefault()
            val time = toLocalDateTime(timeZone)
            runCatching {
                DateTimeFormatter.ofPattern(format).format(time.toJavaLocalDateTime())
            }.getOrElse {
                DateTimeFormatter.ofPattern("dd MMM yy HH:mm").format(time.toJavaLocalDateTime())
            }
        }
    }
