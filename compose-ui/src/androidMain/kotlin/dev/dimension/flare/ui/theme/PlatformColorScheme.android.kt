package dev.dimension.flare.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

internal actual object PlatformColorScheme {
    actual val primary: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primary
    actual val retweetColor: Color
        @Composable
        @ReadOnlyComposable
        get() = Color(0xff00ba7c)
    actual val primaryContainer: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primaryContainer
    actual val onPrimaryContainer: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onPrimaryContainer
    actual val error: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.error
    actual val caption: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.outline
    actual val outline: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.outlineVariant
    actual val card: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surface
    public actual val cardAlt: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceVariant
    actual val onCard: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onSurface

    actual val text: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onBackground
}
