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
}
