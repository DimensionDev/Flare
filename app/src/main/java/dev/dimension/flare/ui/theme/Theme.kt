package dev.dimension.flare.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
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
import androidx.navigation.NavBackStackEntry
import androidx.window.core.layout.WindowWidthSizeClass
import com.materialkolor.rememberDynamicColorScheme
import com.ramcosta.composedestinations.animations.NavHostAnimatedDestinationStyle
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.data.model.Theme
import soup.compose.material.motion.animation.materialSharedAxisXIn
import soup.compose.material.motion.animation.materialSharedAxisXOut
import soup.compose.material.motion.animation.rememberSlideDistance

private object MoreColors {
    val Gray50 = Color(0xFFFAFAFA)
    val Gray100 = Color(0xFFF5F5F5)
    val Gray200 = Color(0xFFEEEEEE)
    val Gray300 = Color(0xFFE0E0E0)
    val Gray400 = Color(0xFFBDBDBD)
    val Gray500 = Color(0xFF9E9E9E)
    val Gray600 = Color(0xFF757575)
    val Gray700 = Color(0xFF616161)
    val Gray800 = Color(0xFF424242)
    val Gray900 = Color(0xFF212121)
}

private fun ColorScheme.withPureColorLightMode(): ColorScheme =
    copy(
        background = Color.White,
        surface = Color.White,
        onBackground = Color.Black,
        onSurface = Color.Black,
        surfaceContainer = MoreColors.Gray100,
        surfaceContainerLow = MoreColors.Gray100,
        surfaceContainerHigh = MoreColors.Gray100,
        surfaceContainerLowest = MoreColors.Gray100,
        surfaceContainerHighest = MoreColors.Gray100,
        onSurfaceVariant = MoreColors.Gray800,
    )

private fun ColorScheme.withPureColorLightModeInBigScreen(): ColorScheme =
    copy(
        background = MoreColors.Gray50,
        surface = MoreColors.Gray50,
        onBackground = Color.Black,
        onSurface = Color.Black,
        surfaceContainer = Color.White,
        surfaceContainerLow = Color.White,
        surfaceContainerHigh = Color.White,
        surfaceContainerLowest = Color.White,
        surfaceContainerHighest = Color.White,
        onSurfaceVariant = Color.Black,
    )

private fun ColorScheme.withPureColorDarkMode(): ColorScheme =
    copy(
        background = Color.Black,
        surface = Color.Black,
        onBackground = Color.White,
        onSurface = Color.White,
        surfaceContainer = MoreColors.Gray900,
        surfaceContainerLow = MoreColors.Gray900,
        surfaceContainerHigh = MoreColors.Gray900,
        surfaceContainerLowest = MoreColors.Gray900,
        surfaceContainerHighest = MoreColors.Gray900,
        onSurfaceVariant = MoreColors.Gray400,
    )

@Composable
fun FlareTheme(
    darkTheme: Boolean = isDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = LocalAppearanceSettings.current.dynamicTheme,
    content: @Composable () -> Unit,
) {
    val seed = Color(LocalAppearanceSettings.current.colorSeed)
    val pureColorMode = LocalAppearanceSettings.current.pureColorMode
    val windowInfo = currentWindowAdaptiveInfo()
    val bigScreen = windowInfo.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED
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
                                    it.withPureColorDarkMode()
                                } else {
                                    it
                                }
                            }
                    } else {
                        dynamicLightColorScheme(context)
                            .let {
                                if (pureColorMode) {
                                    if (bigScreen) {
                                        it.withPureColorLightModeInBigScreen()
                                    } else {
                                        it.withPureColorLightMode()
                                    }
                                } else {
                                    it
                                }
                            }
                    }
                }
            }

            darkTheme ->
                rememberDynamicColorScheme(
                    seed,
                    isDark = true,
                    isAmoled = pureColorMode,
                    modifyColorScheme = {
                        if (pureColorMode) {
                            it.withPureColorDarkMode()
                        } else {
                            it
                        }
                    },
                )

            else ->
                rememberDynamicColorScheme(
                    seed,
                    isDark = false,
                    isAmoled = pureColorMode,
                    modifyColorScheme = {
                        if (pureColorMode) {
                            it.withPureColorLightMode()
                        } else {
                            it
                        }
                    },
                )
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

@Composable
internal fun rememberNavAnimX(): NavHostAnimatedDestinationStyle {
    val slideDistance = rememberSlideDistance()
    return remember(slideDistance) {
        object : NavHostAnimatedDestinationStyle() {
            override val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
                materialSharedAxisXIn(true, slideDistance)
            }
            override val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
                materialSharedAxisXOut(true, slideDistance)
            }
            override val popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
                materialSharedAxisXIn(false, slideDistance)
            }
            override val popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
                materialSharedAxisXOut(false, slideDistance)
            }
        }
    }
}
