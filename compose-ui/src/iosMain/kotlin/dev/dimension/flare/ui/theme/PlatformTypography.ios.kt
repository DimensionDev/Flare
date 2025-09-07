package dev.dimension.flare.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import com.slapps.cupertino.theme.CupertinoTheme

internal actual object PlatformTypography {
    actual val caption: TextStyle
        @Composable
        get() = CupertinoTheme.typography.caption1
    actual val title: TextStyle
        @Composable
        get() = CupertinoTheme.typography.title3
    actual val headline: TextStyle
        @Composable
        get() = CupertinoTheme.typography.headline
}