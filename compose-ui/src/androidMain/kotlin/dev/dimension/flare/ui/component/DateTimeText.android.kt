package dev.dimension.flare.ui.component

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import dev.dimension.flare.ui.render.UiDateTime

@Composable
internal actual fun rememberFormattedDateTime(
    data: UiDateTime,
    fullTime: Boolean,
): String {
    val context = LocalContext.current
    return rememberSaveable(data, UiDateTimeSaver) {
        if (fullTime) {
            DateUtils.formatDateTime(
                context,
                data.value.toEpochMilliseconds(),
                DateUtils.FORMAT_SHOW_DATE or
                    DateUtils.FORMAT_SHOW_TIME or
                    DateUtils.FORMAT_ABBREV_MONTH or
                    DateUtils.FORMAT_NUMERIC_DATE or
                    DateUtils.FORMAT_SHOW_YEAR,
            )
        } else {
            when (data.diff) {
                UiDateTime.DiffType.DAYS ->
                    DateUtils
                        .getRelativeTimeSpanString(
                            data.value.toEpochMilliseconds(),
                            System.currentTimeMillis(),
                            DateUtils.DAY_IN_MILLIS,
                            DateUtils.FORMAT_ABBREV_RELATIVE,
                        ).toString()
                UiDateTime.DiffType.HOURS ->
                    DateUtils
                        .getRelativeTimeSpanString(
                            data.value.toEpochMilliseconds(),
                            System.currentTimeMillis(),
                            DateUtils.HOUR_IN_MILLIS,
                            DateUtils.FORMAT_ABBREV_RELATIVE,
                        ).toString()
                UiDateTime.DiffType.MINUTES ->
                    DateUtils
                        .getRelativeTimeSpanString(
                            data.value.toEpochMilliseconds(),
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS,
                            DateUtils.FORMAT_ABBREV_RELATIVE,
                        ).toString()
                UiDateTime.DiffType.SECONDS ->
                    DateUtils
                        .getRelativeTimeSpanString(
                            data.value.toEpochMilliseconds(),
                            System.currentTimeMillis(),
                            DateUtils.SECOND_IN_MILLIS,
                            DateUtils.FORMAT_ABBREV_RELATIVE,
                        ).toString()
                UiDateTime.DiffType.YEAR_MONTH_DAY ->
                    DateUtils.formatDateTime(
                        context,
                        data.value.toEpochMilliseconds(),
                        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_NUMERIC_DATE,
                    )
                UiDateTime.DiffType.MONTH_DAY ->
                    DateUtils.formatDateTime(
                        context,
                        data.value.toEpochMilliseconds(),
                        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH,
                    )
            }
        }
    }
}
