package dev.dimension.flare.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle

internal actual object PlatformTypography {
    actual val caption: TextStyle
        @Composable
        get() = MaterialTheme.typography.bodySmall

    actual val title: TextStyle
        @Composable
        get() = MaterialTheme.typography.titleMedium
    actual val headline: TextStyle
        @Composable
        get() = MaterialTheme.typography.headlineMedium
    actual val h1: TextStyle
        @Composable
        get() = MaterialTheme.typography.headlineLarge
    actual val h2: TextStyle
        @Composable
        get() = MaterialTheme.typography.headlineMedium
    actual val h3: TextStyle
        @Composable
        get() = MaterialTheme.typography.headlineSmall
    actual val h4: TextStyle
        @Composable
        get() = MaterialTheme.typography.titleLarge
    actual val h5: TextStyle
        @Composable
        get() = MaterialTheme.typography.titleMedium
    actual val h6: TextStyle
        @Composable
        get() = MaterialTheme.typography.titleSmall
}
