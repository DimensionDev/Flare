package dev.dimension.flare.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import io.github.composefluent.FluentTheme

internal actual object PlatformColorScheme {
    actual val primary: Color
        @Composable
        @ReadOnlyComposable
        get() = FluentTheme.colors.fillAccent.default

    actual val retweetColor: Color
        @Composable
        @ReadOnlyComposable
        get() = FluentTheme.colors.fillAccent.default
    actual val primaryContainer: Color
        @Composable
        @ReadOnlyComposable
        get() = FluentTheme.colors.fillAccent.secondary
    actual val onPrimaryContainer: Color
        @Composable
        @ReadOnlyComposable
        get() = FluentTheme.colors.text.onAccent.primary
    actual val error: Color
        @Composable
        @ReadOnlyComposable
        get() = FluentTheme.colors.system.critical
    public actual val caption: Color
        @Composable
        @ReadOnlyComposable
        get() = FluentTheme.colors.text.text.tertiary
    public actual val outline: Color
        @Composable
        @ReadOnlyComposable
        get() = FluentTheme.colors.stroke.divider.default
    public actual val card: Color
        @Composable
        @ReadOnlyComposable
        get() = FluentTheme.colors.background.card.default
    public actual val cardAlt: Color
        @Composable
        @ReadOnlyComposable
        get() = FluentTheme.colors.background.card.secondary
    actual val onCard: Color
        @Composable
        @ReadOnlyComposable
        get() = FluentTheme.colors.text.text.primary

    actual val text: Color
        @Composable
        @ReadOnlyComposable
        get() = FluentTheme.colors.text.text.primary
}
