package dev.dimension.flare.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal expect object PlatformColorScheme {
    @get:Composable
    val primary: Color

    @get:Composable
    val error: Color

    @get:Composable
    val caption: Color

    @get:Composable
    val outline: Color
}
