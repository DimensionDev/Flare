package dev.dimension.flare.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.LayoutDirection

operator fun PaddingValues.plus(contentPadding: PaddingValues): PaddingValues {
    return AdditionalPaddingValues(
        this,
        contentPadding
    )
}

@Stable
private class AdditionalPaddingValues(
    private val base: PaddingValues,
    private val additional: PaddingValues
) : PaddingValues {

    override fun calculateLeftPadding(layoutDirection: LayoutDirection) =
        base.calculateLeftPadding(layoutDirection) + additional.calculateLeftPadding(layoutDirection)

    override fun calculateTopPadding() =
        base.calculateTopPadding() + additional.calculateTopPadding()

    override fun calculateRightPadding(layoutDirection: LayoutDirection) =
        base.calculateRightPadding(layoutDirection) + additional.calculateRightPadding(layoutDirection)

    override fun calculateBottomPadding() =
        base.calculateBottomPadding() + additional.calculateBottomPadding()

    override fun toString(): String = "AdditionalPaddingValues(base=$base, " +
        "additional=$additional)"

    override fun equals(other: Any?): Boolean = this === other ||
        other is AdditionalPaddingValues && base == other.base &&
        additional == other.additional

    override fun hashCode(): Int {
        var result = base.hashCode()
        result = 31 * result + additional.hashCode()
        return result
    }
}
