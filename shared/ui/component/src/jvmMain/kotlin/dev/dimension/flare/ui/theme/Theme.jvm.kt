package dev.dimension.flare.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.ui.graphics.Color
import com.konyaco.fluent.FluentTheme
import com.konyaco.fluent.LocalContentColor

@Composable
internal actual fun isLightTheme(): Boolean = !FluentTheme.colors.darkMode

internal actual val PlatformContentColor: ProvidableCompositionLocal<Color>
    get() = LocalContentColor
