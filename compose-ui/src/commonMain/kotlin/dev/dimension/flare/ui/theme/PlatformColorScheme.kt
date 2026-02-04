package dev.dimension.flare.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

internal expect object PlatformColorScheme {
    @get:Composable
    @get:ReadOnlyComposable
    val primary: Color

    @get:Composable
    @get:ReadOnlyComposable
    val retweetColor: Color

    @get:Composable
    @get:ReadOnlyComposable
    val primaryContainer: Color

    @get:Composable
    @get:ReadOnlyComposable
    val onPrimaryContainer: Color

    @get:Composable
    @get:ReadOnlyComposable
    val error: Color

    @get:Composable
    @get:ReadOnlyComposable
    val caption: Color

    @get:Composable
    @get:ReadOnlyComposable
    val outline: Color

    @get:Composable
    @get:ReadOnlyComposable
    val card: Color

    @get:Composable
    @get:ReadOnlyComposable
    val cardAlt: Color

    @get:Composable
    @get:ReadOnlyComposable
    val onCard: Color

    @get:Composable
    @get:ReadOnlyComposable
    val text: Color
}
