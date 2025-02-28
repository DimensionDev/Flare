package dev.dimension.flare.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle

internal expect object PlatformTypography {
    @get:Composable
    val caption: TextStyle

    @get:Composable
    val title: TextStyle
}
