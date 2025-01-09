package dev.dimension.flare.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.konyaco.fluent.FluentTheme

internal actual object PlatformColorScheme {
    actual val primary: Color
        @Composable
        get() = FluentTheme.colors.fillAccent.default
    actual val error: Color
        @Composable
        get() = FluentTheme.colors.system.criticalBackground
    public actual val caption: Color
        @Composable
        get() = FluentTheme.colors.text.text.tertiary
}
