package dev.dimension.flare.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.ui.graphics.Color

@Composable
internal expect fun isLightTheme(): Boolean

internal expect val PlatformContentColor: ProvidableCompositionLocal<Color>

internal data object PlatformTheme {
    val typography: PlatformTypography = PlatformTypography
    val shapes: PlatformShapes = PlatformShapes
    val colorScheme: PlatformColorScheme = PlatformColorScheme
}

public val DisabledAlpha: Float = 0.38f
public val MediumAlpha: Float = 0.75f
