package dev.dimension.flare.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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
    actual val h1: TextStyle
        @Composable
        get() =
            TextStyle(
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
    actual val h2: TextStyle
        @Composable
        get() =
            TextStyle(
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
    actual val h3: TextStyle
        @Composable
        get() =
            TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic,
            )
    actual val h4: TextStyle
        @Composable
        get() =
            TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
    actual val h5: TextStyle
        @Composable
        get() =
            TextStyle(
                fontWeight = FontWeight.Bold,
            )
    actual val h6: TextStyle
        @Composable
        get() = FluentTheme.typography.body

    actual val body: TextStyle
        @Composable
        get() = FluentTheme.typography.body
}
