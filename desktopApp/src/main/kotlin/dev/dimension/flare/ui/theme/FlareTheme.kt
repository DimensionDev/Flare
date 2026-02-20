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
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.ui.component.ComponentAppearance
import dev.dimension.flare.ui.component.LocalComponentAppearance
import dev.dimension.flare.ui.humanizer.updateTimeFormatterLocale
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.onSuccess
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.FluentTheme
import io.github.composefluent.darkColors
import io.github.composefluent.lightColors
import io.github.kdroidfilter.nucleus.darkmodedetector.isSystemInDarkMode
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
    FluentTheme(
        compactMode = false,
        colors =
            if (isDarkTheme) {
                darkColors()
            } else {
                lightColors()
            },
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
                remember(appearanceSettings, appSettings.aiConfig) {
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
                        videoAutoplay = ComponentAppearance.VideoAutoplay.NEVER,
                        expandMediaSize = appearanceSettings.expandMediaSize,
                        compatLinkPreview = appearanceSettings.compatLinkPreview,
                        aiConfig =
                            ComponentAppearance.AiConfig(
                                translation = appSettings.aiConfig.translation,
                                tldr = appSettings.aiConfig.tldr,
                            ),
                        fullWidthPost = appearanceSettings.fullWidthPost,
                        postActionStyle = appearanceSettings.postActionStyle,
                        absoluteTimestamp = appearanceSettings.absoluteTimestamp,
                        showPlatformLogo = appearanceSettings.showPlatformLogo,
                    )
                },
            content = {
                val isDark = isDarkTheme()
                val titleBarStyle =
                    if (isDark) {
                        DecoratedWindowDefaults.darkTitleBarStyle()
                    } else {
                        DecoratedWindowDefaults.lightTitleBarStyle()
                    }.let {
                        it.copy(
                            metrics =
                                it.metrics.copy(
                                    height = 40.dp,
                                ),
                        )
                    }
                key(appSettings.language) {
                    NucleusDecoratedWindowTheme(
                        isDark = isDark,
                        titleBarStyle = titleBarStyle,
                    ) {
                        content.invoke()
                    }
                }
            },
        )
    }
}
