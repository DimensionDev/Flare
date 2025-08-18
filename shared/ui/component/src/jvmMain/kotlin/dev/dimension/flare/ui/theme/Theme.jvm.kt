package dev.dimension.flare.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.ui.graphics.Color
import io.github.composefluent.FluentTheme
import io.github.composefluent.LocalContentColor

@Composable
internal actual fun isLightTheme(): Boolean = !FluentTheme.colors.darkMode

internal actual val PlatformContentColor: ProvidableCompositionLocal<Color>
    get() = LocalContentColor
