package dev.dimension.flare.ui.render

import androidx.compose.ui.unit.LayoutDirection

val UiRichText.direction: LayoutDirection
    get() = if (isRTL) LayoutDirection.Rtl else LayoutDirection.Ltr
