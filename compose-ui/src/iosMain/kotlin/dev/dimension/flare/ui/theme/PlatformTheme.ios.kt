package dev.dimension.flare.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.ui.graphics.Color
import com.slapps.cupertino.LocalContentColor
import com.slapps.cupertino.theme.CupertinoTheme

@Composable
internal actual fun isLightTheme(): Boolean = !CupertinoTheme.colorScheme.isDark

internal actual val PlatformContentColor: ProvidableCompositionLocal<Color>
    @Composable
    get() = LocalContentColor
