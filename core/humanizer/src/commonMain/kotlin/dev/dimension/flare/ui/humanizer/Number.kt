package dev.dimension.flare.ui.humanizer

import kotlin.math.round

public fun Float.humanizePercentage(): String {
    val roundedNumber = round(this * 100 * 100).toDouble() / 100
    return "$roundedNumber%"
}
