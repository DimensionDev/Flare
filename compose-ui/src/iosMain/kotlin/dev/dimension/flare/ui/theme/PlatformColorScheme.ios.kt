package dev.dimension.flare.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.slapps.cupertino.theme.CupertinoColors
import com.slapps.cupertino.theme.CupertinoTheme
import com.slapps.cupertino.theme.systemRed

internal actual object PlatformColorScheme {
    actual val primary: Color
        @Composable
        get() = CupertinoTheme.colorScheme.accent
    actual val primaryContainer: Color
        @Composable
        get() = CupertinoTheme.colorScheme.accent
    actual val onPrimaryContainer: Color
        @Composable
        get() = CupertinoTheme.colorScheme.label
    actual val error: Color
        @Composable
        get() = CupertinoColors.systemRed
    actual val caption: Color
        @Composable
        get() = CupertinoTheme.colorScheme.secondaryLabel
    actual val outline: Color
        @Composable
        get() = CupertinoTheme.colorScheme.tertiaryLabel
    actual val card: Color
        @Composable
        get() = CupertinoTheme.colorScheme.systemBackground
    actual val cardAlt: Color
        @Composable
        get() = CupertinoTheme.colorScheme.secondarySystemBackground
    actual val onCard: Color
        @Composable
        get() = CupertinoTheme.colorScheme.label
}