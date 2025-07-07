package dev.dimension.flare.ui.render

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

internal actual fun Instant.shortTime(): LocalizedShortTime {
    val compareTo = Clock.System.now()
    val timeZone = TimeZone.currentSystemDefault()
    val time = this.toLocalDateTime(timeZone)
    val diff = compareTo - this
    return when {
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
            LocalizedShortTime.String(diff.inWholeDays.toString() + " d")
        }
        // xx hr(s)
        diff.inWholeHours >= 1 -> {
            LocalizedShortTime.String(diff.inWholeHours.toString() + " h")
        }
        // xx sec(s)
        diff.inWholeMinutes < 1 -> {
            LocalizedShortTime.String(diff.inWholeSeconds.toString() + " s")
        }
        // xx min(s)
        else -> {
            LocalizedShortTime.String(diff.inWholeMinutes.toString() + " m")
        }
    }
}

// public actual data class UiDateTime(
//    val value: Instant,
// ) {
//    public val shortTime: LocalizedShortTime by lazy {
//        val compareTo = Clock.System.now()
//        val timeZone = TimeZone.currentSystemDefault()
//        val time = value.toLocalDateTime(timeZone)
//        val diff = compareTo - value
//        when {
//            // dd MMM yy
//            compareTo.toLocalDateTime(timeZone).year != time.year -> {
//                LocalizedShortTime.YearMonthDay(time.toJavaLocalDateTime())
//            }
//            // dd MMM
//            diff.inWholeDays >= 7 -> {
//                LocalizedShortTime.MonthDay(time.toJavaLocalDateTime())
//            }
//            // xx day(s)
//            diff.inWholeDays >= 1 -> {
//                LocalizedShortTime.String(diff.inWholeDays.toString() + " d")
//            }
//            // xx hr(s)
//            diff.inWholeHours >= 1 -> {
//                LocalizedShortTime.String(diff.inWholeHours.toString() + " h")
//            }
//            // xx sec(s)
//            diff.inWholeMinutes < 1 -> {
//                LocalizedShortTime.String(diff.inWholeSeconds.toString() + " s")
//            }
//            // xx min(s)
//            else -> {
//                LocalizedShortTime.String(diff.inWholeMinutes.toString() + " m")
//            }
//        }
//    }
// }
//
// internal actual fun Instant.toUi(): UiDateTime = UiDateTime(this)

// public sealed interface LocalizedShortTime {
//    public data class String(
//        val value: kotlin.String,
//    ) : LocalizedShortTime
//
//    public data class YearMonthDay(
//        val localDateTime: LocalDateTime,
//    ) : LocalizedShortTime
//
//    public data class MonthDay(
//        val localDateTime: LocalDateTime,
//    ) : LocalizedShortTime
// }
