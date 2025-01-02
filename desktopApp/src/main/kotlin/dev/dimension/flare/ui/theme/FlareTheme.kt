package dev.dimension.flare.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import com.konyaco.fluent.ExperimentalFluentApi
import com.konyaco.fluent.FluentTheme
import com.konyaco.fluent.darkColors
import com.konyaco.fluent.lightColors
import com.mayakapps.compose.windowstyler.WindowFrameStyle
import com.mayakapps.compose.windowstyler.WindowStyleManager
import org.apache.commons.lang3.SystemUtils

@OptIn(ExperimentalFluentApi::class)
@Composable
internal fun FrameWindowScope.FlareTheme(
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
        if (SystemUtils.IS_OS_MAC) {
            LaunchedEffect(window) {
                window.rootPane.apply {
                    rootPane.putClientProperty("apple.awt.fullWindowContent", true)
                    rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
                    rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
                }
            }
        }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(FluentTheme.colors.background.mica.base)
                    .let {
                        if (SystemUtils.IS_OS_MAC) {
                            it.padding(top = 24.dp)
                        } else {
                            it
                        }
                    },
        ) {
            content.invoke()
        }
    }
}
