package dev.dimension.flare.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.materialkolor.rememberDynamicColorScheme
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.data.model.Theme

@Composable
fun FlareTheme(
    darkTheme: Boolean = isDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = LocalAppearanceSettings.current.dynamicTheme,
    content: @Composable () -> Unit,
) {
    val seed = Color(LocalAppearanceSettings.current.colorSeed)
    val pureColorMode = LocalAppearanceSettings.current.pureColorMode
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                remember(
                    darkTheme,
                    pureColorMode,
                ) {
                    if (darkTheme) {
                        dynamicDarkColorScheme(context)
                            .let {
                                if (pureColorMode) {
                                    it.copy(
                                        background = Color.Black,
                                        surface = Color.Black,
                                        onBackground = Color.White,
                                        onSurface = Color.White,
                                    )
                                } else {
                                    it
                                }
                            }
                    } else {
                        dynamicLightColorScheme(context)
                            .let {
                                if (pureColorMode) {
                                    it.copy(
                                        background = Color.White,
                                        surface = Color.White,
                                        onBackground = Color.Black,
                                        onSurface = Color.Black,
                                    )
                                } else {
                                    it
                                }
                            }
                    }
                }
            }

            darkTheme -> rememberDynamicColorScheme(seed, isDark = true, isAmoled = pureColorMode)
            else -> rememberDynamicColorScheme(seed, isDark = false, isAmoled = pureColorMode)
        }
    val view = LocalView.current
    if (!view.isInEditMode && view.context is Activity) {
        SideEffect {
            val window = (view.context as Activity).window
//            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars =
                !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = {
            content.invoke()
        },
    )
}

@Composable
private fun isDarkTheme(): Boolean =
    LocalAppearanceSettings.current.theme == Theme.DARK ||
        (LocalAppearanceSettings.current.theme == Theme.SYSTEM && isSystemInDarkTheme())

@Composable
fun ColorScheme.isLight() = this.background.luminance() > 0.5
