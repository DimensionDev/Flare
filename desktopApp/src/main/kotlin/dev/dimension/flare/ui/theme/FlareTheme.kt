package dev.dimension.flare.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.WindowScope
import com.konyaco.fluent.ExperimentalFluentApi
import com.konyaco.fluent.FluentTheme
import com.konyaco.fluent.darkColors
import com.konyaco.fluent.lightColors
import com.mayakapps.compose.windowstyler.WindowFrameStyle
import com.mayakapps.compose.windowstyler.WindowStyleManager

@OptIn(ExperimentalFluentApi::class)
@Composable
internal fun WindowScope.FlareTheme(
    isDarkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    FluentTheme(
        compactMode = false,
        colors =
            if (isDarkTheme) {
                darkColors()
            } else {
                lightColors()
            },
    ) {
        val micaBase = FluentTheme.colors.background.mica.base
        LaunchedEffect(window, isDarkTheme) {
            WindowStyleManager(
                window = window,
                isDarkTheme = isDarkTheme,
                frameStyle =
                    WindowFrameStyle(
                        titleBarColor = micaBase,
                    ),
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(FluentTheme.colors.background.mica.base),
        ) {
            content.invoke()
        }
    }
}
