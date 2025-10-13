package dev.dimension.flare.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import io.github.composefluent.FluentTheme

internal actual object PlatformTypography {
    actual val caption: TextStyle
        @Composable
        get() = FluentTheme.typography.caption

    actual val title: TextStyle
        @Composable
        get() = FluentTheme.typography.subtitle
    actual val headline: TextStyle
        @Composable
        get() = FluentTheme.typography.title
    actual val h1: TextStyle
        @Composable
        get() = FluentTheme.typography.display
    actual val h2: TextStyle
        @Composable
        get() = FluentTheme.typography.titleLarge
    actual val h3: TextStyle
        @Composable
        get() = FluentTheme.typography.title
    actual val h4: TextStyle
        @Composable
        get() = FluentTheme.typography.subtitle
    actual val h5: TextStyle
        @Composable
        get() = FluentTheme.typography.bodyStrong
    actual val h6: TextStyle
        @Composable
        get() = FluentTheme.typography.bodyLarge
}
