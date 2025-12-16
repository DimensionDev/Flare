package dev.dimension.flare.ui.render

import dev.dimension.flare.ui.humanizer.Formatter.full
import dev.dimension.flare.ui.humanizer.Formatter.relative
import kotlin.time.Instant

public expect class PlatformDateTime

internal expect fun Instant.toPlatform(): PlatformDateTime

public data class UiDateTime internal constructor(
    val value: Instant,
) {
    val platformValue: PlatformDateTime by lazy {
        value.toPlatform()
    }
    val relative: String = value.relative()
    val full: String = value.full()

//    val diff: DiffType by lazy {
//
//        val compareTo = Clock.System.now()
//        val timeZone = TimeZone.currentSystemDefault()
//        val time = this.value.toLocalDateTime(timeZone)
//        val diff = compareTo - this.value
//        when {
//            compareTo.toLocalDateTime(timeZone).year != time.year -> {
//                DiffType.YEAR_MONTH_DAY
//            }
//            diff.inWholeDays >= 7 -> {
//                DiffType.MONTH_DAY
//            }
//            diff.inWholeDays >= 1 -> {
//                DiffType.DAYS
//            }
//            diff.inWholeHours >= 1 -> {
//                DiffType.HOURS
//            }
//            diff.inWholeMinutes < 1 -> {
//                DiffType.SECONDS
//            }
//            else -> {
//                DiffType.MINUTES
//            }
//        }
//    }
//
//    public enum class DiffType {
//        DAYS,
//        HOURS,
//        MINUTES,
//        SECONDS,
//        YEAR_MONTH_DAY,
//        MONTH_DAY,
//    }
}

public fun Instant.toUi(): UiDateTime = UiDateTime(this)

internal operator fun UiDateTime.compareTo(other: UiDateTime): Int = value.compareTo(other.value)
