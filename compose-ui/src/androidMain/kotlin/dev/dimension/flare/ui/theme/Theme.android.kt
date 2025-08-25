package dev.dimension.flare.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

@Composable
internal actual fun isLightTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() > 0.5

internal actual val PlatformContentColor: ProvidableCompositionLocal<Color>
    get() = LocalContentColor
