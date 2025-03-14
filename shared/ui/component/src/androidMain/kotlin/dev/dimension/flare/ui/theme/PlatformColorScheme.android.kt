package dev.dimension.flare.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal actual object PlatformColorScheme {
    actual val primary: Color
        @Composable
        get() = MaterialTheme.colorScheme.primary
    actual val primaryContainer: Color
        @Composable
        get() = MaterialTheme.colorScheme.primaryContainer
    actual val onPrimaryContainer: Color
        @Composable
        get() = MaterialTheme.colorScheme.onPrimaryContainer
    actual val error: Color
        @Composable
        get() = MaterialTheme.colorScheme.error
    actual val caption: Color
        @Composable
        get() = MaterialTheme.colorScheme.onSurfaceVariant
    actual val outline: Color
        @Composable
        get() = MaterialTheme.colorScheme.outlineVariant
    actual val card: Color
        @Composable
        get() = MaterialTheme.colorScheme.surface
    public actual val cardAlt: Color
        @Composable
        get() = MaterialTheme.colorScheme.surfaceVariant
}
