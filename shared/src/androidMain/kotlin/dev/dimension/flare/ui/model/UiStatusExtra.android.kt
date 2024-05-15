package dev.dimension.flare.ui.model

import android.text.format.DateUtils
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import java.text.Bidi
import java.time.LocalDateTime

@Immutable
actual data class UiStatusExtra(
    val contentDirection: LayoutDirection,
    val createdAt: Instant,
) {
    companion object {
        val Empty =
            UiStatusExtra(
                contentDirection = LayoutDirection.Ltr,
                createdAt = Instant.DISTANT_PAST,
            )
    }

    val localizedShortTimeType: LocalizedShortTime by lazy {
        val compareTo = Clock.System.now()
        val timeZone = TimeZone.currentSystemDefault()
        val time = createdAt.toLocalDateTime(timeZone)
        val diff = compareTo - createdAt
        when {
            // dd MMM yy
            compareTo.toLocalDateTime(timeZone).year != time.year -> {
                LocalizedShortTime.YearMonthDay(time.toJavaLocalDateTime())
            }
            // dd MMM
            diff.inWholeDays >= 7 -> {
                LocalizedShortTime.MonthDay(time.toJavaLocalDateTime())
            }
            // xx day(s)
            diff.inWholeDays >= 1 -> {
                DateUtils.getRelativeTimeSpanString(
                    createdAt.toEpochMilliseconds(),
                    System.currentTimeMillis(),
                    DateUtils.DAY_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE,
                ).toString().let(LocalizedShortTime::String)
            }
            // xx hr(s)
            diff.inWholeHours >= 1 -> {
                DateUtils.getRelativeTimeSpanString(
                    createdAt.toEpochMilliseconds(),
                    System.currentTimeMillis(),
                    DateUtils.HOUR_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE,
                ).toString().let(LocalizedShortTime::String)
            }
            // xx sec(s)
            diff.inWholeMinutes < 1 -> {
                DateUtils.getRelativeTimeSpanString(
                    createdAt.toEpochMilliseconds(),
                    System.currentTimeMillis(),
                    DateUtils.SECOND_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE,
                ).toString().let(LocalizedShortTime::String)
            }
            // xx min(s)
            else -> {
                DateUtils.getRelativeTimeSpanString(
                    createdAt.toEpochMilliseconds(),
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE,
                ).toString().let(LocalizedShortTime::String)
            }
        }
    }
}

sealed interface LocalizedShortTime {
    data class String(val value: kotlin.String) : LocalizedShortTime

    data class YearMonthDay(val localDateTime: LocalDateTime) : LocalizedShortTime

    data class MonthDay(val localDateTime: LocalDateTime) : LocalizedShortTime
}

val UiStatus.contentDirection get() = extra.contentDirection

internal actual fun createStatusExtra(status: UiStatus): UiStatusExtra {
    return when (status) {
        is UiStatus.Mastodon -> {
            UiStatusExtra(
                contentDirection =
                    if (Bidi(
                            status.content,
                            Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT,
                        ).baseIsLeftToRight()
                    ) {
                        LayoutDirection.Ltr
                    } else {
                        LayoutDirection.Rtl
                    },
                createdAt = status.createdAt,
            )
        }

        is UiStatus.MastodonNotification -> {
            UiStatusExtra.Empty
        }

        is UiStatus.Misskey -> {
            UiStatusExtra(
                contentDirection =
                    if (Bidi(
                            status.content,
                            Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT,
                        ).baseIsLeftToRight()
                    ) {
                        LayoutDirection.Ltr
                    } else {
                        LayoutDirection.Rtl
                    },
                createdAt = status.createdAt,
            )
        }

        is UiStatus.MisskeyNotification -> {
            UiStatusExtra.Empty
        }

        is UiStatus.Bluesky -> {
            UiStatusExtra(
                contentDirection =
                    if (Bidi(
                            status.content,
                            Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT,
                        ).baseIsLeftToRight()
                    ) {
                        LayoutDirection.Ltr
                    } else {
                        LayoutDirection.Rtl
                    },
                createdAt = status.indexedAt,
            )
        }

        is UiStatus.BlueskyNotification -> {
            UiStatusExtra.Empty
        }

        is UiStatus.XQT -> {
            UiStatusExtra(
                contentDirection =
                    if (Bidi(
                            status.content,
                            Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT,
                        ).baseIsLeftToRight()
                    ) {
                        LayoutDirection.Ltr
                    } else {
                        LayoutDirection.Rtl
                    },
                createdAt = status.createdAt,
            )
        }

        is UiStatus.XQTNotification -> {
            UiStatusExtra.Empty
        }
    }
}
