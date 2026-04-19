package dev.dimension.flare.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

public val LocalIsLightTheme: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

@Composable
internal actual fun isLightTheme(): Boolean = LocalIsLightTheme.current

internal actual val PlatformContentColor: ProvidableCompositionLocal<Color>
    get() = LocalContentColor
