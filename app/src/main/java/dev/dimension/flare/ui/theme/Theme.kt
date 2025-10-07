package dev.dimension.flare.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.data.model.Theme

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
    val Gray900 = Color(0xFF141414)
}

private fun ColorScheme.withPureColorLightMode(): ColorScheme =
    copy(
        background = MoreColors.Gray100,
        surface = Color.White,
        onBackground = Color.Black,
        onSurface = Color.Black,
        surfaceContainer = Color.White,
        surfaceContainerLow = MoreColors.Gray100,
        surfaceContainerHigh = Color.White,
        surfaceContainerLowest = Color.White,
        surfaceContainerHighest = Color.White,
        onSurfaceVariant = MoreColors.Gray800,
        outlineVariant = MoreColors.Gray400,
    )

private fun ColorScheme.withPureColorDarkMode(): ColorScheme =
    copy(
        background = Color.Black,
        surface = MoreColors.Gray900,
        onBackground = Color.White,
        onSurface = Color.White,
        surfaceContainer = MoreColors.Gray900,
        surfaceContainerLow = Color.Black,
        surfaceContainerHigh = MoreColors.Gray900,
        surfaceContainerLowest = MoreColors.Gray900,
        surfaceContainerHighest = MoreColors.Gray900,
        onSurfaceVariant = MoreColors.Gray400,
        outlineVariant = MoreColors.Gray800,
    )

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FlareTheme(
    darkTheme: Boolean = isDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = LocalAppearanceSettings.current.dynamicTheme,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val fontSizeDiff = LocalAppearanceSettings.current.fontSizeDiff
    val lineHeightDiff = LocalAppearanceSettings.current.lineHeightDiff
    val seed = Color(LocalAppearanceSettings.current.colorSeed)
    val pureColorMode = LocalAppearanceSettings.current.pureColorMode
    val colorScheme =
        if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            remember(
                darkTheme,
                pureColorMode,
            ) {
                if (darkTheme) {
                    dynamicDarkColorScheme(context)
                } else {
                    dynamicLightColorScheme(context)
                }
            }
        } else {
            rememberDynamicColorScheme(
                seedColor = seed,
                isAmoled = pureColorMode,
                specVersion = ColorSpec.SpecVersion.SPEC_2025,
                style = PaletteStyle.Expressive,
                isDark = darkTheme,
            )
        }.let {
            remember(it) {
                if (pureColorMode) {
                    if (!darkTheme) {
                        it.withPureColorLightMode()
                    } else {
                        it.withPureColorDarkMode()
                    }
                } else {
                    if (darkTheme) {
                        it.copy(
                            background = it.surfaceContainer,
                            surface = it.surfaceBright,
                            surfaceContainer = it.surfaceContainerHighest,
                        )
                    } else {
                        it.copy(
                            background = it.surfaceContainer,
                            surfaceContainer = it.surfaceContainerHighest,
                        )
                    }
                }
            }
        }
    val view = LocalView.current
    if (!view.isInEditMode && view.context is Activity) {
        SideEffect {
            val window = (view.context as Activity).window
//            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
        val actualDarkTheme = isDarkTheme()
        if (darkTheme != actualDarkTheme) {
            DisposableEffect(Unit) {
                onDispose {
                    val window = (view.context as Activity).window
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !actualDarkTheme
                        isAppearanceLightNavigationBars = !actualDarkTheme
                    }
                }
            }
        }
    }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography =
            remember(fontSizeDiff, lineHeightDiff) {
                typography(fontSizeDiff, lineHeightDiff)
            },
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun typography(
    fontDiff: Float,
    lineHeightDiff: Float,
): Typography =
    Typography().let {
        if (fontDiff == 0f && lineHeightDiff == 0f) {
            it
        } else {
            it.copy(
                displayLarge =
                    it.displayLarge.copy(
                        fontSize = (it.displayLarge.fontSize.value + fontDiff).sp,
                        lineHeight = (it.displayLarge.lineHeight.value + lineHeightDiff).sp,
                    ),
                displayMedium =
                    it.displayMedium.copy(
                        fontSize = (it.displayMedium.fontSize.value + fontDiff).sp,
                        lineHeight = (it.displayMedium.lineHeight.value + lineHeightDiff).sp,
                    ),
                displaySmall =
                    it.displaySmall.copy(
                        fontSize = (it.displaySmall.fontSize.value + fontDiff).sp,
                        lineHeight = (it.displaySmall.lineHeight.value + lineHeightDiff).sp,
                    ),
                headlineLarge =
                    it.headlineLarge.copy(
                        fontSize = (it.headlineLarge.fontSize.value + fontDiff).sp,
                        lineHeight = (it.headlineLarge.lineHeight.value + lineHeightDiff).sp,
                    ),
                headlineMedium =
                    it.headlineMedium.copy(
                        fontSize = (it.headlineMedium.fontSize.value + fontDiff).sp,
                        lineHeight = (it.headlineMedium.lineHeight.value + lineHeightDiff).sp,
                    ),
                headlineSmall =
                    it.headlineSmall.copy(
                        fontSize = (it.headlineSmall.fontSize.value + fontDiff).sp,
                        lineHeight = (it.headlineSmall.lineHeight.value + lineHeightDiff).sp,
                    ),
                titleLarge =
                    it.titleLarge.copy(
                        fontSize = (it.titleLarge.fontSize.value + fontDiff).sp,
                        lineHeight = (it.titleLarge.lineHeight.value + lineHeightDiff).sp,
                    ),
                titleMedium =
                    it.titleMedium.copy(
                        fontSize = (it.titleMedium.fontSize.value + fontDiff).sp,
                        lineHeight = (it.titleMedium.lineHeight.value + lineHeightDiff).sp,
                    ),
                titleSmall =
                    it.titleSmall.copy(
                        fontSize = (it.titleSmall.fontSize.value + fontDiff).sp,
                        lineHeight = (it.titleSmall.lineHeight.value + lineHeightDiff).sp,
                    ),
                bodyLarge =
                    it.bodyLarge.copy(
                        fontSize = (it.bodyLarge.fontSize.value + fontDiff).sp,
                        lineHeight = (it.bodyLarge.lineHeight.value + lineHeightDiff).sp,
                    ),
                bodyMedium =
                    it.bodyMedium.copy(
                        fontSize = (it.bodyMedium.fontSize.value + fontDiff).sp,
                        lineHeight = (it.bodyMedium.lineHeight.value + lineHeightDiff).sp,
                    ),
                bodySmall =
                    it.bodySmall.copy(
                        fontSize = (it.bodySmall.fontSize.value + fontDiff).sp,
                        lineHeight = (it.bodySmall.lineHeight.value + lineHeightDiff).sp,
                    ),
                labelLarge =
                    it.labelLarge.copy(
                        fontSize = (it.labelLarge.fontSize.value + fontDiff).sp,
                        lineHeight = (it.labelLarge.lineHeight.value + lineHeightDiff).sp,
                    ),
                labelMedium =
                    it.labelMedium.copy(
                        fontSize = (it.labelMedium.fontSize.value + fontDiff).sp,
                        lineHeight = (it.labelMedium.lineHeight.value + lineHeightDiff).sp,
                    ),
                labelSmall =
                    it.labelSmall.copy(
                        fontSize = (it.labelSmall.fontSize.value + fontDiff).sp,
                        lineHeight = (it.labelSmall.lineHeight.value + lineHeightDiff).sp,
                    ),
                displayLargeEmphasized =
                    it.displayLargeEmphasized.copy(
                        fontSize = (it.displayLargeEmphasized.fontSize.value + fontDiff).sp,
                        lineHeight = (it.displayLargeEmphasized.lineHeight.value + lineHeightDiff).sp,
                    ),
                displayMediumEmphasized =
                    it.displayMediumEmphasized.copy(
                        fontSize = (it.displayMediumEmphasized.fontSize.value + fontDiff).sp,
                        lineHeight = (it.displayMediumEmphasized.lineHeight.value + lineHeightDiff).sp,
                    ),
                displaySmallEmphasized =
                    it.displaySmallEmphasized.copy(
                        fontSize = (it.displaySmallEmphasized.fontSize.value + fontDiff).sp,
                        lineHeight = (it.displaySmallEmphasized.lineHeight.value + lineHeightDiff).sp,
                    ),
                headlineLargeEmphasized =
                    it.headlineLargeEmphasized.copy(
                        fontSize = (it.headlineLargeEmphasized.fontSize.value + fontDiff).sp,
                        lineHeight = (it.headlineLargeEmphasized.lineHeight.value + lineHeightDiff).sp,
                    ),
                headlineMediumEmphasized =
                    it.headlineMediumEmphasized.copy(
                        fontSize = (it.headlineMediumEmphasized.fontSize.value + fontDiff).sp,
                        lineHeight = (it.headlineMediumEmphasized.lineHeight.value + lineHeightDiff).sp,
                    ),
                headlineSmallEmphasized =
                    it.headlineSmallEmphasized.copy(
                        fontSize = (it.headlineSmallEmphasized.fontSize.value + fontDiff).sp,
                        lineHeight = (it.headlineSmallEmphasized.lineHeight.value + lineHeightDiff).sp,
                    ),
                titleLargeEmphasized =
                    it.titleLargeEmphasized.copy(
                        fontSize = (it.titleLargeEmphasized.fontSize.value + fontDiff).sp,
                        lineHeight = (it.titleLargeEmphasized.lineHeight.value + lineHeightDiff).sp,
                    ),
                titleMediumEmphasized =
                    it.titleMediumEmphasized.copy(
                        fontSize = (it.titleMediumEmphasized.fontSize.value + fontDiff).sp,
                        lineHeight = (it.titleMediumEmphasized.lineHeight.value + lineHeightDiff).sp,
                    ),
                titleSmallEmphasized =
                    it.titleSmallEmphasized
                        .copy(
                            fontSize = (it.titleSmallEmphasized.fontSize.value + fontDiff).sp,
                            lineHeight = (it.titleSmallEmphasized.lineHeight.value + lineHeightDiff).sp,
                        ),
                bodyLargeEmphasized =
                    it.bodyLargeEmphasized
                        .copy(
                            fontSize = (it.bodyLargeEmphasized.fontSize.value + fontDiff).sp,
                            lineHeight = (it.bodyLargeEmphasized.lineHeight.value + lineHeightDiff).sp,
                        ),
                bodyMediumEmphasized =
                    it.bodyMediumEmphasized
                        .copy(
                            fontSize = (it.bodyMediumEmphasized.fontSize.value + fontDiff).sp,
                            lineHeight = (it.bodyMediumEmphasized.lineHeight.value + lineHeightDiff).sp,
                        ),
                bodySmallEmphasized =
                    it.bodySmallEmphasized
                        .copy(
                            fontSize = (it.bodySmallEmphasized.fontSize.value + fontDiff).sp,
                            lineHeight = (it.bodySmallEmphasized.lineHeight.value + lineHeightDiff).sp,
                        ),
                labelLargeEmphasized =
                    it.labelLargeEmphasized
                        .copy(
                            fontSize = (it.labelLargeEmphasized.fontSize.value + fontDiff).sp,
                            lineHeight = (it.labelLargeEmphasized.lineHeight.value + lineHeightDiff).sp,
                        ),
                labelMediumEmphasized =
                    it.labelMediumEmphasized
                        .copy(
                            fontSize = (it.labelMediumEmphasized.fontSize.value + fontDiff).sp,
                            lineHeight = (it.labelMediumEmphasized.lineHeight.value + lineHeightDiff).sp,
                        ),
                labelSmallEmphasized =
                    it.labelSmallEmphasized
                        .copy(
                            fontSize = (it.labelSmallEmphasized.fontSize.value + fontDiff).sp,
                            lineHeight = (it.labelSmallEmphasized.lineHeight.value + lineHeightDiff).sp,
                        ),
            )
        }
    }
