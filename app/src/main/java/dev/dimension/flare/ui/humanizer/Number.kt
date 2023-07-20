package dev.dimension.flare.ui.humanizer

import java.math.BigDecimal
import java.math.RoundingMode


fun Float.humanizePercentage(): String {
    return "${BigDecimal(this.times(100).toDouble()).setScale(0, RoundingMode.HALF_UP)}%"
}