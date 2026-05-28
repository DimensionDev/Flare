@file:OptIn(FormatStringsInDatetimeFormats::class)

package dev.dimension.flare.ui.model

import androidx.compose.ui.unit.LayoutDirection
import dev.dimension.flare.ui.render.UiRichText
import kotlinx.datetime.format.FormatStringsInDatetimeFormats

public val UiRichText.direction: LayoutDirection
    get() =
        if (isRtl) {
            LayoutDirection.Rtl
        } else {
            LayoutDirection.Ltr
        }
