package dev.dimension.flare.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle

internal expect object PlatformTypography {
    @get:Composable
    val caption: TextStyle

    @get:Composable
    val title: TextStyle

    @get:Composable
    val headline: TextStyle

    @get:Composable
    val h1: TextStyle

    @get:Composable
    val h2: TextStyle

    @get:Composable
    val h3: TextStyle

    @get:Composable
    val h4: TextStyle

    @get:Composable
    val h5: TextStyle

    @get:Composable
    val h6: TextStyle
}
