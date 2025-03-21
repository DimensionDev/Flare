package dev.dimension.flare.ui.humanizer

import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round

internal fun Float.humanizePercentage(): String {
    val roundedNumber = round(this * 100 * 100).toDouble() / 100
    return "$roundedNumber%"
}

internal fun Long.humanize(digitPosition: Int = 1): String =
    when {
        this < 0 -> "-" + (-this).humanize(digitPosition)
        this == 0L -> "0"
        this in 1..9999 -> this.toString()
        this in 10000..999999 -> {
            val k = this / 1000
            val m = ((this % 1000) / 10.0.pow(max(0, 3 - digitPosition))).toInt()
            if (m > 0) {
                "$k.${m}k"
            } else {
                "${k}k"
            }
        }
        this in 1000000..999999999 -> {
            val m = this / 1000000
            val k = ((this % 1000000) / 10.0.pow(max(0, 6 - digitPosition))).toInt()
            if (k > 0) {
                "$m.${k}m"
            } else {
                "${m}m"
            }
        }
        else -> {
            val b = this / 1000000000
            val m = ((this % 1000000000) / 10.0.pow(max(0, 9 - digitPosition))).toInt()
            if (m > 0) {
                "$b.${m}b"
            } else {
                "${b}b"
            }
        }
    }
