package dev.dimension.flare.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import com.konyaco.fluent.ExperimentalFluentApi
import com.konyaco.fluent.FluentTheme
import com.konyaco.fluent.darkColors
import com.konyaco.fluent.lightColors
import com.mayakapps.compose.windowstyler.WindowFrameStyle
import com.mayakapps.compose.windowstyler.WindowStyleManager
import kotlinx.coroutines.launch
import org.apache.commons.lang3.SystemUtils

@OptIn(ExperimentalFluentApi::class)
@Composable
internal fun FrameWindowScope.FlareTheme(
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
