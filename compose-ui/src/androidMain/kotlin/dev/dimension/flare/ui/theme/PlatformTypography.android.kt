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
}
