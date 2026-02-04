package dev.dimension.flare.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.slapps.cupertino.theme.CupertinoColors
import com.slapps.cupertino.theme.CupertinoTheme
import com.slapps.cupertino.theme.systemRed

internal actual object PlatformColorScheme {
    actual val primary: Color
        @Composable
        @ReadOnlyComposable
        get() = CupertinoTheme.colorScheme.accent

    actual val retweetColor: Color
        @Composable
        @ReadOnlyComposable
        get() = CupertinoTheme.colorScheme.accent
    actual val primaryContainer: Color
        @Composable
        @ReadOnlyComposable
        get() = CupertinoTheme.colorScheme.accent
    actual val onPrimaryContainer: Color
        @Composable
        @ReadOnlyComposable
        get() = CupertinoTheme.colorScheme.label
    actual val error: Color
        @Composable
        @ReadOnlyComposable
        get() = CupertinoColors.systemRed
    actual val caption: Color
        @Composable
        @ReadOnlyComposable
        get() = CupertinoTheme.colorScheme.secondaryLabel
    actual val outline: Color
        @Composable
        @ReadOnlyComposable
        get() = CupertinoTheme.colorScheme.tertiaryLabel
    actual val card: Color
        @Composable
        @ReadOnlyComposable
        get() = CupertinoTheme.colorScheme.secondarySystemGroupedBackground
    actual val cardAlt: Color
        @Composable
        @ReadOnlyComposable
        get() = CupertinoTheme.colorScheme.tertiarySystemGroupedBackground
    actual val onCard: Color
        @Composable
        @ReadOnlyComposable
        get() = CupertinoTheme.colorScheme.label

    actual val text: Color
        @Composable
        @ReadOnlyComposable
        get() = CupertinoTheme.colorScheme.label
}
