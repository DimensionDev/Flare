package dev.dimension.flare.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowScope
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.data.model.AppearanceSettings
import dev.dimension.flare.data.model.AvatarShape
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.data.model.Theme
import dev.dimension.flare.data.model.VideoAutoplay
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.ui.component.ComponentAppearance
import dev.dimension.flare.ui.component.LocalComponentAppearance
import dev.dimension.flare.ui.component.platform.LocalWifiState
import dev.dimension.flare.ui.humanizer.updateTimeFormatterLocale
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.onSuccess
import io.github.composefluent.Colors
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.FluentTheme
import io.github.composefluent.Shades
import io.github.kdroidfilter.nucleus.darkmodedetector.isSystemInDarkMode
import io.github.kdroidfilter.nucleus.systemcolor.systemAccentColor
import io.github.kdroidfilter.nucleus.window.DecoratedWindowDefaults
import io.github.kdroidfilter.nucleus.window.NucleusDecoratedWindowTheme
import io.github.kdroidfilter.nucleus.window.styling.LocalTitleBarStyle
import kotlinx.coroutines.launch
import org.apache.commons.lang3.SystemUtils
import org.koin.compose.koinInject
import java.util.Locale

internal val LocalComposeWindow =
    staticCompositionLocalOf<ComposeWindow?> {
        error("No ComposeWindow provided")
    }

@OptIn(ExperimentalFluentApi::class)
@Composable
internal fun FlareTheme(
    isDarkTheme: Boolean = isDarkTheme(),
    content: @Composable () -> Unit,
) {
    val accentColor = systemAccentColor() ?: Color(0xFF00F5F0)
    FluentTheme(
        compactMode = false,
        colors =
            Colors(
                remember(
                    accentColor,
                ) {
                    generateFluentShades(accentColor)
                },
                darkMode = isDarkTheme,
            ),
    ) {
        CompositionLocalProvider(
            LocalIndication provides
                FluentIndication(
                    hover = Color.Transparent,
                    pressed = Color.Transparent,
                ),
        ) {
            Box(
                modifier =
                    Modifier
                        .background(FluentTheme.colors.background.mica.base),
            ) {
                content.invoke()
            }
        }
    }
}

private fun generateFluentShades(accentColor: Color): Shades {
    val accentHsl = accentColor.toHsl()
    val isNeutral = accentHsl.saturation <= 0.08f
    val hueScale = if (isNeutral) 0f else 1f

    return Shades(
        base = accentColor,
        light1 =
            accentHsl
                .shifted(
                    hue = -1.5f * hueScale,
                    saturation = 0f,
                    lightness = accentHsl.lightness + 0.07f,
                ).toColor(),
        light2 =
            accentHsl
                .shifted(
                    hue = -7f * hueScale,
                    saturation = -0.12f,
                    lightness = accentHsl.lightness + 0.27f,
                ).toColor(),
        light3 =
            accentHsl
                .shifted(
                    hue = -15f * hueScale,
                    saturation = -0.24f,
                    lightness = accentHsl.lightness + 0.38f,
                ).toColor(),
        dark1 =
            accentHsl
                .shifted(
                    hue = 3f * hueScale,
                    saturation = 0.02f,
                    lightness = accentHsl.lightness - 0.06f,
                ).toColor(),
        dark2 =
            accentHsl
                .shifted(
                    hue = 9f * hueScale,
                    saturation = 0.04f,
                    lightness = accentHsl.lightness - 0.13f,
                ).toColor(),
        dark3 =
            accentHsl
                .shifted(
                    hue = 20f * hueScale,
                    saturation = 0.06f,
                    lightness = accentHsl.lightness - dark3LightnessDrop(accentColor),
                ).toColor(),
    )
}

private fun dark3LightnessDrop(color: Color): Float =
    if (color.luminance() < 0.08f) {
        0.12f
    } else {
        0.21f
    }

private data class HslColor(
    val hue: Float,
    val saturation: Float,
    val lightness: Float,
)

private fun Color.toHsl(): HslColor {
    val red = red
    val green = green
    val blue = blue
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val lightness = (max + min) / 2f
    val delta = max - min

    if (delta == 0f) {
        return HslColor(
            hue = 0f,
            saturation = 0f,
            lightness = lightness,
        )
    }

    val saturation =
        if (lightness > 0.5f) {
            delta / (2f - max - min)
        } else {
            delta / (max + min)
        }

    val rawHue =
        when (max) {
            red -> ((green - blue) / delta).mod(6f)
            green -> ((blue - red) / delta) + 2f
            else -> ((red - green) / delta) + 4f
        }

    return HslColor(
        hue = (rawHue * 60f).normalizeHue(),
        saturation = saturation.coerceIn(0f, 1f),
        lightness = lightness.coerceIn(0f, 1f),
    )
}

private fun HslColor.shifted(
    hue: Float = 0f,
    saturation: Float = 0f,
    lightness: Float = this.lightness,
): HslColor =
    HslColor(
        hue = (this.hue + hue).normalizeHue(),
        saturation = (this.saturation + saturation).coerceIn(0f, 1f),
        lightness = lightness.coerceIn(0f, 1f),
    )

private fun HslColor.toColor(): Color {
    if (saturation == 0f) {
        return Color(lightness, lightness, lightness, 1f)
    }

    val chroma = (1f - kotlin.math.abs(2f * lightness - 1f)) * saturation
    val hueSection = hue / 60f
    val secondComponent = chroma * (1f - kotlin.math.abs(hueSection.mod(2f) - 1f))
    val match = lightness - chroma / 2f

    val (redPrime, greenPrime, bluePrime) =
        when {
            hueSection < 1f -> Triple(chroma, secondComponent, 0f)
            hueSection < 2f -> Triple(secondComponent, chroma, 0f)
            hueSection < 3f -> Triple(0f, chroma, secondComponent)
            hueSection < 4f -> Triple(0f, secondComponent, chroma)
            hueSection < 5f -> Triple(secondComponent, 0f, chroma)
            else -> Triple(chroma, 0f, secondComponent)
        }

    return Color(
        red = (redPrime + match).coerceIn(0f, 1f),
        green = (greenPrime + match).coerceIn(0f, 1f),
        blue = (bluePrime + match).coerceIn(0f, 1f),
        alpha = 1f,
    )
}

private fun Float.normalizeHue(): Float {
    val normalized = this % 360f
    return if (normalized < 0f) normalized + 360f else normalized
}

@Composable
internal fun WindowScope.ProvideComposeWindow(content: @Composable () -> Unit) {
    if (SystemUtils.IS_OS_MAC) {
        LaunchedEffect(window) {
            window
                .let {
                    it as? javax.swing.RootPaneContainer
                }?.let {
                    it.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
                    it.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
                    it.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
                }
        }
    }
    val composeWindow =
        if (this is FrameWindowScope) {
            window
        } else {
            null
        }
    CompositionLocalProvider(
        LocalComposeWindow provides composeWindow,
        LocalWindowPadding provides
            PaddingValues(
                start = 0.dp,
                top = LocalTitleBarStyle.current.metrics.height + 8.dp,
                end = 0.dp,
                bottom = 8.dp,
            ),
    ) {
        content.invoke()
    }
}

private class FluentIndication(
    private val hover: Color,
    private val pressed: Color,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        DefaultDebugIndicationInstance(
            interactionSource = interactionSource,
            hover = hover,
            pressed = pressed,
        )

    override fun hashCode(): Int = hover.hashCode() + pressed.hashCode()

    override fun equals(other: Any?) = other === this

    private class DefaultDebugIndicationInstance(
        private val interactionSource: InteractionSource,
        private val hover: Color,
        private val pressed: Color,
    ) : Modifier.Node(),
        DrawModifierNode {
        private var isPressed = false
        private var isHovered = false
        private var isFocused = false

        override fun onAttach() {
            coroutineScope.launch {
                var pressCount = 0
                var hoverCount = 0
                var focusCount = 0
                interactionSource.interactions.collect { interaction ->
                    when (interaction) {
                        is PressInteraction.Press -> pressCount++
                        is PressInteraction.Release -> pressCount--
                        is PressInteraction.Cancel -> pressCount--
                        is HoverInteraction.Enter -> hoverCount++
                        is HoverInteraction.Exit -> hoverCount--
                        is FocusInteraction.Focus -> focusCount++
                        is FocusInteraction.Unfocus -> focusCount--
                    }
                    val pressed = pressCount > 0
                    val hovered = hoverCount > 0
                    val focused = focusCount > 0
                    var invalidateNeeded = false
                    if (isPressed != pressed) {
                        isPressed = pressed
                        invalidateNeeded = true
                    }
                    if (isHovered != hovered) {
                        isHovered = hovered
                        invalidateNeeded = true
                    }
                    if (isFocused != focused) {
                        isFocused = focused
                        invalidateNeeded = true
                    }
                    if (invalidateNeeded) invalidateDraw()
                }
            }
        }

        override fun ContentDrawScope.draw() {
            drawContent()
            if (isPressed) {
                drawRect(color = pressed, size = size)
            } else if (isHovered || isFocused) {
                drawRect(color = hover, size = size)
            }
        }
    }
}

@Composable
private fun isDarkTheme(): Boolean =
    LocalAppearanceSettings.current.theme == Theme.DARK ||
        (LocalAppearanceSettings.current.theme == Theme.SYSTEM && isSystemInDarkMode())

@Composable
internal fun ProvideThemeSettings(content: @Composable () -> Unit) {
    val settingsRepository = koinInject<SettingsRepository>()
    val appearanceSettings by settingsRepository.appearanceSettings.collectAsState(
        AppearanceSettings(),
    )
    val appSettings by settingsRepository.appSettings.collectAsUiState()
    appSettings.onSuccess { appSettings ->
        LaunchedEffect(appSettings.language) {
            if (appSettings.language.isNotEmpty()) {
                val locale = Locale.forLanguageTag(appSettings.language)
                Locale.setDefault(locale)
                updateTimeFormatterLocale(locale)
            }
        }
        CompositionLocalProvider(
            LocalAppearanceSettings provides appearanceSettings,
            LocalComponentAppearance provides
                remember(appearanceSettings, appSettings.translateConfig, appSettings.aiConfig.tldr) {
                    ComponentAppearance(
                        dynamicTheme = appearanceSettings.dynamicTheme,
                        avatarShape =
                            when (appearanceSettings.avatarShape) {
                                AvatarShape.CIRCLE -> ComponentAppearance.AvatarShape.CIRCLE
                                AvatarShape.SQUARE -> ComponentAppearance.AvatarShape.SQUARE
                            },
                        showNumbers = appearanceSettings.showNumbers,
                        showLinkPreview = appearanceSettings.showLinkPreview,
                        showMedia = appearanceSettings.showMedia,
                        showSensitiveContent = appearanceSettings.showSensitiveContent,
                        videoAutoplay =
                            when (appearanceSettings.videoAutoplay) {
                                VideoAutoplay.ALWAYS -> ComponentAppearance.VideoAutoplay.ALWAYS
                                VideoAutoplay.WIFI -> ComponentAppearance.VideoAutoplay.NEVER
                                VideoAutoplay.NEVER -> ComponentAppearance.VideoAutoplay.NEVER
                            },
                        expandMediaSize = appearanceSettings.expandMediaSize,
                        compatLinkPreview = appearanceSettings.compatLinkPreview,
                        aiConfig =
                            ComponentAppearance.AiConfig(
                                translation = true,
                                tldr = appSettings.aiConfig.tldr,
                            ),
                        fullWidthPost = appearanceSettings.fullWidthPost,
                        postActionStyle = appearanceSettings.postActionStyle,
                        absoluteTimestamp = appearanceSettings.absoluteTimestamp,
                        showPlatformLogo = appearanceSettings.showPlatformLogo,
                    )
                },
            LocalWifiState provides true,
            content = {
                key(appSettings.language) {
                    ProvideNucleusDecoratedWindowTheme(
                        isDark = isDarkTheme(),
                    ) {
                        content.invoke()
                    }
                }
            },
        )
    }
}

@Composable
internal fun ProvideNucleusDecoratedWindowTheme(
    isDark: Boolean = true,
    content: @Composable () -> Unit,
) {
    val titleBarStyle =
        if (isDark) {
            DecoratedWindowDefaults.darkTitleBarStyle()
        } else {
            DecoratedWindowDefaults.lightTitleBarStyle()
        }.let {
            it.copy(
                metrics =
                    it.metrics.copy(
                        height =
                            if (SystemUtils.IS_OS_MAC_OSX) {
                                24.dp
                            } else if (SystemUtils.IS_OS_WINDOWS) {
                                32.dp
                            } else {
                                16.dp
                            },
                    ),
            )
        }
    NucleusDecoratedWindowTheme(
        titleBarStyle = titleBarStyle,
        isDark = isDark,
        content = content,
    )
}
