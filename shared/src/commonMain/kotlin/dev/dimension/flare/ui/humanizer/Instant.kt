package dev.dimension.flare.ui.humanizer

import kotlin.time.Duration

private fun Int.withLeadingZero(): String =
    if (this < 10) {
        "0$this"
    } else {
        this.toString()
    }

public fun Duration.humanize(): String =
    this.toComponents { days, hours, minutes, seconds, _ ->
        buildString {
            if (days > 0) {
                append("${days.toInt().withLeadingZero()}:")
            }
            if (hours > 0) {
                append("${hours.withLeadingZero()}:")
            }
            if (minutes > 0) {
                append("${minutes.withLeadingZero()}:")
            } else {
                append("0:")
            }
            append(seconds.withLeadingZero())
        }
    }
