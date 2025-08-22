package dev.dimension.flare.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal expect object PlatformColorScheme {
    @get:Composable
    val primary: Color

    @get:Composable
    val primaryContainer: Color

    @get:Composable
    val onPrimaryContainer: Color

    @get:Composable
    val error: Color

    @get:Composable
    val caption: Color

    @get:Composable
    val outline: Color

    @get:Composable
    val card: Color

    @get:Composable
    val cardAlt: Color

    @get:Composable
    val onCard: Color
}
