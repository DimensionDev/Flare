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
    actual val h1: TextStyle
        @Composable
        get() = CupertinoTheme.typography.headline
    actual val h2: TextStyle
        @Composable
        get() = CupertinoTheme.typography.subhead
    actual val h3: TextStyle
        @Composable
        get() = CupertinoTheme.typography.largeTitle
    actual val h4: TextStyle
        @Composable
        get() = CupertinoTheme.typography.title1
    actual val h5: TextStyle
        @Composable
        get() = CupertinoTheme.typography.title2
    actual val h6: TextStyle
        @Composable
        get() = CupertinoTheme.typography.title3
}
